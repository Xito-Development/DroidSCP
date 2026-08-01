package com.droidscp.net

import java.io.File
import java.io.InputStream
import java.io.OutputStream

typealias Progress = (done: Long, total: Long) -> Unit

interface RemoteClient {
    fun connect()
    fun home(): String
    fun list(path: String): List<RemoteFile>
    fun download(remotePath: String, localFile: File, size: Long, cb: Progress)
    fun upload(localFile: File, remotePath: String, cb: Progress)
    fun mkdir(path: String)
    fun createFile(path: String)
    fun delete(path: String, isDir: Boolean)
    fun rename(from: String, to: String)
    fun copy(from: String, to: String, isDir: Boolean)
    fun chmod(path: String, octal: String)
    fun readText(path: String): String
    fun writeText(path: String, text: String)
    fun supportsTerminal(): Boolean = false
    fun size(path: String): Long = -1
    fun supportsResume(): Boolean = false
    fun downloadResume(remotePath: String, localFile: java.io.File, size: Long, offset: Long, cb: Progress) {
        download(remotePath, localFile, size, cb)
    }
    fun compress(dir: String, names: List<String>, archive: String) {
        throw RuntimeException("Comprimir solo está disponible por SSH/SFTP")
    }
    fun extract(archivePath: String, destDir: String) {
        throw RuntimeException("Descomprimir solo está disponible por SSH/SFTP")
    }
    fun exec(command: String): String = "No soportado por este protocolo."
    fun close()
}

object ClientFactory {
    fun create(site: Site, onHostKey: (String) -> Unit = {}): RemoteClient = when (site.protocol) {
        Protocol.SFTP, Protocol.SCP -> SshRemoteClient(site, onHostKey)
        Protocol.FTP, Protocol.FTPS -> FtpRemoteClient(site)
    }
}

internal fun pump(input: InputStream, output: OutputStream, total: Long, cb: Progress, startAt: Long = 0) {
    val buf = ByteArray(64 * 1024)
    var done = startAt
    var last = 0L
    input.use { i ->
        output.use { o ->
            while (true) {
                val n = i.read(buf)
                if (n <= 0) break
                o.write(buf, 0, n)
                done += n
                if (done - last > 128 * 1024) { last = done; cb(done, total) }
            }
            o.flush()
        }
    }
    cb(done, total)
}

internal fun shq(s: String) = "'" + s.replace("'", "'\\''") + "'"
