package com.droidscp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidscp.net.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AppRoot(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val msg = vm.message.value
    LaunchedEffect(msg) { if (msg != null) { snackbar.showSnackbar(msg); vm.message.value = null } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.screen.value) {
                Screen.SITES -> SitesScreen(vm)
                Screen.EDIT -> SiteEditScreen(vm)
                Screen.BROWSER -> BrowserScreen(vm)
                Screen.EDITOR -> EditorScreen(vm)
                Screen.TERMINAL -> TerminalScreen(vm)
                Screen.TRANSFERS -> TransfersScreen(vm)
                Screen.TUNNELS -> TunnelsScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
                Screen.ABOUT -> AboutScreen(vm)
            }
            vm.pendingHostKey.value?.let { (site, fp) ->
                AlertDialog(
                    onDismissRequest = { vm.pendingHostKey.value = null },
                    title = { Text("Servidor desconocido") },
                    text = {
                        Column {
                            Text("Es la primera vez que te conectas a ${site.host}. Comprueba que esta huella coincide con la de tu servidor antes de aceptar:",
                                fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            Text(fp, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    confirmButton = { TextButton(onClick = { vm.trustAndConnect() }) { Text("Confiar y conectar") } },
                    dismissButton = { TextButton(onClick = { vm.pendingHostKey.value = null }) { Text("Cancelar") } }
                )
            }
            vm.askPasswordFor.value?.let { site ->
                var pw by remember(site.id) { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { vm.askPasswordFor.value = null },
                    title = { Text("Contraseña") },
                    text = {
                        Column {
                            Text("No se guardan contraseñas. Introduce la de ${site.user}@${site.host}:",
                                fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(value = pw, onValueChange = { pw = it }, singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(12.dp))
                        }
                    },
                    confirmButton = { TextButton(onClick = { vm.connectWithPassword(site, pw) }) { Text("Conectar") } },
                    dismissButton = { TextButton(onClick = { vm.askPasswordFor.value = null }) { Text("Cancelar") } }
                )
            }
            if (vm.busy.value) {
                Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 4.dp) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(14.dp))
                            Text(vm.busyText.value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------- SITIOS ------------------------- */

@Composable
fun SitesScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("DroidSCP", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Tus conexiones guardadas", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        if (vm.sites.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Aún no hay conexiones.\nPulsa + para crear la primera.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(vm.sites, key = { it.id }) { s ->
                    var menu by remember { mutableStateOf(false) }
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { vm.connect(s) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                                Text(s.protocol.name, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (s.name.isBlank()) s.host else s.name, fontWeight = FontWeight.SemiBold)
                                Text("${s.user}@${s.host}:${s.port}", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Editar") },
                                        onClick = { menu = false; vm.editSite(s) })
                                    DropdownMenuItem(text = { Text("Duplicar") },
                                        onClick = { menu = false; vm.duplicateSite(s) })
                                    DropdownMenuItem(text = { Text("Eliminar") },
                                        onClick = { menu = false; vm.deleteSite(s) })
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.screen.value = Screen.SETTINGS }) { Text("Ajustes") }
            TextButton(onClick = { vm.screen.value = Screen.ABOUT }) { Text("Acerca de") }
            Spacer(Modifier.weight(1f))
            ExtendedFloatingActionButton(
                onClick = { vm.newSite() },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White
            ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nueva conexión") }
        }
    }
}

/* ------------------------- EDITAR SITIO ------------------------- */

@Composable
fun SiteEditScreen(vm: AppViewModel) {
    var s by remember { mutableStateOf(vm.editing.value) }
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen.value = Screen.SITES }) { Icon(Icons.Default.ArrowBack, null) }
            Text("Conexión", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Field("Nombre", s.name) { s = s.copy(name = it) }

        Text("Protocolo", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Protocol.values().forEach { p ->
                FilterChip(selected = s.protocol == p, onClick = {
                    s = s.copy(protocol = p, port = if (p == Protocol.SFTP || p == Protocol.SCP) 22 else 21)
                }, label = { Text(p.name) })
            }
        }
        Spacer(Modifier.height(12.dp))

        Field("Host o IP", s.host) { s = s.copy(host = it) }
        Field("Puerto", s.port.toString()) { s = s.copy(port = it.toIntOrNull() ?: 0) }
        Field("Usuario", s.user) { s = s.copy(user = it) }
        Field("Contraseña", s.password, password = true) { s = s.copy(password = it) }
        Field("Ruta inicial (opcional)", s.initialPath) { s = s.copy(initialPath = it) }

        if (s.protocol == Protocol.SFTP || s.protocol == Protocol.SCP) {
            Field("Clave privada: ruta en el móvil (opcional)", s.keyPath) { s = s.copy(keyPath = it) }
            Field("Passphrase de la clave (opcional)", s.keyPassphrase, password = true) { s = s.copy(keyPassphrase = it) }
        }
        if (s.protocol == Protocol.FTP) {
            Surface(shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("FTP envía la contraseña y los archivos sin cifrar. Usa FTPS o SFTP siempre que el servidor lo permita.",
                    Modifier.padding(12.dp), fontSize = 12.sp)
            }
        }
        if (s.protocol == Protocol.FTP || s.protocol == Protocol.FTPS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = s.passiveFtp, onCheckedChange = { s = s.copy(passiveFtp = it) })
                Spacer(Modifier.width(10.dp)); Text("Modo pasivo")
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.saveSite(s) }, Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)) { Text("Guardar") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { vm.saveSite(s); vm.connect(s) }, Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)) { Text("Guardar y conectar") }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun Field(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    )
}

