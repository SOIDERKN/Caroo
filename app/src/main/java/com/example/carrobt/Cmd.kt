package com.example.carrobt

/**
 * Comandos enviados al Arduino por Bluetooth.
 * Los de movimiento (F B L R G H I J S) son los mismos del sketch original.
 * Los demas son SUPOSICIONES mias: cambia aqui la letra para que coincida con tu sketch.
 */
object Cmd {
    // Movimiento
    const val FWD = 'F'; const val BACK = 'B'; const val LEFT = 'L'; const val RIGHT = 'R'
    const val FL = 'G'; const val FR = 'H'; const val BL = 'I'; const val BR = 'J'
    const val STOP = 'S'

    // Luces (encender / apagar)
    const val LOW_ON = 'W'; const val LOW_OFF = 'w'      // luz baja (faro)
    const val HIGH_ON = 'U'; const val HIGH_OFF = 'u'    // luz alta
    const val PARK_ON = 'P'; const val PARK_OFF = 'p'    // estacionamiento
    const val HAZ_ON = 'Z'; const val HAZ_OFF = 'z'      // intermitentes de emergencia

    // Claxon: V al presionar, v al soltar
    const val HORN_ON = 'V'; const val HORN_OFF = 'v'

    // Mantener presionado: derrape y giro sobre su eje (al soltar se envia S)
    const val BURN = 'K'
    const val SPIN = 'T'

    // Velocidad: lento / medio / rapido
    val SPEEDS = charArrayOf('4', '7', 'q')
    val SPEED_LABELS = arrayOf("S", "M", "F")

    // Mapeo (los del sketch del mapeo)
    const val MAP_START = 'M'
    const val MAP_STOP = 'N'
}
