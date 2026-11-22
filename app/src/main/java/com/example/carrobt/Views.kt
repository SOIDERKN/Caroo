package com.example.carrobt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Contenedor que coloca los hijos segun la captura de referencia (1568 x 704). */
class Root(ctx: Context) : ViewGroup(ctx) {
    private class Spec(val v: View, val cx: Float, val cy: Float, val w: Float, val h: Float)

    private val specs = ArrayList<Spec>()

    fun put(v: View, cx: Float, cy: Float, w: Float, h: Float) {
        specs.add(Spec(v, cx, cy, w, h))
        addView(v)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val sx = (r - l) / 1568f
        val sy = (b - t) / 704f
        val s = min(sx, sy)
        for (sp in specs) {
            val w = (sp.w * s).toInt()
            val h = (sp.h * s).toInt()
            val x = (sp.cx * sx - w / 2f).toInt()
            val y = (sp.cy * sy - h / 2f).toInt()
            sp.v.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
            )
            sp.v.layout(x, y, x + w, y + h)
        }
    }
}

/** Boton redondo con icono dibujado a mano (sin imagenes). */
class IconButton(ctx: Context, private val kind: Int) : View(ctx) {

    companion object {
        const val LOW = 0; const val HIGH = 1; const val PARK = 2; const val HAZ = 3
        const val SPEED = 4; const val PLUG = 5; const val MENU = 6; const val CODE = 7
        const val BURN = 8; const val HORN = 9; const val SPIN = 10; const val CHEV = 11
    }

    var active = false
        set(v) { field = v; invalidate() }
    var connected = false
        set(v) { field = v; invalidate() }
    var label = "S"
        set(v) { field = v; invalidate() }
    var labelColor = Color.parseColor("#F28B82")
        set(v) { field = v; invalidate() }
    var chevUp = true
        set(v) { field = v; invalidate() }

    var onDown: (() -> Unit)? = null
    var onUp: (() -> Unit)? = null