/* ------------------------- EXPLORADOR ------------------------- */

@Composable
fun BrowserScreen(vm: AppViewModel) {
    var showMkdir by remember { mutableStateOf(false) }
    var showNewFile by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showChmod by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showGoto by remember { mutableStateOf(false) }
    var showProps by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var favMenu by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var showZip by remember { mutableStateOf(false) }

    val isRemote = vm.pane.value == Pane.REMOTE
    val selCount = if (isRemote) vm.remoteSelected.size else vm.localSelected.size
    val activeTransfers = vm.transfers.count { it.state == TState.PENDIENTE || it.state == TState.EN_CURSO }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.disconnect() }) { Icon(Icons.Default.ArrowBack, null) }
            Column(Modifier.weight(1f)) {
                Text(vm.currentSite.value?.let { if (it.name.isBlank()) it.host else it.name } ?: "",
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(vm.currentSite.value?.protocol?.name ?: "", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { search = !search; if (!search) { vm.query.value = ""; vm.applyView() } }) {
                Icon(Icons.Default.Search, null)
            }
            Box {
                IconButton(onClick = { vm.screen.value = Screen.TRANSFERS }) {
                    Icon(Icons.Default.SwapVert, null,
                        tint = if (activeTransfers > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (vm.client?.supportsTerminal() == true) {
                IconButton(onClick = { vm.screen.value = Screen.TERMINAL }) { Icon(Icons.Default.Terminal, null) }
            }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    Text("Ordenar por", Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SortBy.values().forEach { sb ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (sb) {
                                        SortBy.NAME -> "Nombre"; SortBy.SIZE -> "Tamaño"
                                        SortBy.DATE -> "Fecha"; SortBy.TYPE -> "Tipo"
                                    } + if (vm.sortBy.value == sb) if (vm.sortAsc.value) "  ↑" else "  ↓" else ""
                                )
                            },
                            onClick = {
                                if (vm.sortBy.value == sb) vm.sortAsc.value = !vm.sortAsc.value
                                else { vm.sortBy.value = sb; vm.sortAsc.value = true }
                                vm.applyView(); sortMenu = false
                            })
                    }
                    Divider()
                    DropdownMenuItem(
                        text = { Text(if (vm.showHidden.value) "Ocultar archivos ocultos" else "Mostrar archivos ocultos") },
                        onClick = { vm.showHidden.value = !vm.showHidden.value; vm.applyView(); sortMenu = false })
                    DropdownMenuItem(text = { Text("Seleccionar todo / ninguno") },
                        onClick = { vm.selectAll(); sortMenu = false })
                    DropdownMenuItem(text = { Text("Ir a ruta…") }, onClick = { showGoto = true; sortMenu = false })
                    DropdownMenuItem(text = { Text("Propiedades") },
                        onClick = { showProps = true; sortMenu = false })
                    Divider()
                    DropdownMenuItem(
                        text = { Text(if (vm.isFavorite()) "Quitar de favoritos" else "Añadir a favoritos") },
                        onClick = { vm.toggleFavorite(); sortMenu = false })
                    Divider()
                    DropdownMenuItem(text = { Text("Sincronizar carpetas…") },
                        onClick = { showSync = true; sortMenu = false })
                    if (vm.client?.supportsTerminal() == true) {
                        DropdownMenuItem(text = { Text("Túneles SSH") },
                            onClick = { vm.screen.value = Screen.TUNNELS; sortMenu = false })
                        DropdownMenuItem(text = { Text("Comprimir selección…") },
                            onClick = { showZip = true; sortMenu = false })
                        DropdownMenuItem(text = { Text("Descomprimir selección") },
                            onClick = { vm.extractSelected(); sortMenu = false })
                    }
                    DropdownMenuItem(text = { Text("Olvidar huella del servidor") },
                        onClick = { vm.forgetHostKey(); sortMenu = false })
                }
            }
        }

        if (search) {
            OutlinedTextField(
                value = vm.query.value,
                onValueChange = { vm.query.value = it; vm.applyView() },
                placeholder = { Text("Buscar en esta carpeta…") },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        TabRow(selectedTabIndex = if (isRemote) 0 else 1, containerColor = Color.Transparent) {
            Tab(selected = isRemote, onClick = { vm.pane.value = Pane.REMOTE }, text = { Text("Remoto") })
            Tab(selected = !isRemote, onClick = { vm.pane.value = Pane.LOCAL }, text = { Text("Local") })
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (isRemote) vm.remotePath.value else vm.localPath.value,
                Modifier.weight(1f), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isRemote) {
                Box {
                    IconButton(onClick = { favMenu = true }, modifier = Modifier.size(30.dp)) {
                        Icon(if (vm.isFavorite()) Icons.Default.Star else Icons.Default.StarBorder, null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    val favs = vm.currentSite.value?.favorites ?: mutableListOf()
                    DropdownMenu(expanded = favMenu, onDismissRequest = { favMenu = false }) {
                        if (favs.isEmpty()) DropdownMenuItem(text = { Text("Sin favoritos") }, onClick = { favMenu = false })
                        favs.forEach { p ->
                            DropdownMenuItem(text = { Text(p, fontSize = 12.sp) },
                                onClick = { favMenu = false; vm.openRemote(p) })
                        }
                    }
                }
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) {
            item {
                RowItem("..", true, "", "", false, {
                    if (isRemote) vm.openRemote(parentPath(vm.remotePath.value))
                    else File(vm.localPath.value).parentFile?.let { vm.openLocal(it.absolutePath) }
                }, {})
            }
            if (isRemote) {
                items(vm.remoteFiles, key = { it.path }) { f ->
                    RowItem(f.name, f.isDir, humanSize(f.size), "${f.perms}  ${fmtDate(f.mtime)}",
                        vm.remoteSelected.contains(f.path),
                        { if (f.isDir) vm.openRemote(f.path) else vm.toggleSel(Pane.REMOTE, f.path) },
                        { vm.toggleSel(Pane.REMOTE, f.path) })
                }
            } else {
                items(vm.localFiles, key = { it.absolutePath }) { f ->
                    RowItem(f.name, f.isDirectory, humanSize(f.length()), fmtDate(f.lastModified()),
                        vm.localSelected.contains(f.absolutePath),
                        { if (f.isDirectory) vm.openLocal(f.absolutePath) else vm.toggleSel(Pane.LOCAL, f.absolutePath) },
                        { vm.toggleSel(Pane.LOCAL, f.absolutePath) })
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }

        Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                if (selCount > 0) Text("$selCount seleccionado(s)",
                    Modifier.padding(start = 6.dp, bottom = 4.dp), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionBtn(if (isRemote) Icons.Default.Download else Icons.Default.Upload,
                        if (isRemote) "Descargar" else "Subir", selCount > 0) { vm.transfer() }
                    ActionBtn(Icons.Default.ContentCopy, "Copiar", selCount > 0) { vm.copyToClipboard(false) }
                    ActionBtn(Icons.Default.ContentCut, "Cortar", selCount > 0) { vm.copyToClipboard(true) }
                    ActionBtn(Icons.Default.ContentPaste, "Pegar", vm.clipboard.isNotEmpty()) { vm.paste() }
                    ActionBtn(Icons.Default.CreateNewFolder, "Carpeta", true) { showMkdir = true }
                    ActionBtn(Icons.Default.NoteAdd, "Archivo", true) { showNewFile = true }
                    ActionBtn(Icons.Default.DriveFileRenameOutline, "Renombrar", selCount == 1) { showRename = true }
                    ActionBtn(Icons.Default.Delete, "Eliminar", selCount > 0) { showDelete = true }
                    if (!isRemote) {
                        val ctx = LocalContext.current
                        ActionBtn(Icons.Default.OpenInNew, "Abrir", selCount == 1) {
                            try {
                                val f = File(vm.localSelected.first())
                                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                                val i = Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(uri, "*/*")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(Intent.createChooser(i, "Abrir con"))
                            } catch (e: Exception) { vm.message.value = "No se pudo abrir el archivo" }
                        }
                    }
                    if (isRemote) {
                        val picker = rememberLauncherForActivityResult(
                            ActivityResultContracts.OpenMultipleDocuments()
                        ) { uris -> vm.uploadUris(uris) }
                        ActionBtn(Icons.Default.AttachFile, "Del móvil", true) {
                            try { picker.launch(arrayOf("*/*")) }
                            catch (e: Exception) { vm.message.value = "No se pudo abrir el selector" }
                        }
                        ActionBtn(Icons.Default.Lock, "Permisos", selCount > 0) { showChmod = true }
                        ActionBtn(Icons.Default.EditNote, "Editar", selCount == 1) {
                            vm.openEditor(vm.remoteSelected.first())
                        }
                    }
                }
            }
        }
    }

    if (showMkdir) TextDialog("Nueva carpeta", "Nombre", "") {
        showMkdir = false; if (!it.isNullOrBlank()) vm.mkdir(it)
    }
    if (showNewFile) TextDialog("Nuevo archivo", "Nombre", "") {
        showNewFile = false; if (!it.isNullOrBlank()) vm.newFile(it)
    }
    if (showGoto) TextDialog("Ir a ruta", "Ruta", if (isRemote) vm.remotePath.value else vm.localPath.value) {
        showGoto = false
        if (!it.isNullOrBlank()) { if (isRemote) vm.openRemote(it) else vm.openLocal(it) }
    }
    if (showRename) {
        val cur = if (isRemote) vm.remoteFiles.firstOrNull { f -> vm.remoteSelected.contains(f.path) }?.name ?: ""
        else vm.localFiles.firstOrNull { f -> vm.localSelected.contains(f.absolutePath) }?.name ?: ""
        TextDialog("Renombrar", "Nuevo nombre", cur) {
            showRename = false; if (!it.isNullOrBlank()) vm.renameSelected(it)
        }
    }
    if (showZip) TextDialog("Comprimir", "Nombre del archivo", "archivo.tar.gz") {
        showZip = false; if (!it.isNullOrBlank()) vm.compressSelected(it)
    }
    if (showSync) SyncDialog({ showSync = false }) { toRemote, mirror ->
        showSync = false; vm.sync(toRemote, mirror)
    }
    if (showChmod) TextDialog("Permisos (octal)", "Ej: 644", "644") {
        showChmod = false; if (!it.isNullOrBlank()) vm.chmodSelected(it)
    }
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("Eliminar") },
        text = { Text("¿Eliminar $selCount elemento(s)? No se puede deshacer.") },
        confirmButton = { TextButton(onClick = { showDelete = false; vm.deleteSelected() }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar") } }
    )
    if (showProps) {
        val info = if (isRemote) {
            val f = vm.remoteFiles.firstOrNull { vm.remoteSelected.contains(it.path) }
            if (f == null) "Selecciona un elemento."
            else "Nombre: ${f.name}\nRuta: ${f.path}\nTipo: ${if (f.isDir) "Carpeta" else "Archivo"}\n" +
                "Tamaño: ${humanSize(f.size)}\nPermisos: ${f.perms}\nUID/GID: ${f.owner}/${f.group}\n" +
                "Modificado: ${fmtDate(f.mtime)}"
        } else {
            val f = vm.localFiles.firstOrNull { vm.localSelected.contains(it.absolutePath) }
            if (f == null) "Selecciona un elemento."
            else "Nombre: ${f.name}\nRuta: ${f.absolutePath}\nTipo: ${if (f.isDirectory) "Carpeta" else "Archivo"}\n" +
                "Tamaño: ${humanSize(f.length())}\nModificado: ${fmtDate(f.lastModified())}"
        }
        AlertDialog(onDismissRequest = { showProps = false }, title = { Text("Propiedades") },
            text = { Text(info, fontSize = 13.sp, fontFamily = FontFamily.Monospace) },
            confirmButton = { TextButton(onClick = { showProps = false }) { Text("Cerrar") } })
    }
}

@Composable
fun ActionBtn(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(icon, null, tint = tint)
        Text(label, fontSize = 10.sp, color = tint)
    }
}

@Composable
fun RowItem(name: String, isDir: Boolean, size: String, meta: String,
            selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile, null,
                tint = if (isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta.isNotBlank()) Text(meta, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isDir) Text(size, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (name != "..") Checkbox(checked = selected, onCheckedChange = { onLong() })
        }
    }
}

@Composable
fun TextDialog(title: String, label: String, initial: String, onResult: (String?) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onResult(null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text(label) },
                singleLine = true, shape = RoundedCornerShape(12.dp))
        },
        confirmButton = { TextButton(onClick = { onResult(t) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text("Cancelar") } }
    )
}

