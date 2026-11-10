package io.github.amedeoalf.wifiscanner

import android.net.wifi.ScanResult
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

// https://stackoverflow.com/questions/11217674/how-to-calculate-distance-from-wifi-router-using-signal-strength
fun routerDistance(result: ScanResult) =
    10.0.pow((27.55 - (20 * log10(result.frequency.toDouble())) + abs(result.level)) / 20.0)

fun Double.toFixed(digitsAfterComma: Int = 2) = "%.${digitsAfterComma}f".format(this)