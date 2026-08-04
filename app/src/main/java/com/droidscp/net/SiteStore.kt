package com.droidscp.net

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Almacena las conexiones cifradas con una clave del Android Keystore
 * (AES-256-GCM). Si el cifrado no estuviera disponible, cae a
 * SharedPreferences normales para no dejar la app inutilizable.
 */
class SiteStore(private val ctx: Context) {

    private val gson = Gson()

    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            "droidscp_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        ctx.getSharedPreferences("droidscp_sites", Context.MODE_PRIVATE)
    }

    init { migrateLegacy() }

    /** Mueve las conexiones guardadas en claro por versiones anteriores y borra el rastro. */
    private fun migrateLegacy() {
        try {
            val old = ctx.getSharedPreferences("droidscp_sites", Context.MODE_PRIVATE)
            val json = old.getString("sites", null)
            if (json != null && prefs !== old) {
                if (prefs.getString("sites", null) == null) prefs.edit().putString("sites", json).apply()
                old.edit().clear().apply()
            }
            val older = ctx.getSharedPreferences("xito_scp_sites", Context.MODE_PRIVATE)
            if (older.contains("sites")) older.edit().clear().apply()
        } catch (_: Throwable) {}
    }

    fun load(): MutableList<Site> {
        val json = prefs.getString("sites", null) ?: return mutableListOf()
        return try {
            gson.fromJson(json, object : TypeToken<MutableList<Site>>() {}.type)
        } catch (e: Exception) { mutableListOf() }
    }

    fun save(sites: List<Site>) {
        prefs.edit().putString("sites", gson.toJson(sites)).apply()
    }

    var parallel: Int
        get() = prefs.getInt("parallel", 1)
        set(v) { prefs.edit().putInt("parallel", v.coerceIn(1, 4)).apply() }

    var biometric: Boolean
        get() = prefs.getBoolean("biometric", false)
        set(v) { prefs.edit().putBoolean("biometric", v).apply() }

    var resume: Boolean
        get() = prefs.getBoolean("resume", true)
        set(v) { prefs.edit().putBoolean("resume", v).apply() }

    /** Bloquea capturas de pantalla y miniaturas de la app. */
    var secureScreen: Boolean
        get() = prefs.getBoolean("secure_screen", true)
        set(v) { prefs.edit().putBoolean("secure_screen", v).apply() }

    /** Si es false, no se guardan contraseñas: se piden al conectar. */
    var savePasswords: Boolean
        get() = prefs.getBoolean("save_passwords", true)
        set(v) { prefs.edit().putBoolean("save_passwords", v).apply() }
}