/* ------------------------- TRANSFERENCIAS ------------------------- */

@Composable
fun TransfersScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen.value = Screen.BROWSER }) { Icon(Icons.Default.ArrowBack, null) }
            Text("Transferencias", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TextButton(onClick = { vm.retryErrors() }) { Text("Reintentar") }
            TextButton(onClick = { vm.clearFinished() }) { Text("Limpiar") }
        }
        if (vm.transfers.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No hay transferencias.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp)) {
                items(vm.transfers, key = { it.id }) { t ->
                    val pct = if (t.total > 0) (t.done.toFloat() / t.total).coerceIn(0f, 1f) else 0f
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (t.upload) Icons.Default.Upload else Icons.Default.Download, null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(t.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when (t.state) {
                                    TState.HECHO -> "Hecho"
                                    TState.ERROR -> "Error"
                                    TState.EN_CURSO -> "${(pct * 100).toInt()}%"
                                    else -> "En cola"
                                },
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = if (t.state == TState.HECHO) 1f else pct,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            if (t.state == TState.ERROR) t.error
                            else "${humanSize(t.done)} / ${humanSize(t.total)}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------- EDITOR ------------------------- */

@Composable
fun EditorScreen(vm: AppViewModel) {
    var find by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var rep by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen.value = Screen.BROWSER }) { Icon(Icons.Default.ArrowBack, null) }
            Text(vm.editorPath.value, Modifier.weight(1f), fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { find = !find }) { Icon(Icons.Default.FindReplace, null) }
            TextButton(onClick = { vm.saveEditor() }) { Text("Guardar") }
        }
        val lines = vm.editorText.value.count { it == '\n' } + 1
        Text("$lines líneas · ${vm.editorText.value.length} caracteres",
            Modifier.padding(horizontal = 14.dp), fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (find) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("Buscar") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(value = rep, onValueChange = { rep = it }, label = { Text("Reemplazar") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                TextButton(onClick = {
                    if (q.isNotEmpty()) {
                        val n = vm.editorText.value.split(q).size - 1
                        vm.editorText.value = vm.editorText.value.replace(q, rep)
                        vm.message.value = "$n reemplazo(s)"
                    }
                }) { Text("Todo") }
            }
        }
        OutlinedTextField(
            value = vm.editorText.value, onValueChange = { vm.editorText.value = it },
            modifier = Modifier.fillMaxSize().padding(10.dp),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        )
    }
}