    private var pressedNow = false

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ln = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedNow = true
                invalidate()
                onDown?.invoke()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pressedNow) {
                    pressedNow = false
                    invalidate()
                    onUp?.invoke()
                }
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f

        bg.color = when {
            pressedNow -> Color.parseColor("#0F4A3E")
            active -> Color.parseColor("#0B5A48")
            else -> Color.parseColor("#041F1F")
        }

        if (kind == CHEV) {
            c.drawRoundRect(RectF(0f, 0f, w, h * 2f), h * 0.9f, h * 0.9f, bg)
            ln.strokeWidth = h * 0.1f
            val d = h * 0.18f
            val y0 = h * 0.5f
            val p = Path()
            if (chevUp) {
                p.moveTo(cx - d * 1.6f, y0 + d * 0.6f); p.lineTo(cx, y0 - d * 0.8f); p.lineTo(cx + d * 1.6f, y0 + d * 0.6f)
            } else {
                p.moveTo(cx - d * 1.6f, y0 - d * 0.6f); p.lineTo(cx, y0 + d * 0.8f); p.lineTo(cx + d * 1.6f, y0 - d * 0.6f)
            }
            c.drawPath(p, ln)
            return
        }

        if (kind == SPEED) {
            c.drawRoundRect(RectF(0f, 0f, w, h), h * 0.3f, h * 0.3f, bg)
            val ccx = h * 0.55f
            val rr = h * 0.2f
            c.drawCircle(ccx, cy, rr, fl)
            // flechas ⇄ en el color del fondo
            val saved = ln.color
            ln.color = Color.parseColor("#041F1F")
            ln.strokeWidth = rr * 0.22f
            c.drawLine(ccx - rr * 0.55f, cy - rr * 0.25f, ccx + rr * 0.55f, cy - rr * 0.25f, ln)
            c.drawLine(ccx + rr * 0.55f, cy - rr * 0.25f, ccx + rr * 0.2f, cy - rr * 0.55f, ln)
            c.drawLine(ccx + rr * 0.55f, cy + rr * 0.25f, ccx - rr * 0.55f, cy + rr * 0.25f, ln)
            c.drawLine(ccx - rr * 0.55f, cy + rr * 0.25f, ccx - rr * 0.2f, cy + rr * 0.55f, ln)
            ln.color = saved
            val saveTx = tx.color
            tx.color = labelColor
            tx.textSize = h * 0.36f
            c.drawText(label, w * 0.68f, cy + h * 0.13f, tx)
            tx.color = saveTx
            return
        }

        c.drawCircle(cx, cy, r, bg)
        val u = r
        ln.strokeWidth = u * 0.09f
        fun X(v: Float) = cx + v * u
        fun Y(v: Float) = cy + v * u

        when (kind) {
            LOW -> {
                val p = Path()
                p.moveTo(X(-0.55f), Y(-0.4f)); p.lineTo(X(0.05f), Y(-0.4f))
                p.quadTo(X(0.6f), Y(-0.4f), X(0.6f), Y(0.02f))
                p.quadTo(X(0.6f), Y(0.4f), X(0.1f), Y(0.4f))
                p.lineTo(X(-0.55f), Y(0.4f)); p.close()
                c.drawPath(p, ln)
                c.drawLine(X(-0.4f), Y(0f), X(0.15f), Y(0f), ln)
            }
            HIGH -> {
                val p = Path()
                p.moveTo(X(0f), Y(-0.4f))
                p.quadTo(X(0.62f), Y(-0.4f), X(0.62f), Y(0f))
                p.quadTo(X(0.62f), Y(0.4f), X(0f), Y(0.4f)); p.close()
                c.drawPath(p, fl)
                c.drawLine(X(-0.6f), Y(-0.3f), X(-0.2f), Y(-0.3f), ln)
                c.drawLine(X(-0.5f), Y(0f), X(-0.2f), Y(0f), ln)
                c.drawLine(X(-0.6f), Y(0.3f), X(-0.2f), Y(0.3f), ln)
            }
            PARK -> {
                tx.textSize = u * 1.15f
                c.drawText("P", cx, cy + u * 0.4f, tx)
            }
            HAZ -> {
                val p = Path()
                p.moveTo(X(0f), Y(-0.55f)); p.lineTo(X(0.6f), Y(0.45f)); p.lineTo(X(-0.6f), Y(0.45f)); p.close()
                ln.style = Paint.Style.FILL_AND_STROKE
                ln.strokeWidth = u * 0.14f
                c.drawPath(p, ln)
                ln.style = Paint.Style.STROKE
                val saved = ln.color
                ln.color = bg.color
                ln.strokeWidth = u * 0.1f
                c.drawLine(X(0f), Y(-0.15f), X(0f), Y(0.1f), ln)
                c.drawPoint(X(0f), Y(0.27f), ln)
                ln.color = saved
            }
            PLUG -> {
                c.save()
                c.rotate(-45f, cx, cy)
                c.drawRoundRect(RectF(X(-0.28f), Y(-0.5f), X(0.28f), Y(0f)), u * 0.1f, u * 0.1f, fl)
                c.drawLine(X(-0.12f), Y(-0.5f), X(-0.12f), Y(-0.75f), ln)
                c.drawLine(X(0.12f), Y(-0.5f), X(0.12f), Y(-0.75f), ln)
                c.drawLine(X(0f), Y(0f), X(0f), Y(0.6f), ln)
                c.restore()
                val bx = X(0.45f); val by = Y(0.4f)
                bg.color = if (connected) Color.parseColor("#4CAF7A") else Color.parseColor("#F0645A")
                c.drawCircle(bx, by, u * 0.28f, bg)
                val saved = ln.color
                ln.color = Color.WHITE
                ln.strokeWidth = u * 0.08f
                if (connected) {
                    c.drawLine(bx - u * 0.12f, by, bx - u * 0.03f, by + u * 0.09f, ln)
                    c.drawLine(bx - u * 0.03f, by + u * 0.09f, bx + u * 0.13f, by - u * 0.1f, ln)
                } else {
                    c.drawLine(bx - u * 0.1f, by - u * 0.1f, bx + u * 0.1f, by + u * 0.1f, ln)
                    c.drawLine(bx - u * 0.1f, by + u * 0.1f, bx + u * 0.1f, by - u * 0.1f, ln)
                }
                ln.color = saved
            }
            MENU -> {
                ln.strokeWidth = u * 0.1f
                c.drawLine(X(-0.45f), Y(-0.3f), X(0.45f), Y(-0.3f), ln)
                c.drawLine(X(-0.45f), Y(0f), X(0.3f), Y(0f), ln)
                c.drawLine(X(-0.45f), Y(0.3f), X(0.1f), Y(0.3f), ln)
            }
            CODE -> {
                ln.strokeWidth = u * 0.1f
                val p1 = Path()
                p1.moveTo(X(-0.2f), Y(-0.32f)); p1.lineTo(X(-0.55f), Y(0f)); p1.lineTo(X(-0.2f), Y(0.32f))
                c.drawPath(p1, ln)
                val p2 = Path()
                p2.moveTo(X(0.2f), Y(-0.32f)); p2.lineTo(X(0.55f), Y(0f)); p2.lineTo(X(0.2f), Y(0.32f))
                c.drawPath(p2, ln)
                c.drawLine(X(0.1f), Y(-0.4f), X(-0.1f), Y(0.4f), ln)
            }
            HORN -> {
                val p = Path()
                p.moveTo(X(-0.5f), Y(-0.12f)); p.lineTo(X(-0.2f), Y(-0.12f)); p.lineTo(X(0.3f), Y(-0.42f))
                p.lineTo(X(0.3f), Y(0.42f)); p.lineTo(X(-0.2f), Y(0.12f)); p.lineTo(X(-0.5f), Y(0.12f)); p.close()
                c.drawPath(p, fl)
                ln.strokeWidth = u * 0.07f
                c.drawLine(X(0.45f), Y(-0.2f), X(0.62f), Y(-0.3f), ln)
                c.drawLine(X(0.47f), Y(0f), X(0.68f), Y(0f), ln)
                c.drawLine(X(0.45f), Y(0.2f), X(0.62f), Y(0.3f), ln)
            }
            SPIN -> {
                ln.strokeWidth = u * 0.08f
                val oval = RectF(X(-0.6f), Y(-0.28f), X(0.6f), Y(0.28f))
                c.save(); c.rotate(-30f, cx, cy); c.drawOval(oval, ln); c.restore()
                c.save(); c.rotate(30f, cx, cy); c.drawOval(oval, ln); c.restore()
                c.drawCircle(cx, cy, u * 0.1f, fl)
            }
            BURN -> {
                ln.strokeWidth = u * 0.08f
                c.drawCircle(X(0.12f), Y(0.02f), u * 0.36f, ln)
                c.drawCircle(X(0.12f), Y(0.02f), u * 0.12f, ln)
                c.drawLine(X(0.12f), Y(-0.34f), X(0.12f), Y(-0.1f), ln)
                c.drawLine(X(0.12f), Y(0.14f), X(0.12f), Y(0.38f), ln)
                c.drawLine(X(-0.24f), Y(0.02f), X(0f), Y(0.02f), ln)
                c.drawLine(X(0.24f), Y(0.02f), X(0.48f), Y(0.02f), ln)
                c.drawLine(X(-0.65f), Y(-0.35f), X(-0.4f), Y(-0.1f), ln)
                c.drawLine(X(-0.65f), Y(-0.05f), X(-0.45f), Y(0.1f), ln)
                c.drawLine(X(-0.65f), Y(0.5f), X(0.65f), Y(0.5f), ln)
            }
        }
    }
}

