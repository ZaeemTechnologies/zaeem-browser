package com.zaeem.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

object Ui {
    // ZAEEM Obsidian Blue tokens. Names kept from v1: EMERALD = primary accent (Signature Azure), MINT = secondary accent (Ice Blue).
    // Set once per Activity start by applyTheme(); read when screens are built.
    var BG = 0xFF080C11.toInt(); var SURFACE = 0xFF101820.toInt(); var SURFACE2 = 0xFF1B2633.toInt()
    var STROKE = 0xFF314355.toInt(); var EMERALD = 0xFF39A9EB.toInt(); var MINT = 0xFF79C5F4.toInt()
    var TEXT = 0xFFF3F6FA.toInt(); var MUTED = 0xFF8695A5.toInt(); var DANGER = 0xFFF2A0A5.toInt(); var WARN = 0xFFE4BC78.toInt()
    var OK = 0xFF78C8A4.toInt(); var ON_ACCENT = 0xFF080C11.toInt(); var DANGER_FG = 0xFF080C11.toInt(); var isLight = false

    /** mode: "obsidian" (default), "amoled", "light", "system". */
    fun applyTheme(mode: String, systemDark: Boolean) {
        val m = if (mode == "system") (if (systemDark) "obsidian" else "light") else mode
        isLight = m == "light"
        when (m) {
            "light" -> {
                BG = 0xFFF5F7FA.toInt(); SURFACE = 0xFFFFFFFF.toInt(); SURFACE2 = 0xFFE7EDF3.toInt(); STROKE = 0xFFCBD5E1.toInt()
                EMERALD = 0xFF176B9E.toInt(); MINT = 0xFF176B9E.toInt(); TEXT = 0xFF101820.toInt(); MUTED = 0xFF4D5C6C.toInt()
                DANGER = 0xFFA52C35.toInt(); WARN = 0xFF805600.toInt(); OK = 0xFF176B50.toInt(); ON_ACCENT = 0xFFFFFFFF.toInt(); DANGER_FG = 0xFFFFFFFF.toInt()
            }
            else -> {
                BG = if (m == "amoled") 0xFF000000.toInt() else 0xFF080C11.toInt()
                SURFACE = if (m == "amoled") 0xFF080C11.toInt() else 0xFF101820.toInt()
                SURFACE2 = if (m == "amoled") 0xFF101820.toInt() else 0xFF1B2633.toInt()
                STROKE = 0xFF314355.toInt(); EMERALD = 0xFF39A9EB.toInt(); MINT = 0xFF79C5F4.toInt(); TEXT = 0xFFF3F6FA.toInt(); MUTED = 0xFF8695A5.toInt()
                DANGER = 0xFFF2A0A5.toInt(); WARN = 0xFFE4BC78.toInt(); OK = 0xFF78C8A4.toInt(); ON_ACCENT = 0xFF080C11.toInt(); DANGER_FG = 0xFF080C11.toInt()
            }
        }
    }

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun round(c: Context, fill: Int, r: Int = 14, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(c, r).toFloat(); if (stroke != 0) setStroke(dp(c, 1), stroke)
    }

    fun ripple(c: Context, base: GradientDrawable) = RippleDrawable(ColorStateList.valueOf((EMERALD and 0xFFFFFF) or 0x33000000), base, null)

