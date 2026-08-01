package com.droidscp.net

import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.password.PasswordUtils
import net.schmizz.sshj.xfer.FileSystemFile
import java.io.File
import java.util.EnumSet

class SshRemoteClient(
    private val site: Site,
    private val onHostKey: (String) -> Unit = {}
) : RemoteClient {

    private var ssh: SSHClient? = null
    private var sftp: SFTPClient? = null
    private var scpMode = false   // solo si el servidor no ofrece subsistema SFTP

    private fun buildConfig(): DefaultConfig {
        val cfg = DefaultConfig()
        if (!SshCrypto.hasX25519) {
            cfg.keyExchangeFactories = cfg.keyExchangeFactories
                .filter { !it.name.lowercase().contains("curve25519") }
        }
        if (!SshCrypto.hasEd25519) {
            cfg.keyAlgorithms = cfg.keyAlgorithms
                .filter { !it.name.lowercase().contains("ed25519") }
        }
        return cfg
    }

    override fun connect() {
        SshCrypto.init()
        val c = SSHClient(buildConfig())
        c.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: java.security.PublicKey): Boolean {
                val fp = SecurityUtils.getFingerprint(key)
                if (site.hostKey.isBlank()) { site.hostKey = fp; onHostKey(fp); return true }
                if (site.hostKey == fp) return true
                throw RuntimeException(
                    "La huella del servidor ha cambiado.\nGuardada: ${site.hostKey}\nActual: $fp\n" +
                    "Si el cambio es legítimo, borra la huella en los ajustes de la conexión."
                )
            }
            override fun findExistingAlgorithms(hostname: String, port: Int): MutableList<String> = mutableListOf()
        })
        c.connectTimeout = 20000
        c.timeout = 60000
        c.connect(site.host, if (site.port > 0) site.port else 22)
        if (site.keyPath.isNotBlank()) {
            val kp = if (site.keyPassphrase.isNotBlank())
                c.loadKeys(site.keyPath, PasswordUtils.createOneOff(site.keyPassphrase.toCharArray()))
            else c.loadKeys(site.keyPath)
            c.authPublickey(site.user, kp)
        } else {
            c.authPassword(site.user, site.password)
        }
        ssh = c
        sftp = try {
            scpMode = false
            c.newSFTPClient()
        } catch (e: Exception) {
            // Algunos servidores (hostings compartidos) no tienen subsistema SFTP:
            // en ese caso se usa SCP para transferir y comandos de shell para listar.
            scpMode = true
            null
        }
    }

    private fun sftp(): SFTPClient = sftp ?: throw IllegalStateException("Sin conexión")

    override fun home(): String {
        if (site.initialPath.isNotBlank()) return site.initialPath
        if (scpMode) return exec("pwd").trim().lines().firstOrNull()?.trim() ?: "/"
        return try { sftp().canonicalize(".") } catch (e: Exception) { "/" }
    }

    override fun list(path: String): List<RemoteFile> {
        if (scpMode) return listViaShell(path)
        return sftp().ls(path).map { r ->
            val a = r.attributes
            RemoteFile(
                name = r.name,
                path = r.path,
                isDir = r.isDirectory,
                size = a.size,
                mtime = a.mtime * 1000,
                perms = String.format("%03o", a.mode.permissionsMask),
                owner = a.uid.toString(),
                group = a.gid.toString()
            )
        }
    }

    private fun listViaShell(path: String): List<RemoteFile> {
        val out = exec("ls -la --time-style=+%s ${shq(path)} 2>/dev/null")
        val res = mutableListOf<RemoteFile>()
        for (line in out.lines()) {
            val p = line.trim().split(Regex("\\s+"), 7)
            if (p.size < 7 || p[0].length < 10) continue
            val name = p[6]
            if (name == "." || name == "..") continue
            val isDir = p[0].startsWith("d")
            res.add(
                RemoteFile(
                    name = name,
                    path = joinPath(path, name),
                    isDir = isDir,
                    size = p[4].toLongOrNull() ?: 0L,
                    mtime = (p[5].toLongOrNull() ?: 0L) * 1000,
                    perms = permsFromRwx(p[0]),
                    owner = p[2],
                    group = p[3]
                )
            )
        }
        return res
    }

    private fun permsFromRwx(s: String): String {
        var m = 0
        for (g in 0..2) {
            var v = 0
            val base = 1 + g * 3
            if (s.length > base + 2) {
                if (s[base] == 'r') v += 4
                if (s[base + 1] == 'w') v += 2
                if (s[base + 2] == 'x' || s[base + 2] == 's') v += 1
            }
            m = m * 8 + v
        }
        return String.format("%03o", m)
    }

    override fun download(remotePath: String, localFile: File, size: Long, cb: Progress) {
        localFile.parentFile?.mkdirs()
        if (scpMode) {
            ssh!!.newSCPFileTransfer().download(remotePath, FileSystemFile(localFile))
            cb(localFile.length(), localFile.length())
            return
        }
        val rf = sftp().open(remotePath)
        val total = if (size > 0) size else rf.length()
        pump(rf.ReadAheadRemoteFileInputStream(16), localFile.outputStream(), total, cb)
        try { rf.close() } catch (_: Exception) {}
    }

    override fun upload(localFile: File, remotePath: String, cb: Progress) {
        if (scpMode) {
            ssh!!.newSCPFileTransfer().upload(FileSystemFile(localFile), remotePath)
            cb(localFile.length(), localFile.length())
            return
        }
        val rf = sftp().open(
            remotePath,
            EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
        )
        pump(localFile.inputStream(), rf.RemoteFileOutputStream(0, 16), localFile.length(), cb)
        try { rf.close() } catch (_: Exception) {}
    }

    override fun mkdir(path: String) {
        if (scpMode) { requireOk(exec("mkdir -p ${shq(path)} && echo OK"), "No se pudo crear la carpeta"); return }
        sftp().mkdir(path)
    }

    private fun requireOk(out: String, msg: String) {
        if (!out.contains("OK")) throw RuntimeException(out.trim().ifBlank { msg })
    }

    override fun createFile(path: String) {
        if (scpMode) { requireOk(exec("touch ${shq(path)} && echo OK"), "No se pudo crear el archivo"); return }
        val rf = sftp().open(path, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
        rf.close()
    }

    override fun delete(path: String, isDir: Boolean) {
        if (scpMode) { requireOk(exec("rm -rf ${shq(path)} && echo OK"), "No se pudo eliminar"); return }
        if (isDir) deleteDirRecursive(path) else sftp().rm(path)
    }

    private fun deleteDirRecursive(path: String) {
        for (f in list(path)) if (f.isDir) deleteDirRecursive(f.path) else sftp().rm(f.path)
        sftp().rmdir(path)
    }

    override fun rename(from: String, to: String) {
        if (scpMode) { requireOk(exec("mv ${shq(from)} ${shq(to)} && echo OK"), "No se pudo renombrar"); return }
        sftp().rename(from, to)
    }

    override fun copy(from: String, to: String, isDir: Boolean) {
        val out = exec("cp -r ${shq(from)} ${shq(to)} && echo OK")
        if (!out.contains("OK")) throw RuntimeException(out.trim().ifBlank { "No se pudo copiar" })
    }

    override fun chmod(path: String, octal: String) {
        if (scpMode) { requireOk(exec("chmod $octal ${shq(path)} && echo OK"), "No se pudo aplicar permisos"); return }
        sftp().chmod(path, Integer.parseInt(octal, 8))
    }

    override fun readText(path: String): String {
        val tmp = File.createTempFile("droidscp", ".txt")
        download(path, tmp, 0) { _, _ -> }
        val t = tmp.readText(); tmp.delete(); return t
    }

    override fun writeText(path: String, text: String) {
        val tmp = File.createTempFile("droidscp", ".txt")
        tmp.writeText(text); upload(tmp, path) { _, _ -> }; tmp.delete()
    }

    override fun supportsTerminal() = true

    override fun supportsResume() = !scpMode

    override fun downloadResume(remotePath: String, localFile: File, size: Long, offset: Long, cb: Progress) {
        if (scpMode || offset <= 0) { download(remotePath, localFile, size, cb); return }
        val rf = sftp().open(remotePath)
        val total = if (size > 0) size else rf.length()
        val ins = rf.ReadAheadRemoteFileInputStream(16, offset)
        val out = java.io.FileOutputStream(localFile, true)
        pump(ins, out, total, cb, offset)
        try { rf.close() } catch (_: Exception) {}
    }

    override fun size(path: String): Long =
        if (scpMode) -1 else try { sftp().size(path) } catch (e: Exception) { -1 }

    override fun compress(dir: String, names: List<String>, archive: String) {
        val list = names.joinToString(" ") { shq(it) }
        val cmd = if (archive.endsWith(".zip"))
            "cd ${shq(dir)} && zip -r ${shq(archive)} $list && echo OK"
        else "cd ${shq(dir)} && tar -czf ${shq(archive)} $list && echo OK"
        val out = exec(cmd)
        if (!out.contains("OK")) throw RuntimeException(out.trim().ifBlank { "No se pudo comprimir" })
    }

    override fun extract(archivePath: String, destDir: String) {
        val cmd = when {
            archivePath.endsWith(".zip") -> "unzip -o ${shq(archivePath)} -d ${shq(destDir)} && echo OK"
            archivePath.endsWith(".tar") -> "tar -xf ${shq(archivePath)} -C ${shq(destDir)} && echo OK"
            archivePath.endsWith(".gz") || archivePath.endsWith(".tgz") ->
                "tar -xzf ${shq(archivePath)} -C ${shq(destDir)} && echo OK"
            archivePath.endsWith(".bz2") -> "tar -xjf ${shq(archivePath)} -C ${shq(destDir)} && echo OK"
            archivePath.endsWith(".xz") -> "tar -xJf ${shq(archivePath)} -C ${shq(destDir)} && echo OK"
            else -> throw RuntimeException("Formato no reconocido")
        }
        val out = exec(cmd)
        if (!out.contains("OK")) throw RuntimeException(out.trim().ifBlank { "No se pudo descomprimir" })
    }

    private val forwarders = mutableListOf<Thread>()
    private val sockets = mutableListOf<java.net.ServerSocket>()

    fun startForward(localPort: Int, remoteHost: String, remotePort: Int) {
        val ss = java.net.ServerSocket()
        ss.reuseAddress = true
        ss.bind(java.net.InetSocketAddress("127.0.0.1", localPort))
        sockets.add(ss)
        val t = Thread {
            try {
                ssh!!.newLocalPortForwarder(
                    Parameters("127.0.0.1", localPort, remoteHost, remotePort), ss
                ).listen()
            } catch (_: Exception) {}
        }
        t.isDaemon = true
        t.start()
        forwarders.add(t)
    }

    fun stopForwards() {
        for (s in sockets) try { s.close() } catch (_: Exception) {}
        sockets.clear(); forwarders.clear()
    }

    override fun exec(command: String): String {
        val s = ssh!!.startSession()
        try {
            val cmd = s.exec(command)
            val out = IOUtils.readFully(cmd.inputStream).toString()
            val err = IOUtils.readFully(cmd.errorStream).toString()
            cmd.join()
            return out + err
        } finally { try { s.close() } catch (_: Exception) {} }
    }

    override fun close() {
        stopForwards()
        try { sftp?.close() } catch (_: Exception) {}
        try { ssh?.disconnect() } catch (_: Exception) {}
        sftp = null; ssh = null
    }
}
