package com.example.carrobt

import kotlin.math.abs
import kotlin.math.sin

class Pt(
    val t: Float, val x: Float, val y: Float, val z: Float,
    val yaw: Float, val roll: Float, val pitch: Float
)

/** Datos del mapeo en memoria (los comparten MainActivity y MapActivity). */
object MapData {
    val pts = ArrayList<Pt>()
    var armed = false      // mapeo pedido
    var started = false    // el carro ya empezo a moverse (punto 0)
    var pathLen = 0f       // recorrido total en cm (suma de |avance|, cuenta tambien la reversa)
    var yaw = 0f
    var roll = 0f
    var pitch = 0f
    private var lastDist = 0f
    private var z = 0f

    fun clear() {
        pts.clear()
        armed = false
        started = false
        pathLen = 0f
        yaw = 0f; roll = 0f; pitch = 0f
        lastDist = 0f
        z = 0f
    }

    fun begin() {
        pts.clear()
        pathLen = 0f; lastDist = 0f; z = 0f
        yaw = 0f; roll = 0f; pitch = 0f
        started = true
        pts.add(Pt(0f, 0f, 0f, 0f, 0f, 0f, 0f))
    }

    /** Linea D,t,dist,x,y,yaw,roll,pitch. Z se estima con el pitch: z += avance * sin(pitch). */
    fun add(t: Float, dist: Float, x: Float, y: Float, yawD: Float, rollD: Float, pitchD: Float) {
        val dd = dist - lastDist
        lastDist = dist
        pathLen += abs(dd)
        z += dd * sin(Math.toRadians(pitchD.toDouble())).toFloat()
        yaw = yawD; roll = rollD; pitch = pitchD
        pts.add(Pt(t, x, y, z, yawD, rollD, pitchD))
    }
}
