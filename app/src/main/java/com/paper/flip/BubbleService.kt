package com.paper.flip

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class BubbleService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var bubble: TextView
    private lateinit var bp: WindowManager.LayoutParams
    private var panel: LinearLayout? = null
    private var ivCoin: ImageView? = null
    private var tvTitle: TextView? = null
    private var tvMc: TextView? = null
    private var tvPnl: TextView? = null
    private var tvX: TextView? = null
    private var tvBal: TextView? = null
    private var tvEntry: TextView? = null
    @Volatile private var ca: String? = null
    @Volatile private var alive = true
    private val coins = HashMap<String, Api.Coin>()
    private val h = Handler(Looper.getMainLooper())
    private val GREEN = "#00E5A0"
    private val RED = "#FF5C7A"

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("b", "Bubble", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "b").setContentTitle("Paper trader running")
            .setSmallIcon(android.R.drawable.ic_menu_info_details).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        makeBubble()
        startWorker()
    }

    // ---------- price worker: current CA + every open position ----------
    private fun startWorker() {
        Thread {
            while (alive) {
                val set = LinkedHashSet<String>()
                ca?.let { set.add(it) }
                try { val ks = Store.open().keys(); while (ks.hasNext()) set.add(ks.next()) } catch (e: Exception) {}
                for (c in set) {
                    val r = Api.coin(c)
                    if (r != null) h.post { coins[c] = r; render() }
                }
                try { Thread.sleep(1500) } catch (e: Exception) {}
            }
        }.start()
    }

    // ---------- bubble / pill ----------
    private fun makeBubble() {
        bubble = TextView(this).apply {
            text = "PT"; setTextColor(Color.WHITE); gravity = Gravity.CENTER; textSize = 12f
            setTypeface(null, Typeface.BOLD); alpha = 0.93f
            setPadding(dp(14), dp(8), dp(14), dp(8)); minWidth = dp(56); minHeight = dp(40)
            background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(Color.parseColor("#6C5CE7")) }
        }
        bp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 400 }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = bp.x; oy = bp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - sx) > 10 || Math.abs(e.rawY - sy) > 10) moved = true
                    bp.x = ox + (e.rawX - sx).toInt(); bp.y = oy + (e.rawY - sy).toInt()
                    wm.updateViewLayout(bubble, bp)
                }
                MotionEvent.ACTION_UP -> if (!moved) togglePanel()
            }
            true
        }
        wm.addView(bubble, bp)
    }

    private fun tint(c: String) { (bubble.background as GradientDrawable).setColor(Color.parseColor(c)) }

    // ---------- panel (the HUD) ----------
    private fun tv(size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun btn(t: String, c: String, weighted: Boolean = true, f: () -> Unit) = Button(this).apply {
        text = t; textSize = 12f; isAllCaps = false; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.parseColor(c)) }
        setOnClickListener { f() }
        layoutParams = if (weighted) LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        else LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { setMargins(dp(3), 0, dp(3), 0) }
    }

    private fun col(label: String, v: TextView, g: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = g
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addView(tv(10f, Color.GRAY).apply { text = label })
        addView(v)
    }

    private fun togglePanel() {
        val old = panel
        if (old != null) {
            wm.removeView(old); panel = null; bubble.visibility = View.VISIBLE
            ivCoin = null; tvTitle = null; tvMc = null; tvPnl = null; tvX = null; tvBal = null; tvEntry = null
            render(); return
        }
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#F00B0B16")) }
        }
        val iv = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(8) } }
        val title = tv(15f, Color.parseColor(GREEN), true).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(iv); head.addView(title)
        head.addView(btn("Paste CA", "#2D3436", false) { readClip() })
        head.addView(btn("—", "#2D3436", false) { togglePanel() })
        p.addView(head)

        val mc = tv(18f, Color.WHITE, true); val pnl = tv(16f, Color.WHITE, true); val x = tv(18f, Color.WHITE, true)
        val stats = LinearLayout(this).apply { setPadding(0, dp(10), 0, dp(10)) }
        stats.addView(col("Live Market Cap", mc, Gravity.START))
        stats.addView(col("Live PnL", pnl, Gravity.CENTER_HORIZONTAL))
        stats.addView(col("Multiple", x, Gravity.END))
        p.addView(stats)

        val bal = tv(11f, Color.WHITE, true)
        val buyHead = LinearLayout(this)
        buyHead.addView(tv(11f, Color.parseColor(GREEN), true).apply { text = "INSTANT BUY (SOL)"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        buyHead.addView(bal)
        p.addView(buyHead)
        val buyRow = LinearLayout(this)
        for (a in Store.presetList()) buyRow.addView(btn(a.toString().removeSuffix(".0") + " SOL", "#0F5C47") { buy(a) })
        p.addView(buyRow)

        p.addView(tv(11f, Color.parseColor(RED), true).apply { text = "INSTANT SELL (%)"; setPadding(0, dp(8), 0, 0) })
        val sellRow = LinearLayout(this)
        for (pc in listOf(25, 50, 75, 100)) sellRow.addView(btn("$pc%", "#6B1E2C") { sell(pc) })
        p.addView(sellRow)

        val entry = tv(11f, Color.GRAY).apply { setPadding(0, dp(6), 0, 0) }
        p.addView(entry)
        p.addView(tv(10f, Color.GRAY).apply { text = "Fee: ${Store.feePct}% + ${Store.tipSol} SOL  |  Slippage: ${Store.slipPct}%" })

        ivCoin = iv; tvTitle = title; tvMc = mc; tvPnl = pnl; tvX = x; tvBal = bal; tvEntry = entry
        panel = p
        bubble.visibility = View.GONE
        p.viewTreeObserver.addOnWindowFocusChangeListener { if (it) readClip() }
        wm.addView(p, WindowManager.LayoutParams(dp(340), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(90) })
        render()
    }

    private fun readClip() {
        val t = getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString() ?: return
        val m = Regex("[1-9A-HJ-NP-Za-km-z]{32,44}").find(t)?.value ?: return
        if (m == ca) return
        ca = m; render()
        Thread { val r = Api.coin(m); if (r != null) h.post { coins[m] = r; render() } }.start()
    }

    // ---------- math ----------
    private fun sellValue(units: Double, price: Double) =
        maxOf(0.0, units * price * (1 - Store.slipPct / 100.0) * (1 - Store.feePct / 100.0) - Store.tipSol)

    private fun focus(o: JSONObject): String? {
        val c = ca
        if (c != null && o.has(c)) return c
        var best: String? = null; var bt = 0L
        val ks = o.keys()
        while (ks.hasNext()) { val k = ks.next(); val t = o.getJSONObject(k).getLong("t"); if (t >= bt) { bt = t; best = k } }
        return best
    }

    private fun render() {
        val o = Store.open()
        // collapsed pill: tracks the coin you're in
        val f = focus(o)
        val fp = f?.let { o.optJSONObject(it) }; val fc = f?.let { coins[it] }
        if (fp != null && fc != null) {
            val tot = fp.getDouble("got") + sellValue(fp.getDouble("units"), fc.price)
            val sp = fp.getDouble("spent"); val x = tot / sp
            bubble.text = "%.2fx  %+.3f SOL".format(x, tot - sp) + "\n${fc.sym} · in ${Store.fmt(fp.getDouble("mc"))}"
            tint(if (x >= 1) "#00B894" else "#D63031")
        } else { bubble.text = if (fp != null) "..." else "PT"; tint("#6C5CE7") }

        // expanded panel
        val iv = ivCoin ?: return
        val cur = ca?.let { coins[it] }; val cp = ca?.let { o.optJSONObject(it) }
        tvTitle?.text = if (cur != null) "\$" + cur.sym else if (ca == null) "Paste a CA" else "Loading..."
        tvMc?.text = if (cur != null) Store.fmt(cur.mcap) else "--"
        tvBal?.text = "Bal: %.2f SOL".format(Store.bal)
        val url = cur?.img ?: ""
        if (iv.tag != url) { iv.tag = url; iv.setImageBitmap(null); Img.load(url, iv) }
        if (cp != null && cur != null) {
            val tot = cp.getDouble("got") + sellValue(cp.getDouble("units"), cur.price)
            val sp = cp.getDouble("spent"); val x = tot / sp
            val mins = (System.currentTimeMillis() - cp.getLong("t")) / 60000
            val col = Color.parseColor(if (x >= 1) GREEN else RED)
            tvPnl?.text = "%+.3f SOL".format(tot - sp); tvPnl?.setTextColor(col)
            tvX?.text = "%.2fx".format(x); tvX?.setTextColor(col)
            tvEntry?.text = "Avg entry MC ${Store.fmt(cp.getDouble("mc"))}  |  held ${mins}m  |  realized " + "%.3f".format(cp.getDouble("got")) + " SOL"
        } else {
            tvPnl?.text = "--"; tvX?.text = "--"
            tvPnl?.setTextColor(Color.WHITE); tvX?.setTextColor(Color.WHITE)
            tvEntry?.text = "No position"
        }
    }

    // ---------- trading ----------
    private fun buy(amt: Float) {
        val c = ca?.let { coins[it] } ?: return
        if (amt > Store.bal) { Toast.makeText(this, "Not enough paper balance", Toast.LENGTH_SHORT).show(); return }
        val net = amt * (1 - Store.feePct / 100.0) - Store.tipSol
        if (net <= 0) return
        val ep = c.price * (1 + Store.slipPct / 100.0)
        val o = Store.open()
        val pos = o.optJSONObject(c.ca) ?: JSONObject().put("sym", c.sym).put("name", c.name).put("img", c.img)
            .put("mc", c.mcap).put("spent", 0.0).put("units", 0.0).put("got", 0.0)
            .put("t", System.currentTimeMillis()).put("sells", JSONArray())
        val sp = pos.getDouble("spent")
        pos.put("mc", (pos.getDouble("mc") * sp + c.mcap * amt) / (sp + amt))
        pos.put("spent", sp + amt)
        pos.put("units", pos.getDouble("units") + net / ep)
        o.put(c.ca, pos); Store.saveOpen(o)
        Store.bal = Store.bal - amt
        render()
    }

    private fun sell(pct: Int) {
        val c = ca?.let { coins[it] } ?: return
        val o = Store.open(); val pos = o.optJSONObject(c.ca) ?: return
        val units = pos.getDouble("units"); val sold = units * pct / 100.0
        val got = sellValue(sold, c.price)
        pos.put("units", units - sold).put("got", pos.getDouble("got") + got)
        pos.getJSONArray("sells").put(JSONObject().put("pct", pct).put("mc", c.mcap).put("got", got))
        Store.bal = Store.bal + got.toFloat()
        if (pct >= 100) {
            Store.addTrade(JSONObject().put("ca", c.ca).put("sym", pos.getString("sym")).put("img", pos.getString("img"))
                .put("mc_in", pos.getDouble("mc")).put("spent", pos.getDouble("spent")).put("got", pos.getDouble("got"))
                .put("secs", (System.currentTimeMillis() - pos.getLong("t")) / 1000).put("sells", pos.getJSONArray("sells")))
            o.remove(c.ca)
        } else o.put(c.ca, pos)
        Store.saveOpen(o)
        render()
    }

    override fun onDestroy() {
        alive = false
        panel?.let { wm.removeView(it) }
        wm.removeView(bubble)
        super.onDestroy()
    }
}
