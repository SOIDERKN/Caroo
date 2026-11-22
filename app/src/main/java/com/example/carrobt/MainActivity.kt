package com.example.carrobt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Control del carro por Bluetooth (HC-05) + mapeo de trayectoria.
 * Recibe del Arduino lineas de texto:
 *   D,t,dist,x,y,yaw,roll,pitch   (mapeo)      MAP,ARMADO / MAP,INICIO / MAP,FIN
 *   objeto a: 23 cm | objeto cerca | Cuidado   (sensor VL53)
 */
@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var out: OutputStream? = null

    private lateinit var info: TextView
    private lateinit var drawer: LinearLayout
    private lateinit var btnPlug: IconButton
    private lateinit var btnChev: IconButton
    private lateinit var btnSpeed: IconButton

    private var fwd = 0
    private var turn = 0
    private var lastCmd = Cmd.STOP
    private var low = false
    private var high = false
    private var park = false
    private var haz = false
    private var speedIdx = 0

    private var objCm = -1f
    private var objMs = 0L
    private var warnMs = 0L
    private var nearMs = 0L
    private var tick = 0
    private val logLines = ArrayList<String>()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) onConnectClicked() else toast("Se necesita el permiso de Bluetooth")
        }

    private val ticker = object : Runnable {
        override fun run() {
            if (out != null && "FBLRGHIJ".indexOf(lastCmd) >= 0) send(lastCmd)   // keep-alive
            tick++
            if (tick % 3 == 0) updateInfo()
            handler.postDelayed(this, 150)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        val dm = resources.displayMetrics
        val s = min(dm.widthPixels, dm.heightPixels) / 704f

        val root = Root(this)
        root.setBackgroundColor(Color.BLACK)

        val btnLow = IconButton(this, IconButton.LOW)
        val btnHigh = IconButton(this, IconButton.HIGH)
        val btnPark = IconButton(this, IconButton.PARK)
        val btnHaz = IconButton(this, IconButton.HAZ)
        btnSpeed = IconButton(this, IconButton.SPEED)
        btnPlug = IconButton(this, IconButton.PLUG)
        val btnMenu = IconButton(this, IconButton.MENU)
        val btnCode = IconButton(this, IconButton.CODE)
        val btnBurn = IconButton(this, IconButton.BURN)
        val btnHorn = IconButton(this, IconButton.HORN)
        val btnSpin = IconButton(this, IconButton.SPIN)
        btnChev = IconButton(this, IconButton.CHEV)
        val padL = PadView(this, true)
        val padR = PadView(this, false)

        // Luces
        btnLow.onDown = { low = !low; btnLow.active = low; send(if (low) Cmd.LOW_ON else Cmd.LOW_OFF) }
        btnHigh.onDown = { high = !high; btnHigh.active = high; send(if (high) Cmd.HIGH_ON else Cmd.HIGH_OFF) }
        btnPark.onDown = { park = !park; btnPark.active = park; send(if (park) Cmd.PARK_ON else Cmd.PARK_OFF) }
        btnHaz.onDown = { haz = !haz; btnHaz.active = haz; send(if (haz) Cmd.HAZ_ON else Cmd.HAZ_OFF) }

        // Velocidad S / M / F
        btnSpeed.onDown = {
            speedIdx = (speedIdx + 1) % 3
            btnSpeed.label = Cmd.SPEED_LABELS[speedIdx]
            btnSpeed.labelColor = when (speedIdx) {
                0 -> Color.parseColor("#F28B82")
                1 -> Color.parseColor("#FDD663")
                else -> Color.parseColor("#81C995")
            }
            send(Cmd.SPEEDS[speedIdx])
        }

        // Claxon, derrape, giro (mantener presionado)
        btnHorn.onDown = { send(Cmd.HORN_ON) }
        btnHorn.onUp = { send(Cmd.HORN_OFF) }
        btnBurn.onDown = { lastCmd = Cmd.BURN; send(Cmd.BURN) }
        btnBurn.onUp = { lastCmd = Cmd.STOP; send(Cmd.STOP) }
        btnSpin.onDown = { lastCmd = Cmd.SPIN; send(Cmd.SPIN) }
        btnSpin.onUp = { lastCmd = Cmd.STOP; send(Cmd.STOP) }

        // Conexion, menu, datos crudos, panel de mapeo
        btnPlug.onDown = { onConnectClicked() }
        btnMenu.onDown = { showMenu() }
        btnCode.onDown = { showLog() }
        btnChev.onDown = {
            val show = drawer.visibility != View.VISIBLE
            drawer.visibility = if (show) View.VISIBLE else View.GONE
            btnChev.chevUp = !show
        }
        btnChev.chevUp = false   // el panel arranca abierto

        // Cruceta
        padL.onValue = { v -> fwd = v; updateDirection() }
        padR.onValue = { v -> turn = v; updateDirection() }

        // Panel de datos
        info = TextView(this)
        info.setTextColor(Color.WHITE)
        info.setTextSize(TypedValue.COMPLEX_UNIT_PX, 21f * s)
        info.gravity = Gravity.CENTER_VERTICAL
        info.setPadding((20 * s).toInt(), (8 * s).toInt(), (12 * s).toInt(), (8 * s).toInt())
        val infoBg = GradientDrawable()
        infoBg.cornerRadius = 28f * s
        infoBg.setColor(Color.parseColor("#66041F1F"))
        info.background = infoBg

        // Panel con los 3 botones de mapeo
        drawer = LinearLayout(this)
        drawer.orientation = LinearLayout.HORIZONTAL
        drawer.gravity = Gravity.CENTER
        val bStart = mapButton("Iniciar mapeo", "#0B5A48", s)
        val bStop = mapButton("Detener mapeo", "#7A2E2A", s)
        val bClear = mapButton("Borrar mapeo", "#33403F", s)
        for (b in listOf(bStart, bStop, bClear)) {
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            val m = (5 * s).toInt()
            lp.setMargins(m, m, m, m)
            drawer.addView(b, lp)
        }
        bStart.setOnClickListener { startMapping() }
        bStop.setOnClickListener { stopMapping() }
        bClear.setOnClickListener { clearMapping() }

        root.put(btnLow, 87f, 80f, 98f, 98f)
        root.put(btnHigh, 205f, 80f, 98f, 98f)
        root.put(btnPark, 322f, 80f, 98f, 98f)
        root.put(btnHaz, 440f, 80f, 98f, 98f)
        root.put(btnSpeed, 580f, 80f, 146f, 98f)
        root.put(btnPlug, 1362f, 80f, 98f, 98f)
        root.put(btnMenu, 1480f, 80f, 98f, 98f)
        root.put(btnCode, 1480f, 197f, 98f, 98f)
        root.put(btnBurn, 518f, 225f, 98f, 98f)
        root.put(btnHorn, 1050f, 225f, 98f, 98f)
        root.put(btnSpin, 971f, 361f, 98f, 98f)
        root.put(padL, 283f, 459f, 372f, 372f)
        root.put(padR, 1284f, 459f, 372f, 372f)
        root.put(info, 700f, 470f, 420f, 170f)
        root.put(drawer, 784f, 610f, 620f, 96f)
        root.put(btnChev, 784f, 682f, 110f, 44f)
        setContentView(root)

        setConnected(false)
        updateInfo()
    }

    private fun mapButton(text: String, color: String, s: Float): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17f * s)
        b.setPadding(0, 0, 0, 0)
        val g = GradientDrawable()
        g.cornerRadius = 30f * s
        g.setColor(Color.parseColor(color))
        b.background = g
        return b
    }

    // ---------------- Movimiento ----------------

    private fun updateDirection() {
        val c = when {
            fwd > 0 && turn < 0 -> Cmd.FL
            fwd > 0 && turn > 0 -> Cmd.FR
            fwd < 0 && turn < 0 -> Cmd.BL
            fwd < 0 && turn > 0 -> Cmd.BR
            fwd > 0 -> Cmd.FWD
            fwd < 0 -> Cmd.BACK
            turn < 0 -> Cmd.LEFT
            turn > 0 -> Cmd.RIGHT
            else -> Cmd.STOP
        }
        if (c != lastCmd) {
            lastCmd = c
            send(c)
        }
    }

    // ---------------- Mapeo ----------------

    private fun startMapping() {
        if (out == null) { toast("Conecta el Bluetooth primero"); return }
        MapData.clear()
        MapData.armed = true
        send(Cmd.MAP_START)
        updateInfo()
        toast("Mapeo armado: avanza para comenzar")
    }

    private fun stopMapping() {
        if (MapData.armed) send(Cmd.MAP_STOP)
        finishMapping()
    }

    private fun clearMapping() {
        if (MapData.armed) send(Cmd.MAP_STOP)
        MapData.clear()
        updateInfo()
        toast("Mapeo borrado")
    }

    private fun finishMapping() {
        if (!MapData.armed) return
        MapData.armed = false
        updateInfo()
        if (MapData.pts.size >= 2) {
            startActivity(Intent(this, MapActivity::class.java))
        } else {
            toast("No hay suficientes datos del mapeo")
        }
    }

    private fun mapState(): String = when {
        MapData.armed && !MapData.started -> "armado, esperando movimiento"
        MapData.armed -> "grabando (${MapData.pts.size} pts)"
        MapData.pts.size > 1 -> "terminado (${MapData.pts.size} pts)"
        else -> "inactivo"
    }

    // ---------------- Datos entrantes ----------------

    private fun startReader(s: BluetoothSocket) {
        val t = Thread {
            try {
                val ins = s.inputStream
                val sb = StringBuilder()
                val buf = ByteArray(256)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    for (i in 0 until n) {
                        val ch = (buf[i].toInt() and 0xFF).toChar()
                        if (ch == '\n') {
                            val line = sb.toString().trim()
                            sb.setLength(0)
                            if (line.isNotEmpty()) runOnUiThread { onLine(line) }
                        } else if (ch != '\r' && sb.length < 200) {
                            sb.append(ch)
                        }
                    }
                }
            } catch (_: IOException) {
            }
            runOnUiThread { if (socket === s) disconnect("Conexion perdida") }
        }
        t.isDaemon = true
        t.start()
    }

    private fun onLine(line: String) {
        logLines.add(line)
        if (logLines.size > 40) logLines.removeAt(0)

        if (line.startsWith("D,")) {
            parseD(line)
            updateInfo()
            return
        }
        if (line.startsWith("MAP,")) {
            when (line.substring(4).trim()) {
                "ARMADO" -> { MapData.clear(); MapData.armed = true }
                "INICIO" -> {
                    MapData.armed = true
                    MapData.begin()
                }
                "FIN" -> finishMapping()
            }
            updateInfo()
            return
        }

        // Sensor de distancia (VL53)
        val low = line.lowercase(Locale.ROOT)
        val now = SystemClock.uptimeMillis()
        if (low.startsWith("cuidado")) warnMs = now
        if (low.startsWith("objeto cerca")) nearMs = now
        if (low.startsWith("objeto a") || low.startsWith("vl")) {
            val body = line.substringAfter(':', line.substringAfter(',', line))
            val m = Regex("-?\\d+(?:[.,]\\d+)?").find(body)
            val v = m?.value?.replace(',', '.')?.toFloatOrNull()
            if (v != null) {
                // "VL,<mm>" se interpreta en mm; "objeto a: N cm" en cm (o mm si lo dice)
                val mm = low.startsWith("vl") || low.contains("mm")
                objCm = if (mm) v / 10f else v
                objMs = now
            }
        }
        updateInfo()
    }

    private fun parseD(line: String) {
        val f = line.split(',')
        if (f.size < 8) return
        val v = FloatArray(7)
        for (i in 0 until 7) {
            v[i] = f[i + 1].trim().toFloatOrNull() ?: return
        }
        MapData.armed = true
        if (!MapData.started) MapData.begin()
        MapData.add(v[0], v[1], v[2], v[3], v[4], v[5], v[6])
    }

    private fun updateInfo() {
        val now = SystemClock.uptimeMillis()
        val sb = SpannableStringBuilder()

        val fresh = objCm >= 0f && now - objMs < 1500
        val l1 = if (fresh) String.format(Locale.US, "Objeto a: %.1f cm", objCm) else "Sin datos del sensor"
        val alert = fresh && (now - nearMs < 1500 || now - warnMs < 1500)
        val s1 = sb.length
        sb.append(if (alert) "$l1  \u00A1CERCA!" else l1)
        if (alert) {
            sb.setSpan(ForegroundColorSpan(Color.parseColor("#F28B82")), s1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        sb.append('\n')
        sb.append(String.format(Locale.US, "Recorrido: %.2f m", MapData.pathLen / 100f))
        sb.append('\n')
        sb.append(String.format(Locale.US, "Orientacion: %.1f\u00B0", MapData.yaw))
        sb.append('\n')
        sb.append("Mapeo: ").append(mapState())
        info.text = sb
    }

    // ---------------- Bluetooth ----------------

    private fun onConnectClicked() {
        if (out != null) { disconnect(); return }

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null) { toast("Este telefono no tiene Bluetooth"); return }
        if (!adapter.isEnabled) { toast("Activa el Bluetooth primero"); return }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }

        val devices = adapter.bondedDevices.toList()
        if (devices.isEmpty()) {
            toast("Primero empareja el HC-05 en Ajustes > Bluetooth (PIN 1234 o 0000)")
            return
        }
        val names = devices.map { (it.name ?: "Sin nombre") + "\n" + it.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Elige tu HC-05")
            .setItems(names) { _, i -> connect(devices[i]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun connect(device: BluetoothDevice) {
        toast("Conectando a " + (device.name ?: device.address) + "...")
        io.execute {
            try {
                val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
                adapter.cancelDiscovery()
                val s = device.createRfcommSocketToServiceRecord(sppUuid)
                s.connect()
                socket = s
                out = s.outputStream
                runOnUiThread {
                    setConnected(true)
                    toast("Conectado")
                }
                startReader(s)
            } catch (e: IOException) {
                runOnUiThread { toast("No se pudo conectar") }
            }
        }
    }

    private fun send(c: Char) {
        io.execute {
            try {
                out?.write(c.code)
                out?.flush()
            } catch (e: IOException) {
                runOnUiThread { disconnect("Conexion perdida") }
            }
        }
    }

    private fun disconnect(msg: String = "Desconectado") {
        try { out?.write(Cmd.STOP.code) } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        out = null
        setConnected(false)
        toast(msg)
    }

    private fun setConnected(c: Boolean) {
        btnPlug.connected = c
    }

    // ---------------- Menu y datos crudos ----------------

    private fun showMenu() {
        val items = arrayOf("Ver ultimo mapa", if (out != null) "Desconectar" else "Conectar")
        AlertDialog.Builder(this)
            .setItems(items) { _, i ->
                if (i == 0) {
                    if (MapData.pts.size >= 2) startActivity(Intent(this, MapActivity::class.java))
                    else toast("Aun no hay un mapeo")
                } else onConnectClicked()
            }
            .show()
    }

    private fun showLog() {
        val msg = if (logLines.isEmpty()) "(sin datos recibidos)" else logLines.takeLast(15).joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle("Ultimas lineas recibidas")
            .setMessage(msg)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    // ---------------- Ciclo de vida ----------------

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        fwd = 0; turn = 0; lastCmd = Cmd.STOP
        if (out != null) send(Cmd.STOP)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { socket?.close() } catch (_: IOException) {}
        io.shutdown()
    }
}
