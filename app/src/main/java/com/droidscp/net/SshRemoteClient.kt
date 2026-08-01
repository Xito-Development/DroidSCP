package com.droidscp.net

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
    private val scpMode = site.protocol == Protocol.SCP

    override fun connect() {
        val c = SSHClient()
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
        sftp = c.newSFTPClient()
    }

    private fun sftp(): SFTPClient = sftp ?: throw IllegalStateException("Sin conexión")

    override fun home(): String {
        if (site.initialPath.isNotBlank()) return site.initialPath
        return try { sftp().canonicalize(".") } catch (e: Exception) { "/" }
    }

    override fun list(path: String): List<RemoteFile> =
        sftp().ls(path).map { r ->
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

    override fun mkdir(path: String) = sftp().mkdir(path)

    override fun createFile(path: String) {
        val rf = sftp().open(path, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
        rf.close()
    }

    override fun delete(path: String, isDir: Boolean) {
        if (isDir) deleteDirRecursive(path) else sftp().rm(path)
    }

    private fun deleteDirRecursive(path: String) {
        for (f in list(path)) if (f.isDir) deleteDirRecursive(f.path) else sftp().rm(f.path)
        sftp().rmdir(path)
    }

    override fun rename(from: String, to: String) = sftp().rename(from, to)

    override fun copy(from: String, to: String, isDir: Boolean) {
        val out = exec("cp -r ${shq(from)} ${shq(to)} && echo OK")
        if (!out.contains("OK")) throw RuntimeException(out.trim().ifBlank { "No se pudo copiar" })
    }

    override fun chmod(path: String, octal: String) {
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

    override fun size(path: String): Long = try { sftp().size(path) } catch (e: Exception) { -1 }

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
