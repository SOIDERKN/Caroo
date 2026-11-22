package com.example.carrobt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Dibuja el recorrido (vistas XY, XZ, YZ + datos) en un solo PNG. */
object PlotRenderer {

    private const val W = 1800
    private const val H = 1290
    private val DARK = Color.parseColor("#1B1B1B")

    fun render(pts: List<Pt>): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        val n = pts.size
        val xs = FloatArray(n) { pts[it].x }
        val ys = FloatArray(n) { pts[it].y }
        val zs = FloatArray(n) { pts[it].z }

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DARK; textSize = 54f; typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText("Recorrido del carro (IMU + encoder)", 40f, 80f, title)

        val last = pts[n - 1]
        drawPlot(c, RectF(40f, 110f, 1100f, 1040f), xs, ys, "Vista superior XY", "X (cm)", "Y (cm)", true, last.yaw)
        drawPlot(c, RectF(1130f, 110f, 1760f, 560f), xs, zs, "Vista lateral XZ", "X (cm)", "Z (cm)", false, null)
        drawPlot(c, RectF(1130f, 590f, 1760f, 1040f), ys, zs, "Vista frontal YZ", "Y (cm)", "Z (cm)", false, null)

        // Datos
        val total = MapData.pathLen
        val straight = sqrt(last.x * last.x + last.y * last.y + last.z * last.z)
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F4"); style = Paint.Style.FILL
        }
        c.drawRoundRect(RectF(40f, 1060f, 1760f, 1265f), 24f, 24f, box)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DARK; textSize = 38f; typeface = Typeface.DEFAULT_BOLD }
        val t2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#444444"); textSize = 30f }
        c.drawText(
            "Distancia recorrida: ${fmt(total)}     En linea recta (inicio-fin): ${fmt(straight)}     Tiempo: ${f1(last.t)} s",
            70f, 1118f, t
        )
        c.drawText(
            "Orientacion final:  Yaw ${f1(last.yaw)}\u00B0    Roll ${f1(last.roll)}\u00B0    Pitch ${f1(last.pitch)}\u00B0",
            70f, 1175f, t
        )
        c.drawText(
            "Posicion final (cm):  X ${f1(last.x)}   Y ${f1(last.y)}   Z ${f1(last.z)}     Puntos: $n     (Z estimada con el pitch)",
            70f, 1230f, t2
        )
        return bmp
    }

    private fun f1(v: Float) = String.format(Locale.US, "%.1f", v)

    private fun fmt(cm: Float): String =
        if (cm >= 100f) String.format(Locale.US, "%.2f m", cm / 100f)
        else String.format(Locale.US, "%.1f cm", cm)

    private fun niceStep(range: Float): Float {
        val raw = range / 6f
        val p = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
        val m = raw / p
        val k = when {
            m < 1.5f -> 1f
            m < 3.5f -> 2f
            m < 7.5f -> 5f
            else -> 10f
        }
        return k * p
    }

    private fun drawPlot(
        c: Canvas, box: RectF, hs: FloatArray, vs: FloatArray,
        title: String, hLabel: String, vLabel: String, equal: Boolean, yawEnd: Float?
    ) {
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.parseColor("#999999"); strokeWidth = 2f
        }
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DARK; textSize = 24f }
        val tt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DARK; textSize = 32f; typeface = Typeface.DEFAULT_BOLD
        }
        val grid = Paint().apply { color = Color.parseColor("#DDDDDD"); strokeWidth = 1.5f }
        val axis = Paint().apply { color = Color.parseColor("#777777"); strokeWidth = 2.5f }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.parseColor("#1565C0")
            strokeWidth = 4f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }

        c.drawRect(box, frame)
        c.drawText(title, box.left + 16f, box.top + 40f, tt)

        val ix = box.left + 110f
        val iy = box.top + 60f
        val iw = box.width() - 110f - 30f
        val ih = box.height() - 60f - 80f

        var h0 = hs.min(); var h1 = hs.max()
        var v0 = vs.min(); var v1 = vs.max()
        val minSpan = if (equal) 20f else 10f
        if (h1 - h0 < minSpan) { val m = (h0 + h1) / 2f; h0 = m - minSpan / 2f; h1 = m + minSpan / 2f }
        if (v1 - v0 < minSpan) { val m = (v0 + v1) / 2f; v0 = m - minSpan / 2f; v1 = m + minSpan / 2f }
        val ph = (h1 - h0) * 0.1f
        val pv = (v1 - v0) * 0.1f
        h0 -= ph; h1 += ph; v0 -= pv; v1 += pv

        if (equal) {
            val sc = min(iw / (h1 - h0), ih / (v1 - v0))
            val spanH = iw / sc
            val spanV = ih / sc
            val mh = (h0 + h1) / 2f
            val mv = (v0 + v1) / 2f
            h0 = mh - spanH / 2f; h1 = mh + spanH / 2f
            v0 = mv - spanV / 2f; v1 = mv + spanV / 2f
        }

        fun px(h: Float) = ix + (h - h0) / (h1 - h0) * iw
        fun py(v: Float) = iy + ih - (v - v0) / (v1 - v0) * ih

        val stepH = niceStep(h1 - h0)
        val stepV = niceStep(v1 - v0)
        val dec = { s: Float -> if (s >= 1f) "%.0f" else "%.1f" }

        c.save()
        c.clipRect(ix, iy, ix + iw, iy + ih)
        var t = ceil(h0 / stepH) * stepH
        while (t <= h1) {
            c.drawLine(px(t), iy, px(t), iy + ih, if (t == 0f) axis else grid)
            t += stepH
        }
        t = ceil(v0 / stepV) * stepV
        while (t <= v1) {
            c.drawLine(ix, py(t), ix + iw, py(t), if (t == 0f) axis else grid)
            t += stepV
        }
        val path = Path()
        for (i in hs.indices) {
            if (i == 0) path.moveTo(px(hs[i]), py(vs[i])) else path.lineTo(px(hs[i]), py(vs[i]))
        }
        c.drawPath(path, line)
        c.restore()

        // Etiquetas de los ejes
        txt.textAlign = Paint.Align.CENTER
        t = ceil(h0 / stepH) * stepH
        while (t <= h1) {
            c.drawText(String.format(Locale.US, dec(stepH), t), px(t), iy + ih + 30f, txt)
            t += stepH
        }
        txt.textAlign = Paint.Align.RIGHT
        t = ceil(v0 / stepV) * stepV
        while (t <= v1) {
            c.drawText(String.format(Locale.US, dec(stepV), t), ix - 10f, py(t) + 8f, txt)
            t += stepV
        }
        txt.textAlign = Paint.Align.CENTER
        c.drawText(hLabel, ix + iw / 2f, box.bottom - 14f, txt)
        c.save()
        c.rotate(-90f, box.left + 26f, iy + ih / 2f)
        c.drawText(vLabel, box.left + 26f, iy + ih / 2f, txt)
        c.restore()

        // Inicio y fin
        val sx = px(hs[0]); val sy = py(vs[0])
        val ex = px(hs[hs.size - 1]); val ey = py(vs[vs.size - 1])
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        dot.color = Color.parseColor("#2E7D32")
        c.drawCircle(sx, sy, 11f, dot)
        dot.color = Color.parseColor("#C62828")
        c.drawCircle(ex, ey, 11f, dot)
        txt.textAlign = Paint.Align.LEFT
        txt.color = Color.parseColor("#2E7D32")
        c.drawText("Inicio", sx + 16f, sy - 12f, txt)
        txt.color = Color.parseColor("#C62828")
        c.drawText("Fin", ex + 16f, ey - 12f, txt)
        txt.color = DARK

        // Flecha con la orientacion final (solo en la vista superior)
        if (yawEnd != null) {
            val a = Math.toRadians(yawEnd.toDouble())
            val dx = cos(a).toFloat() * 70f
            val dy = sin(a).toFloat() * 70f   // en pantalla Y crece hacia abajo: heading = (cos, -sin) en plano
            val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; color = Color.parseColor("#C62828")
                strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
            }
            val tx = ex + dx; val ty = ey + dy
            c.drawLine(ex, ey, tx, ty, arrow)
            val back = a + Math.PI
            for (off in doubleArrayOf(0.5, -0.5)) {
                val hx = tx + cos(back + off).toFloat() * 24f
                val hy = ty + sin(back + off).toFloat() * 24f
                c.drawLine(tx, ty, hx, hy, arrow)
            }
        }
    }
}