    fun text(c: Context, s: CharSequence, size: Float = 15f, color: Int = TEXT, bold: Boolean = false) = TextView(c).apply {
        text = s; textSize = size; setTextColor(color); if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    /** kind: 0 primary (emerald), 1 secondary (outline), 2 danger. */
    fun button(c: Context, label: String, kind: Int = 0, onClick: () -> Unit) = TextView(c).apply {
        text = label; textSize = 15f; gravity = Gravity.CENTER; isClickable = true; isFocusable = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val (fill, stroke, fg) = when (kind) {
            0 -> Triple(EMERALD, 0, ON_ACCENT)
            2 -> Triple(DANGER, 0, DANGER_FG)
            else -> Triple(SURFACE, STROKE, TEXT)
        }
        setTextColor(fg); background = ripple(c, round(c, fill, 14, stroke))
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14)); setOnClickListener { onClick() }
    }

    fun lp(c: Context, top: Int = 0, bottom: Int = 0, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, w: Int = ViewGroup.LayoutParams.MATCH_PARENT) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(c, top); bottomMargin = dp(c, bottom) }

    fun card(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; background = round(c, SURFACE, 16, STROKE)
        setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
    }

    fun section(c: Context, t: String) = text(c, t.uppercase(), 12f, MINT, true).apply {
        letterSpacing = 0.1f; setPadding(dp(c, 4), dp(c, 18), 0, dp(c, 6))
    }

    fun switchView(c: Context, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(c).apply {
        isChecked = checked
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(states, intArrayOf(MINT, MUTED))
        trackTintList = ColorStateList(states, intArrayOf((EMERALD and 0xFFFFFF) or 0x66000000, STROKE))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    fun toggle(c: Context, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = round(c, SURFACE, 14, STROKE); setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
        }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(c, title, 15f, TEXT, true))
        if (sub != null) col.addView(text(c, sub, 12f, MUTED).apply { setPadding(0, dp(c, 2), dp(c, 8), 0) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(switchView(c, checked, onChange))
        return row
    }

    fun toggleChoice(c: Context, title: String, selected: Boolean, onClick: () -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isClickable = true
        background = ripple(c, round(c, if (selected) SURFACE2 else SURFACE, 14, if (selected) EMERALD else STROKE))
        setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14)); setOnClickListener { onClick() }
        addView(text(c, if (selected) "◉" else "○", 18f, if (selected) EMERALD else MUTED).apply { setPadding(0, 0, dp(c, 12), 0) })
        addView(text(c, title, 15f, TEXT, true))
    }

    fun nav(c: Context, title: String, sub: String?, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isClickable = true
            background = ripple(c, round(c, SURFACE, 14, STROKE)); setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14))
            setOnClickListener { onClick() }
        }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(c, title, 15f, TEXT, true))
        if (sub != null) col.addView(text(c, sub, 12f, MUTED).apply { setPadding(0, dp(c, 2), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text(c, "›", 22f, MUTED))
        return row
    }

    fun kv(c: Context, k: String, v: String, vColor: Int = TEXT) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(c, 6), 0, dp(c, 6))
        addView(text(c, k, 14f, MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        addView(text(c, v, 14f, vColor, true).apply { gravity = Gravity.END; maxWidth = dp(c, 220) })
    }

    fun chip(c: Context, label: String, color: Int) = text(c, label, 12f, color, true).apply {
        background = round(c, SURFACE2, 20, color); setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4))
    }

    fun header(c: Context, title: String, onBack: (() -> Unit)?, action: Pair<String, () -> Unit>? = null): View {
        val h = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(c, 6), dp(c, 8), dp(c, 12), dp(c, 8)) }
        if (onBack != null) h.addView(text(c, "‹", 30f, MINT).apply {
            setPadding(dp(c, 12), 0, dp(c, 14), dp(c, 4)); isClickable = true; setOnClickListener { onBack() }
        })
        h.addView(text(c, title, 18f, TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        if (action != null) h.addView(text(c, action.first, 14f, EMERALD, true).apply {
            setPadding(dp(c, 10), dp(c, 8), dp(c, 4), dp(c, 8)); isClickable = true; setOnClickListener { action.second() }
        })
        return h
    }

    /** Standard sub-screen: header + scrolling column. */
    fun scaffold(c: Context, title: String, onBack: (() -> Unit)?, action: Pair<String, () -> Unit>? = null, body: LinearLayout.() -> Unit): View {
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        root.addView(header(c, title, onBack, action))
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, 16), dp(c, 4), dp(c, 16), dp(c, 28)) }
        col.body()
        root.addView(ScrollView(c).apply { addView(col); isFillViewport = true }, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    fun LinearLayout.add(v: View, top: Int = 8): View { addView(v, lp(context, top)); return v }
}

/** Shield glyph drawn with a Path (no image assets). */
class ShieldView(c: Context, var mode: Int = 0, var tint: Int = Ui.EMERALD) : View(c) { // mode 0 = Z, 1 = check, 2 = exclamation
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); if (w <= 0) return
        val p = Path().apply {
            moveTo(w * .5f, h * .03f); lineTo(w * .92f, h * .18f); lineTo(w * .92f, h * .52f)
            cubicTo(w * .92f, h * .75f, w * .68f, h * .92f, w * .5f, h * .98f)
            cubicTo(w * .32f, h * .92f, w * .08f, h * .75f, w * .08f, h * .52f); lineTo(w * .08f, h * .18f); close()
        }
        fill.color = (tint and 0x00FFFFFF) or 0x26000000
        cv.drawPath(p, fill)
        line.color = tint; line.strokeWidth = w * .045f
        cv.drawPath(p, line)
        line.strokeWidth = w * .075f
        val g = Path()
        when (mode) {
            0 -> { g.moveTo(w * .32f, h * .32f); g.lineTo(w * .68f, h * .32f); g.lineTo(w * .32f, h * .66f); g.lineTo(w * .68f, h * .66f) }
            1 -> { g.moveTo(w * .30f, h * .50f); g.lineTo(w * .45f, h * .65f); g.lineTo(w * .71f, h * .35f) }
            else -> { g.moveTo(w * .5f, h * .28f); g.lineTo(w * .5f, h * .56f); g.moveTo(w * .5f, h * .70f); g.lineTo(w * .5f, h * .71f) }
        }
        cv.drawPath(g, line)
    }
}


/** ZAEEM Browser symbol: minimal globe (platinum lines) with one azure orbital ring. Matte, no glow. */
class GlobeView(c: Context) : View(c) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); if (w <= 0) return
        val cx = w / 2f; val cy = height / 2f; val r = w * .30f
        line.color = Ui.TEXT; line.strokeWidth = w * .028f
        cv.drawCircle(cx, cy, r, line)
        cv.drawOval(RectF(cx - r * .42f, cy - r, cx + r * .42f, cy + r), line)
        cv.drawLine(cx, cy - r, cx, cy + r, line); cv.drawLine(cx - r, cy, cx + r, cy, line)
        cv.drawArc(RectF(cx - r, cy - r * 1.25f, cx + r, cy - r * .15f), 20f, 140f, false, line)
        cv.drawArc(RectF(cx - r, cy + r * .15f, cx + r, cy + r * 1.25f), 200f, 140f, false, line)
        line.color = Ui.EMERALD; line.strokeWidth = w * .04f
        cv.save(); cv.rotate(-22f, cx, cy); cv.drawOval(RectF(cx - w * .43f, cy - w * .12f, cx + w * .43f, cy + w * .12f), line); cv.restore()
    }
}