/* ------------------------- TERMINAL ------------------------- */

@Composable
fun TerminalScreen(vm: AppViewModel) {
    var cmd by remember { mutableStateOf("") }
    var histMenu by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(vm.terminalLog.value) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen.value = Screen.BROWSER }) { Icon(Icons.Default.ArrowBack, null) }
            Text("Terminal SSH", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Box {
                IconButton(onClick = { histMenu = true }) { Icon(Icons.Default.History, null) }
                DropdownMenu(expanded = histMenu, onDismissRequest = { histMenu = false }) {
                    if (vm.terminalHistory.isEmpty())
                        DropdownMenuItem(text = { Text("Sin historial") }, onClick = { histMenu = false })
                    vm.terminalHistory.reversed().take(15).forEach { h ->
                        DropdownMenuItem(text = { Text(h, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                            onClick = { cmd = h; histMenu = false })
                    }
                }
            }
            IconButton(onClick = { vm.terminalLog.value = "" }) { Icon(Icons.Default.ClearAll, null) }
        }
        Surface(Modifier.weight(1f).fillMaxWidth().padding(10.dp),
            shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(vm.terminalLog.value.ifBlank { "Escribe un comando abajo…" },
                Modifier.verticalScroll(scroll).padding(12.dp),
                fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = cmd, onValueChange = { cmd = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("ls -la") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace))
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { if (cmd.isNotBlank()) { vm.runCommand(cmd); cmd = "" } }) {
                Icon(Icons.Default.Send, null)
            }
        }
    }
}

