package com.droidscp.net

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SiteStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("droidscp_sites", Context.MODE_PRIVATE)
    private val gson = Gson()

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
}
