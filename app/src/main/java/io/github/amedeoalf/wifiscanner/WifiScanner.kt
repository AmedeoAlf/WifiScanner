package io.github.amedeoalf.wifiscanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.app.ActivityCompat
import org.json.JSONArray
import org.json.JSONObject

class WifiScanner(private val ctx: Context) {
    val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager

    init {
        if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.CHANGE_WIFI_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw Exception("Change wifi state not granted")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                val success = p1?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                if (!success) throw Exception("error in receiving networks")
            }
        }
        ctx.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            }
        )
        if (!wifiManager.startScan()) throw Exception("Error in startScan()")
    }

    @SuppressLint("MissingPermission")
    fun getNetworks(): List<ScanResult> = wifiManager.scanResults.map { it.convert(wifiManager) }
}

fun android.net.wifi.ScanResult.convert(wifiManager: WifiManager) = ScanResult(
    ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        wifiSsid?.bytes?.decodeToString() ?: "" else SSID,
    absoluteLevel = wifiManager.calculateSignalLevel(level),
    freq = frequency,
    estimatedDistance = routerDistance(this),
    bssid = BSSID
)

data class ScanResult(
    val ssid: String,
    val absoluteLevel: Int,
    val freq: Int,
    val estimatedDistance: Double,
    val bssid: String
)

fun List<ScanResult>.toJSON() = JSONObject().also { res ->
    forEach {
        if (!res.has(it.ssid)) res.put(it.ssid, JSONObject())
        val ssidObj = res.getJSONObject(it.ssid)
        ssidObj.put(it.bssid, JSONObject().apply {
            put("mhz", it.freq)
            put("dbM", it.absoluteLevel)
        })
    }
}
