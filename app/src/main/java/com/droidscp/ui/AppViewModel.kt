package com.droidscp.ui

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.droidscp.net.*
import com.droidscp.net.Notifs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { SITES, EDIT, BROWSER, EDITOR, TERMINAL, TRANSFERS, TUNNELS, SETTINGS, ABOUT }
enum class Pane { REMOTE, LOCAL }
enum class TState { PENDIENTE, EN_CURSO, HECHO, ERROR }

data class TransferItem(
    val id: Long,
    val name: String,
    val upload: Boolean,
    val src: String,
    val dst: String,
    val total: Long,
    val done: Long = 0,
    val state: TState = TState.PENDIENTE,
    val error: String = ""
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SiteStore(app)

    val sites = mutableStateListOf<Site>()
    val screen = mutableStateOf(Screen.SITES)
    val editing = mutableStateOf(Site())
    val busy = mutableStateOf(false)
    val busyText = mutableStateOf("")
    val message = mutableStateOf<String?>(null)

    var client: RemoteClient? = null
    val currentSite = mutableStateOf<Site?>(null)

    val pane = mutableStateOf(Pane.REMOTE)

    val remotePath = mutableStateOf("/")
    private val remoteAll = mutableStateListOf<RemoteFile>()
    val remoteFiles = mutableStateListOf<RemoteFile>()
    val remoteSelected = mutableStateListOf<String>()

    val localPath = mutableStateOf(Environment.getExternalStorageDirectory().absolutePath)
    private val localAll = mutableStateListOf<File>()
    val localFiles = mutableStateListOf<File>()
    val localSelected = mutableStateListOf<String>()

    // vista
    val query = mutableStateOf("")
    val sortBy = mutableStateOf(SortBy.NAME)
    val sortAsc = mutableStateOf(true)
    val showHidden = mutableStateOf(false)

    // portapapeles remoto
    val clipboard = mutableStateListOf<String>()
    val clipboardCut = mutableStateOf(false)

    // editor / terminal
    val editorPath = mutableStateOf("")
    val editorText = mutableStateOf("")
    val terminalLog = mutableStateOf("")
    val terminalHistory = mutableStateListOf<String>()

    // ajustes
    val parallel = mutableStateOf(store.parallel)
    val biometric = mutableStateOf(store.biometric)
    val resume = mutableStateOf(store.resume)

    fun setParallel(v: Int) { parallel.value = v.coerceIn(1, 4); store.parallel = parallel.value }
    fun setBiometric(v: Boolean) { biometric.value = v; store.biometric = v }
    fun setResume(v: Boolean) { resume.value = v; store.resume = v }

    // tuneles
    data class Tunnel(val localPort: Int, val remoteHost: String, val remotePort: Int)
    val tunnels = mutableStateListOf<Tunnel>()

    // transferencias
    val transfers = mutableStateListOf<TransferItem>()
    private var worker: Job? = null
    private var seq = 0L

    init {
        sites.addAll(store.load())
        refreshLocal()
    }

    fun persist() = store.save(sites)

    fun newSite() { editing.value = Site(); screen.value = Screen.EDIT }
    fun editSite(s: Site) { editing.value = s.copy(); screen.value = Screen.EDIT }

    fun saveSite(s: Site) {
        val i = sites.indexOfFirst { it.id == s.id }
        if (i >= 0) sites[i] = s else sites.add(s)
        persist(); screen.value = Screen.SITES
    }

    fun duplicateSite(s: Site) {
        sites.add(s.copy(id = java.util.UUID.randomUUID().toString(), name = s.name + " (copia)"))
        persist()
    }

    fun deleteSite(s: Site) { sites.removeAll { it.id == s.id }; persist() }

    /* ---------- conexión ---------- */

    fun connect(site: Site) = run("Conectando a ${site.host}…") {
        client?.close()
        val c = ClientFactory.create(site) { fp ->
            val idx = sites.indexOfFirst { it.id == site.id }
            if (idx >= 0) { sites[idx] = sites[idx].copy(hostKey = fp); persist() }
        }
        c.connect()
        client = c
        val start = c.home()
        val files = c.list(start)
        withContext(Dispatchers.Main) {
            currentSite.value = site
            remotePath.value = start
            remoteAll.clear(); remoteAll.addAll(files); applyView()
            remoteSelected.clear()
            pane.value = Pane.REMOTE
            terminalLog.value = ""
            screen.value = Screen.BROWSER
        }
    }

    fun disconnect() {
        worker?.cancel(); worker = null
        stopTunnels()
        Notifs.cancel(getApplication())
        try { client?.close() } catch (_: Exception) {}
        client = null
        currentSite.value = null
        remoteAll.clear(); remoteFiles.clear(); remoteSelected.clear()
        transfers.clear(); clipboard.clear()
        screen.value = Screen.SITES
    }

    /* ---------- listados y vista ---------- */

    fun openRemote(path: String) = run("Abriendo…") {
        val files = client!!.list(path)
        withContext(Dispatchers.Main) {
            remotePath.value = path
            remoteAll.clear(); remoteAll.addAll(files)
            remoteSelected.clear(); query.value = ""; applyView()
        }
    }

    fun refreshRemote() = openRemote(remotePath.value)

    fun refreshLocal() {
        val dir = File(localPath.value)
        localAll.clear()
        localAll.addAll(dir.listFiles() ?: emptyArray())
        localSelected.clear()
        applyView()
    }

    fun openLocal(path: String) { localPath.value = path; query.value = ""; refreshLocal() }

    fun applyView() {
        val q = query.value.trim().lowercase()
        val asc = sortAsc.value

        var r = remoteAll.toList()
        if (!showHidden.value) r = r.filter { !it.name.startsWith(".") }
        if (q.isNotEmpty()) r = r.filter { it.name.lowercase().contains(q) }
        val rc: Comparator<RemoteFile> = when (sortBy.value) {
            SortBy.NAME -> compareBy { it.name.lowercase() }
            SortBy.SIZE -> compareBy { it.size }
            SortBy.DATE -> compareBy { it.mtime }
            SortBy.TYPE -> compareBy { it.name.substringAfterLast('.', "").lowercase() }
        }
        r = r.sortedWith(compareByDescending<RemoteFile> { it.isDir }
            .then(if (asc) rc else rc.reversed()))
        remoteFiles.clear(); remoteFiles.addAll(r)

        var l = localAll.toList()
        if (!showHidden.value) l = l.filter { !it.name.startsWith(".") }
        if (q.isNotEmpty()) l = l.filter { it.name.lowercase().contains(q) }
        val lc: Comparator<File> = when (sortBy.value) {
            SortBy.NAME -> compareBy { it.name.lowercase() }
            SortBy.SIZE -> compareBy { it.length() }
            SortBy.DATE -> compareBy { it.lastModified() }
            SortBy.TYPE -> compareBy { it.extension.lowercase() }
        }
        l = l.sortedWith(compareByDescending<File> { it.isDirectory }
            .then(if (asc) lc else lc.reversed()))
        localFiles.clear(); localFiles.addAll(l)
    }

    fun toggleSel(p: Pane, key: String) {
        val l = if (p == Pane.REMOTE) remoteSelected else localSelected
        if (l.contains(key)) l.remove(key) else l.add(key)
    }

    fun selectAll() {
        if (pane.value == Pane.REMOTE) {
            if (remoteSelected.size == remoteFiles.size) remoteSelected.clear()
            else { remoteSelected.clear(); remoteSelected.addAll(remoteFiles.map { it.path }) }
        } else {
            if (localSelected.size == localFiles.size) localSelected.clear()
            else { localSelected.clear(); localSelected.addAll(localFiles.map { it.absolutePath }) }
        }
    }

    /* ---------- favoritos ---------- */

    fun toggleFavorite() {
        val s = currentSite.value ?: return
        val p = if (pane.value == Pane.REMOTE) remotePath.value else localPath.value
        if (pane.value != Pane.REMOTE) { message.value = "Los favoritos son para rutas remotas"; return }
        if (s.favorites.contains(p)) s.favorites.remove(p) else s.favorites.add(p)
        val i = sites.indexOfFirst { it.id == s.id }
        if (i >= 0) sites[i] = s.copy(favorites = s.favorites.toMutableList())
        currentSite.value = sites.getOrNull(i) ?: s
        persist()
    }

    fun isFavorite(): Boolean =
        pane.value == Pane.REMOTE && currentSite.value?.favorites?.contains(remotePath.value) == true

    /* ---------- transferencias ---------- */

    fun transfer() {
        if (pane.value == Pane.REMOTE) {
            val sel = remoteFiles.filter { remoteSelected.contains(it.path) }
            val destDir = localPath.value
            viewModelScope.launch(Dispatchers.IO) {
                for (f in sel) enqueueDownload(f, destDir)
                startWorker()
            }
            remoteSelected.clear()
        } else {
            val sel = localFiles.filter { localSelected.contains(it.absolutePath) }
            viewModelScope.launch(Dispatchers.IO) {
                for (f in sel) enqueueUpload(f, remotePath.value)
                startWorker()
            }
            localSelected.clear()
        }
        screen.value = Screen.TRANSFERS
    }

    private fun enqueueDownload(f: RemoteFile, destDir: String) {
        if (f.isDir) {
            val d = File(destDir, f.name); d.mkdirs()
            for (ch in client!!.list(f.path)) enqueueDownload(ch, d.absolutePath)
        } else {
            transfers.add(TransferItem(seq++, f.name, false, f.path, File(destDir, f.name).absolutePath, f.size))
        }
    }

    private fun enqueueUpload(f: File, destPath: String) {
        if (f.isDirectory) {
            val p = joinPath(destPath, f.name)
            try { client!!.mkdir(p) } catch (_: Exception) {}
            for (ch in f.listFiles() ?: emptyArray()) enqueueUpload(ch, p)
        } else {
            transfers.add(TransferItem(seq++, f.name, true, f.absolutePath, joinPath(destPath, f.name), f.length()))
        }
    }

    private fun startWorker() {
        if (worker?.isActive == true) return
        worker = viewModelScope.launch(Dispatchers.IO) {
            val n = if (client?.supportsResume() == true) parallel.value else 1
            (1..n).map { launch(Dispatchers.IO) { drainQueue() } }.forEach { it.join() }
            val errs = transfers.count { it.state == TState.ERROR }
            Notifs.done(
                getApplication(),
                if (errs > 0) "Terminado con $errs error(es)" else "Todas las transferencias completadas"
            )
            withContext(Dispatchers.Main) {
                message.value = "Transferencias finalizadas"
                refreshLocal()
                try {
                    val files = client!!.list(remotePath.value)
                    remoteAll.clear(); remoteAll.addAll(files); applyView()
                } catch (_: Exception) {}
            }
        }
    }

    private val lock = Any()

    private fun takeNext(): TransferItem? = synchronized(lock) {
        val idx = transfers.indexOfFirst { it.state == TState.PENDIENTE }
        if (idx < 0) return null
        val item = transfers[idx].copy(state = TState.EN_CURSO)
        transfers[idx] = item
        item
    }

    private fun drainQueue() {
        while (true) {
            val item = takeNext() ?: break
            notifyProgress()
            try {
                if (item.upload) {
                    client!!.upload(File(item.src), item.dst) { d, t ->
                        val i = transfers.indexOfFirst { it.id == item.id }
                        if (i >= 0) transfers[i] = transfers[i].copy(done = d, total = if (t > 0) t else transfers[i].total)
                    }
                } else {
                    val lf = File(item.dst)
                    val off = if (resume.value && client!!.supportsResume() &&
                        lf.exists() && lf.length() in 1 until item.total) lf.length() else 0L
                    client!!.downloadResume(item.src, lf, item.total, off) { d, t ->
                        val i = transfers.indexOfFirst { it.id == item.id }
                        if (i >= 0) transfers[i] = transfers[i].copy(done = d, total = if (t > 0) t else transfers[i].total)
                    }
                }
                val i = transfers.indexOfFirst { it.id == item.id }
                if (i >= 0) transfers[i] = transfers[i].copy(state = TState.HECHO, done = transfers[i].total)
                notifyProgress()
            } catch (e: Exception) {
                val i = transfers.indexOfFirst { it.id == item.id }
                if (i >= 0) transfers[i] = transfers[i].copy(state = TState.ERROR, error = e.message ?: "Error")
            }
        }
    }

    private fun notifyProgress() {
        val total = transfers.size.coerceAtLeast(1)
        val done = transfers.count { it.state == TState.HECHO }
        Notifs.progress(getApplication(), "Transfiriendo", "$done / ${transfers.size}", done * 100 / total)
    }

    /* ---------- sincronización ---------- */

    fun sync(toRemote: Boolean, mirror: Boolean) = run("Comparando carpetas…") {
        val localRoot = File(localPath.value)
        val remoteRoot = remotePath.value
        val plan = mutableListOf<Pair<String, String>>()
        if (toRemote) syncScanUp(localRoot, remoteRoot, plan, mirror)
        else syncScanDown(remoteRoot, localRoot, plan, mirror)
        withContext(Dispatchers.Main) {
            if (transfers.isEmpty() && plan.isEmpty()) message.value = "Ya está todo sincronizado"
            else screen.value = Screen.TRANSFERS
        }
        startWorker()
    }

    private fun syncScanUp(local: File, remote: String, plan: MutableList<Pair<String, String>>, mirror: Boolean) {
        val remoteList = try { client!!.list(remote) } catch (e: Exception) {
            client!!.mkdir(remote); emptyList()
        }
        val byName = remoteList.associateBy { it.name }
        for (f in local.listFiles() ?: emptyArray()) {
            val r = byName[f.name]
            if (f.isDirectory) {
                val p = joinPath(remote, f.name)
                if (r == null) try { client!!.mkdir(p) } catch (_: Exception) {}
                syncScanUp(f, p, plan, mirror)
            } else if (r == null || r.size != f.length() || r.mtime < f.lastModified() - 2000) {
                transfers.add(TransferItem(seq++, f.name, true, f.absolutePath, joinPath(remote, f.name), f.length()))
                plan.add(f.absolutePath to remote)
            }
        }
        if (mirror) {
            val localNames = (local.listFiles() ?: emptyArray()).map { it.name }.toSet()
            for (r in remoteList) if (!localNames.contains(r.name))
                try { client!!.delete(r.path, r.isDir) } catch (_: Exception) {}
        }
    }

    private fun syncScanDown(remote: String, local: File, plan: MutableList<Pair<String, String>>, mirror: Boolean) {
        local.mkdirs()
        val remoteList = client!!.list(remote)
        val localFilesMap = (local.listFiles() ?: emptyArray()).associateBy { it.name }
        for (r in remoteList) {
            val f = localFilesMap[r.name]
            if (r.isDir) {
                syncScanDown(r.path, File(local, r.name), plan, mirror)
            } else if (f == null || f.length() != r.size || f.lastModified() < r.mtime - 2000) {
                transfers.add(TransferItem(seq++, r.name, false, r.path, File(local, r.name).absolutePath, r.size))
                plan.add(r.path to local.absolutePath)
            }
        }
        if (mirror) {
            val remoteNames = remoteList.map { it.name }.toSet()
            for (f in local.listFiles() ?: emptyArray()) if (!remoteNames.contains(f.name)) f.deleteRecursively()
        }
    }

    /* ---------- comprimir ---------- */

    fun compressSelected(archiveName: String) = run("Comprimiendo…") {
        val names = remoteFiles.filter { remoteSelected.contains(it.path) }.map { it.name }
        client!!.compress(remotePath.value, names, archiveName)
        reloadRemote()
    }

    fun extractSelected() = run("Descomprimiendo…") {
        for (f in remoteFiles.filter { remoteSelected.contains(it.path) })
            client!!.extract(f.path, remotePath.value)
        reloadRemote()
    }

    /* ---------- túneles ---------- */

    fun addTunnel(localPort: Int, remoteHost: String, remotePort: Int) {
        val c = client
        if (c !is com.droidscp.net.SshRemoteClient) { message.value = "Los túneles requieren SSH/SFTP"; return }
        try {
            c.startForward(localPort, remoteHost, remotePort)
            tunnels.add(Tunnel(localPort, remoteHost, remotePort))
            message.value = "Túnel activo en 127.0.0.1:$localPort"
        } catch (e: Exception) { message.value = e.message ?: "No se pudo abrir el túnel" }
    }

    fun forgetHostKey() {
        val s = currentSite.value ?: return
        val i = sites.indexOfFirst { it.id == s.id }
        if (i >= 0) { sites[i] = sites[i].copy(hostKey = ""); persist() }
        currentSite.value = sites.getOrNull(i) ?: s
        message.value = "Huella olvidada. Se guardará de nuevo al reconectar."
    }

    fun stopTunnels() {
        (client as? com.droidscp.net.SshRemoteClient)?.stopForwards()
        tunnels.clear()
    }

    fun clearFinished() = transfers.removeAll { it.state == TState.HECHO }

    fun retryErrors() {
        for (i in transfers.indices) if (transfers[i].state == TState.ERROR)
            transfers[i] = transfers[i].copy(state = TState.PENDIENTE, done = 0, error = "")
        startWorker()
    }

    /* ---------- subir desde el selector del móvil ---------- */

    fun uploadUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            for (u in uris) {
                try {
                    var name = "archivo"
                    ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (i >= 0 && c.moveToFirst()) name = c.getString(i)
                    }
                    val tmp = File(ctx.cacheDir, "up_${System.currentTimeMillis()}_${name}")
                    ctx.contentResolver.openInputStream(u)?.use { inp ->
                        tmp.outputStream().use { out -> inp.copyTo(out) }
                    }
                    transfers.add(
                        TransferItem(seq++, name, true, tmp.absolutePath,
                            joinPath(remotePath.value, name), tmp.length())
                    )
                } catch (e: Exception) { message.value = e.message }
            }
            startWorker()
        }
        screen.value = Screen.TRANSFERS
    }

    /* ---------- operaciones de archivo ---------- */

    fun mkdir(name: String) {
        if (pane.value == Pane.REMOTE) run("Creando carpeta…") {
            client!!.mkdir(joinPath(remotePath.value, name)); reloadRemote()
        } else { File(localPath.value, name).mkdirs(); refreshLocal() }
    }

    fun newFile(name: String) {
        if (pane.value == Pane.REMOTE) run("Creando archivo…") {
            client!!.createFile(joinPath(remotePath.value, name)); reloadRemote()
        } else { File(localPath.value, name).createNewFile(); refreshLocal() }
    }

    fun deleteSelected() {
        if (pane.value == Pane.REMOTE) run("Eliminando…") {
            for (f in remoteFiles.filter { remoteSelected.contains(it.path) })
                client!!.delete(f.path, f.isDir)
            reloadRemote()
        } else {
            for (f in localFiles.filter { localSelected.contains(it.absolutePath) }) f.deleteRecursively()
            refreshLocal()
        }
    }

    fun renameSelected(newName: String) {
        if (pane.value == Pane.REMOTE) run("Renombrando…") {
            val f = remoteFiles.first { remoteSelected.contains(it.path) }
            client!!.rename(f.path, joinPath(remotePath.value, newName))
            reloadRemote()
        } else {
            val f = localFiles.first { localSelected.contains(it.absolutePath) }
            f.renameTo(File(localPath.value, newName)); refreshLocal()
        }
    }

    fun chmodSelected(octal: String) = run("Aplicando permisos…") {
        for (f in remoteFiles.filter { remoteSelected.contains(it.path) }) client!!.chmod(f.path, octal)
        reloadRemote()
    }

    fun copyToClipboard(cut: Boolean) {
        clipboard.clear()
        clipboard.addAll(if (pane.value == Pane.REMOTE) remoteSelected.toList() else localSelected.toList())
        clipboardCut.value = cut
        message.value = if (cut) "${clipboard.size} elemento(s) cortado(s)" else "${clipboard.size} elemento(s) copiado(s)"
    }

    fun paste() {
        if (clipboard.isEmpty()) return
        if (pane.value == Pane.REMOTE) run(if (clipboardCut.value) "Moviendo…" else "Copiando…") {
            for (p in clipboard.toList()) {
                val name = p.trimEnd('/').substringAfterLast('/')
                val dst = joinPath(remotePath.value, name)
                if (dst == p) continue
                if (clipboardCut.value) client!!.rename(p, dst)
                else client!!.copy(p, dst, false)
            }
            withContext(Dispatchers.Main) { clipboard.clear() }
            reloadRemote()
        } else {
            for (p in clipboard.toList()) {
                val src = File(p)
                if (!src.exists()) continue
                val dst = File(localPath.value, src.name)
                if (src.isDirectory) src.copyRecursively(dst, true) else src.copyTo(dst, true)
                if (clipboardCut.value) src.deleteRecursively()
            }
            clipboard.clear(); refreshLocal()
        }
    }

    private suspend fun reloadRemote() {
        val files = client!!.list(remotePath.value)
        withContext(Dispatchers.Main) {
            remoteAll.clear(); remoteAll.addAll(files); remoteSelected.clear(); applyView()
        }
    }

    /* ---------- editor y terminal ---------- */

    fun openEditor(path: String) = run("Abriendo archivo…") {
        val t = client!!.readText(path)
        withContext(Dispatchers.Main) {
            editorPath.value = path; editorText.value = t; screen.value = Screen.EDITOR
        }
    }

    fun saveEditor() = run("Guardando…") {
        client!!.writeText(editorPath.value, editorText.value)
        withContext(Dispatchers.Main) { message.value = "Archivo guardado"; screen.value = Screen.BROWSER }
    }

    fun runCommand(cmd: String) = run("Ejecutando…") {
        val out = client!!.exec(cmd)
        withContext(Dispatchers.Main) {
            terminalHistory.add(cmd)
            terminalLog.value = terminalLog.value + "\n$ $cmd\n" + out
        }
    }

    private fun run(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true; busyText.value = label
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: Exception) { message.value = e.message ?: e.javaClass.simpleName }
            finally { busy.value = false; busyText.value = "" }
        }
    }

    override fun onCleared() {
        try { client?.close() } catch (_: Exception) {}
        super.onCleared()
    }
}
