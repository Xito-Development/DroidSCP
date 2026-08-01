package com.droidscp.net

enum class Protocol { SFTP, SCP, FTP, FTPS }

data class Site(
    var id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "",
    var protocol: Protocol = Protocol.SFTP,
    var host: String = "",
    var port: Int = 22,
    var user: String = "",
    var password: String = "",
    var keyPath: String = "",
    var keyPassphrase: String = "",
    var initialPath: String = "",
    var passiveFtp: Boolean = true,
    var favorites: MutableList<String> = mutableListOf(),
    var hostKey: String = ""
)

data class RemoteFile(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long,
    val perms: String,
    val owner: String = "",
    val group: String = ""
)

enum class SortBy { NAME, SIZE, DATE, TYPE }

fun humanSize(b: Long): String {
    if (b < 1024) return "$b B"
    val u = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble() / 1024.0
    var i = 0
    while (v >= 1024 && i < u.size - 1) { v /= 1024.0; i++ }
    return String.format("%.1f %s", v, u[i])
}

fun joinPath(base: String, name: String): String {
    val b = if (base.endsWith("/")) base.dropLast(1) else base
    return if (b.isEmpty()) "/$name" else "$b/$name"
}

fun parentPath(path: String): String {
    if (path == "/" || path.isEmpty()) return "/"
    val p = path.trimEnd('/')
    val i = p.lastIndexOf('/')
    return if (i <= 0) "/" else p.substring(0, i)
}
