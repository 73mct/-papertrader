package com.paper.flip

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object Store {
    lateinit var p: SharedPreferences
    fun init(c: Context) { if (!::p.isInitialized) p = c.applicationContext.getSharedPreferences("s", 0) }

    var presets: String
        get() = p.getString("presets", "0.25,0.5,1,2.5") ?: "0.25,0.5,1,2.5"
        set(v) { p.edit().putString("presets", v).apply() }
    var feePct: Float
        get() = p.getFloat("fee", 1f)
        set(v) { p.edit().putFloat("fee", v).apply() }
    var slipPct: Float
        get() = p.getFloat("slip", 5f)
        set(v) { p.edit().putFloat("slip", v).apply() }
    var tipSol: Float
        get() = p.getFloat("tip", 0.005f)
        set(v) { p.edit().putFloat("tip", v).apply() }
    var startBal: Float
        get() = p.getFloat("startbal", 10f)
        set(v) { p.edit().putFloat("startbal", v).apply() }
    var bal: Float
        get() = p.getFloat("bal", startBal)
        set(v) { p.edit().putFloat("bal", v).apply() }

    fun presetList(): List<Float> =
        presets.split(",").mapNotNull { it.trim().toFloatOrNull() }.filter { it > 0 }.take(4).ifEmpty { listOf(1f) }

    fun arr(k: String) = JSONArray(p.getString(k, "[]"))
    fun open() = JSONObject(p.getString("open", "{}"))
    fun saveOpen(o: JSONObject) { p.edit().putString("open", o.toString()).apply() }
    fun addTrade(t: JSONObject) { val a = arr("trades"); a.put(t); p.edit().putString("trades", a.toString()).apply() }
    fun reset() { p.edit().remove("trades").remove("open").putFloat("bal", startBal).apply() }
    fun fmt(v: Double) = if (v >= 1e6) "%.2fM".format(v / 1e6) else "%.1fK".format(v / 1e3)
}
