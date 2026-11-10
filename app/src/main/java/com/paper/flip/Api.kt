package com.paper.flip

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    class Coin(val ca: String, val name: String, val sym: String, val img: String, val price: Double, val mcap: Double)

    fun coin(ca: String): Coin? {
        try {
            val c = URL("https://api.dexscreener.com/latest/dex/tokens/$ca").openConnection() as HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 4000
            val pairs = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("pairs") ?: return null
            var best: JSONObject? = null
            var bl = -1.0
            for (i in 0 until pairs.length()) {
                val p = pairs.getJSONObject(i)
                if (p.getJSONObject("baseToken").getString("address") != ca) continue
                val l = p.optJSONObject("liquidity")?.optDouble("usd", 0.0) ?: 0.0
                if (l > bl) { bl = l; best = p }
            }
            val p = best ?: return null
            val b = p.getJSONObject("baseToken")
            val mc = if (p.has("marketCap")) p.getDouble("marketCap") else p.optDouble("fdv", 0.0)
            return Coin(ca, b.getString("name"), b.getString("symbol"),
                p.optJSONObject("info")?.optString("imageUrl") ?: "", p.getString("priceUsd").toDouble(), mc)
        } catch (e: Exception) { return null }
    }
}

object Img {
    private val cache = android.util.LruCache<String, Bitmap>(40)
    fun load(url: String, iv: ImageView) {
        if (url.isEmpty()) return
        val hit = cache.get(url)
        if (hit != null) { iv.setImageBitmap(hit); return }
        Thread {
            try {
                val b = BitmapFactory.decodeStream(URL(url).openStream())
                if (b != null) { cache.put(url, b); iv.post { iv.setImageBitmap(b) } }
            } catch (e: Exception) {}
        }.start()
    }
}
