package com.droidscp.net

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.ByteArrayInputStream
import java.io.File

class FtpRemoteClient(private val site: Site) : RemoteClient {

    private var ftp: FTPClient? = null
    private var cwd = "/"

    override fun connect() {
        val c: FTPClient = if (site.protocol == Protocol.FTPS) FTPSClient(false) else FTPClient()
        c.connectTimeout = 20000
        c.connect(site.host, if (site.port > 0) site.port else 21)
        if (!FTPReply.isPositiveCompletion(c.replyCode)) {
            c.disconnect(); throw RuntimeException("El servidor rechazó la conexión")
        }
        if (!c.login(site.user, site.password)) throw RuntimeException("Usuario o contraseña incorrectos")
        if (c is FTPSClient) { c.execPBSZ(0); c.execPROT("P") }
        if (site.passiveFtp) c.enterLocalPassiveMode() else c.enterLocalActiveMode()
        c.setFileType(FTPClient.BINARY_FILE_TYPE)
        c.controlEncoding = "UTF-8"
        c.bufferSize = 64 * 1024
        ftp = c
        cwd = c.printWorkingDirectory() ?: "/"
    }

    private fun c(): FTPClient = ftp ?: throw IllegalStateException("Sin conexión")

    override fun home(): String = if (site.initialPath.isNotBlank()) site.initialPath else cwd

    override fun list(path: String): List<RemoteFile> {
        val files: Array<FTPFile> = c().listFiles(path) ?: emptyArray()
        return files.filter { it.name != "." && it.name != ".." }.map { f ->
            RemoteFile(
                name = f.name,
                path = joinPath(path, f.name),
                isDir = f.isDirectory,
                size = f.size,
                mtime = f.timestamp?.timeInMillis ?: 0L,
                perms = permsOf(f),
                owner = f.user ?: "",
                group = f.group ?: ""
            )
        }
    }

    private fun permsOf(f: FTPFile): String {
        var m = 0
        for (a in arrayOf(FTPFile.USER_ACCESS, FTPFile.GROUP_ACCESS, FTPFile.WORLD_ACCESS)) {
            var v = 0
            if (f.hasPermission(a, FTPFile.READ_PERMISSION)) v += 4
            if (f.hasPermission(a, FTPFile.WRITE_PERMISSION)) v += 2
            if (f.hasPermission(a, FTPFile.EXECUTE_PERMISSION)) v += 1
            m = m * 8 + v
        }
        return String.format("%03o", m)
    }

    override fun download(remotePath: String, localFile: File, size: Long, cb: Progress) {
        localFile.parentFile?.mkdirs()
        val ins = c().retrieveFileStream(remotePath) ?: throw RuntimeException("No se pudo abrir $remotePath")
        pump(ins, localFile.outputStream(), size, cb)
        c().completePendingCommand()
    }

    override fun upload(localFile: File, remotePath: String, cb: Progress) {
        val out = c().storeFileStream(remotePath) ?: throw RuntimeException("No se pudo escribir $remotePath")
        pump(localFile.inputStream(), out, localFile.length(), cb)
        c().completePendingCommand()
    }

    override fun mkdir(path: String) {
        if (!c().makeDirectory(path)) throw RuntimeException("No se pudo crear la carpeta")
    }

    override fun createFile(path: String) {
        ByteArrayInputStream(ByteArray(0)).use {
            if (!c().storeFile(path, it)) throw RuntimeException("No se pudo crear el archivo")
        }
    }

    override fun delete(path: String, isDir: Boolean) {
        if (isDir) {
            for (f in list(path)) delete(f.path, f.isDir)
            if (!c().removeDirectory(path)) throw RuntimeException("No se pudo eliminar la carpeta")
        } else {
            if (!c().deleteFile(path)) throw RuntimeException("No se pudo eliminar el archivo")
        }
    }

    override fun rename(from: String, to: String) {
        if (!c().rename(from, to)) throw RuntimeException("No se pudo renombrar")
    }

    override fun copy(from: String, to: String, isDir: Boolean) {
        if (isDir) throw RuntimeException("Copiar carpetas no está soportado en FTP")
        val tmp = File.createTempFile("droidscp", ".bin")
        download(from, tmp, 0) { _, _ -> }
        upload(tmp, to) { _, _ -> }
        tmp.delete()
    }

    override fun chmod(path: String, octal: String) {
        if (!c().sendSiteCommand("CHMOD $octal $path"))
            throw RuntimeException("El servidor FTP no admite CHMOD")
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

    override fun close() {
        try { ftp?.logout() } catch (_: Exception) {}
        try { ftp?.disconnect() } catch (_: Exception) {}
        ftp = null
    }
}