fun fmtDate(ms: Long): String =
    if (ms <= 0) "" else SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(ms))


/* ------------------------- SINCRONIZAR ------------------------- */

@Composable
fun SyncDialog(onDismiss: () -> Unit, onRun: (Boolean, Boolean) -> Unit) {
    var toRemote by remember { mutableStateOf(true) }
    var mirror by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sincronizar carpetas") },
        text = {
            Column {
                Text("Compara la carpeta local actual con la remota actual y transfiere solo lo nuevo o modificado.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = toRemote, onClick = { toRemote = true })
                    Text("Local → Remoto")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !toRemote, onClick = { toRemote = false })
                    Text("Remoto → Local")
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = mirror, onCheckedChange = { mirror = it })
                    Spacer(Modifier.width(10.dp))
                    Text("Modo espejo (borra lo que sobre en destino)", fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onRun(toRemote, mirror) }) { Text("Sincronizar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/* ------------------------- TÚNELES ------------------------- */

@Composable
fun TunnelsScreen(vm: AppViewModel) {
    var lp by remember { mutableStateOf("8080") }
    var rh by remember { mutableStateOf("127.0.0.1") }
    var rp by remember { mutableStateOf("80") }

    Column(Modifier.fillMaxSize().padding(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen.value = Screen.BROWSER }) { Icon(Icons.Default.ArrowBack, null) }
            Text("Túneles SSH", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TextButton(onClick = { vm.stopTunnels() }) { Text("Cerrar todos") }
        }
        Text(
            "Redirige un puerto local del móvil a un host/puerto accesible desde el servidor. " +
            "Luego abre 127.0.0.1:<puerto local> en el navegador o la app que uses.",
            Modifier.padding(horizontal = 16.dp), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.padding(14.dp)) {
            Field("Puerto local", lp) { lp = it }
            Field("Host remoto (visto desde el servidor)", rh) { rh = it }
            Field("Puerto remoto", rp) { rp = it }
            Button(
                onClick = {
                    vm.addTunnel(lp.toIntOrNull() ?: 0, rh, rp.toIntOrNull() ?: 0)
                },
                Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)
            ) { Text("Abrir túnel") }
        }
        Divider()
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            items(vm.tunnels) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cable, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("127.0.0.1:${t.localPort}  →  ${t.remoteHost}:${t.remotePort}",
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        }
    }
}


/* ------------------------- BLOQUEO ------------------------- */

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("DroidSCP bloqueado", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onUnlock, shape = RoundedCornerShape(14.dp)) { Text("Desbloquear") }
        }
    }
}

