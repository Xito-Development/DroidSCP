package com.droidscp.net

import net.schmizz.sshj.common.SecurityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.Security
import java.security.Signature

object SshCrypto {

    var hasX25519 = false
        private set
    var hasEd25519 = false
        private set

    fun init() {
        try {
            // Android trae una versión recortada y antigua de BouncyCastle: se sustituye
            // por la completa que incluye la app.
            // Se quita la BC recortada de Android y se añade la completa AL FINAL:
            // insertarla en primera posición rompe el TLS del sistema.
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        } catch (_: Throwable) {
        }
        try {
            SecurityUtils.setRegisterBouncyCastle(true)
            SecurityUtils.setSecurityProvider(BouncyCastleProvider.PROVIDER_NAME)
        } catch (_: Throwable) {
        }
        hasX25519 = try { KeyFactory.getInstance("X25519", BouncyCastleProvider.PROVIDER_NAME); true }
        catch (_: Throwable) {
            try { KeyFactory.getInstance("X25519"); true } catch (_: Throwable) { false }
        }
        hasEd25519 = try { Signature.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME); true }
        catch (_: Throwable) {
            try { Signature.getInstance("Ed25519"); true } catch (_: Throwable) { false }
        }
    }
}