/** Mitad de cruceta: circulo con degradado verde y dos flechas (vertical u horizontal). */
class PadView(ctx: Context, private val vertical: Boolean) : View(ctx) {

    /** Vertical: +1 arriba / -1 abajo. Horizontal: -1 izquierda / +1 derecha. 0 = suelto. */
    var onValue: ((Int) -> Unit)? = null

    private var value = 0
    private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(34, 255, 255, 255) }
    private val ch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val div = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f

        circle.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(
                Color.rgb(0, 0, 0), Color.rgb(3, 16, 13), Color.rgb(28, 111, 85),
                Color.rgb(46, 156, 120), Color.argb(0, 46, 156, 120)
            ),
            floatArrayOf(0f, 0.5f, 0.88f, 0.97f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, circle)

        if (value != 0) {
            c.save()
            if (vertical) {
                if (value > 0) c.clipRect(0f, 0f, w, cy) else c.clipRect(0f, cy, w, h)
            } else {
                if (value < 0) c.clipRect(0f, 0f, cx, h) else c.clipRect(cx, 0f, w, h)
            }
            c.drawCircle(cx, cy, r * 0.97f, glow)
            c.restore()
        }

        if (vertical) c.drawRect(cx - r * 0.75f, cy - 1.5f, cx + r * 0.75f, cy + 1.5f, div)
        else c.drawRect(cx - 1.5f, cy - r * 0.75f, cx + 1.5f, cy + r * 0.75f, div)

        ch.strokeWidth = r * 0.075f
        val s = r * 0.17f
        val d = r * 0.42f
        if (vertical) {
            ch.color = if (value > 0) Color.parseColor("#B8FFE3") else Color.WHITE
            chev(c, cx, cy - d, 0, s)
            ch.color = if (value < 0) Color.parseColor("#B8FFE3") else Color.WHITE
            chev(c, cx, cy + d, 1, s)
        } else {
            ch.color = if (value < 0) Color.parseColor("#B8FFE3") else Color.WHITE
            chev(c, cx - d, cy, 2, s)
            ch.color = if (value > 0) Color.parseColor("#B8FFE3") else Color.WHITE
            chev(c, cx + d, cy, 3, s)
        }
    }

    /** dir: 0 arriba, 1 abajo, 2 izquierda, 3 derecha */
    private fun chev(c: Canvas, x: Float, y: Float, dir: Int, s: Float) {
        val p = Path()
        when (dir) {
            0 -> { p.moveTo(x - s * 1.4f, y + s * 0.6f); p.lineTo(x, y - s * 0.8f); p.lineTo(x + s * 1.4f, y + s * 0.6f) }
            1 -> { p.moveTo(x - s * 1.4f, y - s * 0.6f); p.lineTo(x, y + s * 0.8f); p.lineTo(x + s * 1.4f, y - s * 0.6f) }
            2 -> { p.moveTo(x + s * 0.6f, y - s * 1.4f); p.lineTo(x - s * 0.8f, y); p.lineTo(x + s * 0.6f, y + s * 1.4f) }
            else -> { p.moveTo(x - s * 0.6f, y - s * 1.4f); p.lineTo(x + s * 0.8f, y); p.lineTo(x - s * 0.6f, y + s * 1.4f) }
        }
        c.drawPath(p, ch)
    }

    private fun setValue(v: Int) {
        if (v != value) {
            value = v
            invalidate()
            onValue?.invoke(v)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val cx = width / 2f
                val cy = height / 2f
                val r = min(width, height) / 2f
                val dx = e.x - cx
                val dy = e.y - cy
                var v = 0
                if (hypot(dx, dy) <= r * 1.15f) {
                    v = if (vertical) {
                        if (abs(dy) < r * 0.06f) 0 else if (dy < 0) 1 else -1
                    } else {
                        if (abs(dx) < r * 0.06f) 0 else if (dx < 0) -1 else 1
                    }
                }
                setValue(v)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setValue(0)
        }
        return true
    }
}