/* ------------------------- AJUSTES ------------------------- */

@Composable
fun SettingsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen.value = Screen.SITES }) { Icon(Icons.Default.ArrowBack, null) }
            Text("Ajustes", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Column(Modifier.padding(20.dp)) {
            Text("Transferencias simultáneas", fontWeight = FontWeight.SemiBold)
            Text("Solo con SFTP. Más hilos = más rápido en conexiones con latencia.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { n ->
                    FilterChip(selected = vm.parallel.value == n, onClick = { vm.setParallel(n) },
                        label = { Text("$n") })
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.resume.value, onCheckedChange = { vm.setResume(it) })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Reanudar descargas")
                    Text("Continúa los archivos incompletos en lugar de empezar de cero",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.secureScreen.value, onCheckedChange = { vm.setSecureScreen(it) })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Bloquear capturas de pantalla")
                    Text("Oculta la app en el multitarea e impide capturas (al reiniciarla)",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.savePasswords.value, onCheckedChange = { vm.setSavePasswords(it) })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Guardar contraseñas")
                    Text("Si lo desactivas se borran las guardadas y se piden al conectar",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.biometric.value, onCheckedChange = { vm.setBiometric(it) })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Bloqueo con huella")
                    Text("Pide huella o PIN al abrir la app (se aplica al reiniciarla)",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/* ------------------------- ACERCA DE ------------------------- */

@Composable
fun AboutScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen.value = Screen.SITES }) { Icon(Icons.Default.ArrowBack, null) }
            Text("Acerca de", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Column(Modifier.padding(20.dp)) {
            Text("DroidSCP", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Cliente SFTP / SCP / FTP / FTPS para Android", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("© 2026 kVe — Xito Development", fontSize = 13.sp)
            Text("Publicado bajo licencia MIT. El texto completo está en el archivo LICENSE del proyecto.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Text("Seguridad", fontWeight = FontWeight.SemiBold)
            Text("Las conexiones se guardan cifradas con AES-256-GCM usando una clave del Android Keystore. " +
                "La app no permite copias de seguridad ni transferencia de datos a otro dispositivo, " +
                "y verifica la huella del servidor SSH en cada conexión.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Text("Software de terceros", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val libs = listOf(
                "SSHJ — SSH, SFTP, SCP y túneles — Apache 2.0",
                "Apache Commons Net — FTP y FTPS — Apache 2.0",
                "Bouncy Castle — criptografía y claves — Licencia BC (tipo MIT)",
                "SLF4J — logging — MIT",
                "Gson — guardado de conexiones — Apache 2.0",
                "AndroidX, Jetpack Compose y Material 3 — Apache 2.0",
                "AndroidX Biometric — bloqueo con huella — Apache 2.0"
            )
            libs.forEach {
                Text("• $it", fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            Text("\"WinSCP\" es marca de su propietario. DroidSCP no está afiliado ni respaldado por él.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
