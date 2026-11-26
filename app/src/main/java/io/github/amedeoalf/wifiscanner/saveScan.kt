package io.github.amedeoalf.wifiscanner

import androidx.compose.ui.geometry.Offset
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun saveScan(dumpDir: File, position: Offset, networks: List<ScanResult>): String {
    val timestamp = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    dumpDir.mkdirs()
    val fname = "$timestamp.json"
    val file = dumpDir.resolve(fname)
    val json = JSONObject(mapOf(
        "location" to JSONArray(listOf(position.x, position.y)),
        "networks" to networks.toJSON()
    ))
    file.writeText(json.toString())
    return fname
}