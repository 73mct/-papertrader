package com.paper.flip

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    private lateinit var histBox: LinearLayout
    private lateinit var sum: TextView

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Store.init(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 80, 40, 40) }
        setContentView(ScrollView(this).apply { addView(root) })

        fun field(label: String, v: String, num: Boolean = true): EditText {
            root.addView(TextView(this).apply { text = label })
            val e = EditText(this)
            e.setText(v)
            e.inputType = if (num) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT
            root.addView(e); return e
        }
        fun b(t: String, f: () -> Unit) { root.addView(Button(this).apply { text = t; setOnClickListener { f() } }) }

        val presets = field("Buy buttons (SOL, up to 4, comma separated)", Store.presets, false)
        val fee = field("Fees (% per side)", Store.feePct.toString())
        val tip = field("Priority fee / tip (SOL per trade)", Store.tipSol.toString())
        val slip = field("Slippage (% per side)", Store.slipPct.toString())
        val bal = field("Starting paper balance (SOL, applies on reset)", Store.startBal.toString())
        b("Save settings") {
            Store.presets = presets.text.toString()
            Store.feePct = fee.text.toString().toFloatOrNull() ?: 1f
            Store.tipSol = tip.text.toString().toFloatOrNull() ?: 0.005f
            Store.slipPct = slip.text.toString().toFloatOrNull() ?: 5f
            Store.startBal = bal.text.toString().toFloatOrNull() ?: 10f
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        b("1. Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        b("2. Start bubble") { startForegroundService(Intent(this, BubbleService::class.java)) }
        b("Stop bubble") { stopService(Intent(this, BubbleService::class.java)) }
        b("Reset balance and delete history") {
            AlertDialog.Builder(this).setMessage("Delete all trades and reset the balance?")
                .setPositiveButton("Reset") { _, _ -> Store.reset(); refresh() }
                .setNegativeButton("Cancel", null).show()
        }
        sum = TextView(this).apply { setPadding(0, 40, 0, 20); setTypeface(null, Typeface.BOLD) }
        root.addView(sum)
        histBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(histBox)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val a = Store.arr("trades"); var tot = 0.0; var wins = 0
        histBox.removeAllViews()
        for (i in a.length() - 1 downTo 0) {
            val t = a.getJSONObject(i)
            val got = t.getDouble("got"); val sp = t.getDouble("spent")
            tot += got - sp; if (got > sp) wins++
            val sells = t.getJSONArray("sells"); val ex = StringBuilder()
            for (j in 0 until sells.length()) {
                val sl = sells.getJSONObject(j)
                ex.append(sl.getInt("pct")).append("%@").append(Store.fmt(sl.getDouble("mc"))).append("  ")
            }
            val row = LinearLayout(this).apply { setPadding(0, 20, 0, 20) }
            val iv = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(110, 110).apply { rightMargin = 24 } }
            Img.load(t.getString("img"), iv)
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(TextView(this).apply {
                text = t.getString("sym") + "   " + "%.2fx   %+.3f SOL   %dm".format(got / sp, got - sp, t.getLong("secs") / 60)
                setTypeface(null, Typeface.BOLD)
            })
            c.addView(TextView(this).apply { text = "Entry " + Store.fmt(t.getDouble("mc_in")) + "  |  Exits: " + ex })
            c.addView(TextView(this).apply { text = t.getString("ca"); textSize = 10f })
            row.addView(iv); row.addView(c); histBox.addView(row)
        }
        sum.text = "Balance: %.3f SOL   Trades: %d   Wins: %d   Total PnL: %+.3f SOL".format(Store.bal, a.length(), wins, tot)
    }
}
