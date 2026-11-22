package com.example.carrobt

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ventana con el PNG del recorrido y boton para guardarlo en la galeria. */
class MapActivity : AppCompatActivity() {

    private var bmp: Bitmap? = null

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) save() else toast("Sin permiso para guardar la imagen")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (MapData.pts.size < 2) {
            toast("No hay datos de mapeo")
            finish()
            return
        }
        bmp = PlotRenderer.render(ArrayList(MapData.pts))

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)

        val img = ImageView(this)
        img.setImageBitmap(bmp)
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        img.setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        root.addView(img, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())

        val bSave = makeButton("Guardar foto", "#0B5A48")
        bSave.setOnClickListener { save() }
        val bClose = makeButton("Cerrar", "#33403F")
        bClose.setOnClickListener { finish() }
        val lp = { LinearLayout.LayoutParams(0, (48 * dp).toInt(), 1f).apply { setMargins((6 * dp).toInt(), 0, (6 * dp).toInt(), 0) } }
        row.addView(bSave, lp())
        row.addView(bClose, lp())
        root.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(root)
    }

    private fun makeButton(text: String, color: String): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        val g = GradientDrawable()
        g.cornerRadius = 40f
        g.setColor(Color.parseColor(color))
        b.background = g
        return b
    }

    private fun save() {
        val b = bmp ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        val name = "Recorrido_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues()
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name)
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CarroBT")
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                    ?: throw java.io.IOException("insert")
                val os = contentResolver.openOutputStream(uri) ?: throw java.io.IOException("open")
                os.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "CarroBT"
                )
                dir.mkdirs()
                val f = File(dir, name)
                FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                MediaScannerConnection.scanFile(this, arrayOf(f.absolutePath), null, null)
            }
            toast("Guardada en Imagenes/CarroBT")
        } catch (e: Exception) {
            toast("No se pudo guardar la imagen")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
