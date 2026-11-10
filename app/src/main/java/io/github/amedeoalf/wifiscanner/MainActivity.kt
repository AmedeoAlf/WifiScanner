package io.github.amedeoalf.wifiscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.amedeoalf.wifiscanner.ui.theme.WifiScannerTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.File

class MainActivity : ComponentActivity() {
    lateinit var updatePerms: (() -> Unit)

    val permissionRequestLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updatePerms()
        }

    fun requestPerms() {
        permissionRequestLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var gotPerms by remember {
                mutableStateOf(
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                )
            }

            updatePerms = {
                gotPerms =
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                println("did we get perms: $gotPerms")
            }


            if (!gotPerms) {
                requestPerms()
            }

            var scanner = if (gotPerms) WifiScanner(this) else null

            WifiScannerTheme {
                if (gotPerms) {
                    var result by remember { mutableStateOf(emptyList<ScanResult>()) }
                    val handler = Handler(Looper.getMainLooper())
                    LaunchedEffect(Unit) {
                        val updateResult =
                            object : Runnable {
                                override fun run() {
                                    if (scanner != null) result =
                                        scanner!!.getNetworks().sortedBy { it.estimatedDistance }
                                    handler.postDelayed(this, 500)
                                }
                            }
                        updateResult.run()
                    }
                    Surface() {
                        Scaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding(),
                            bottomBar = {
                                val scrollState = remember { ScrollState(1000) }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .safeContentPadding()
                                        .horizontalScroll(scrollState),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        10.dp,
                                        Alignment.End
                                    )
                                ) {
                                    val dumpDir = filesDir.resolve("dumps/")
                                    var cleared by remember {
                                        mutableStateOf(
                                            dumpDir.listFiles()?.isEmpty() != false
                                        )
                                    }
                                    Button({
                                        dumpDir.deleteRecursively()
                                        cleared = true
                                    }, enabled = !cleared) { Text("Clear") }
                                    Button({
                                        val files = dumpDir.listFiles()
                                        if (files == null || files.isEmpty()) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "No reports saved",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            shareFiles(files.toList())
                                        }
                                    }) { Text("Share all") }
                                    Button({
                                        val timestamp = LocalDateTime.now()
                                            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                                        dumpDir.mkdirs()
                                        val fname = "$timestamp.json"
                                        val file = dumpDir.resolve(fname)
                                        file.writeText(result.toJSON().toString())
                                        Toast.makeText(
                                            this@MainActivity,
                                            "saved '$fname'",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        cleared = false
                                    }) { Text("Save current") }
                                    Button({
                                        scanner = WifiScanner(this@MainActivity)
                                    }) { Text("Scan") }
                                }
                            }) { innerPadding ->
                            val scrollState = remember { ScrollState(0) }
                            Column(
                                Modifier
                                    .padding(innerPadding)
                                    .verticalScroll(scrollState)
                            ) {
                                Text(
                                    "${result.size} networks found",
                                    style = MaterialTheme.typography.displaySmall
                                )
                                Text(result.joinToString("\n") {
                                    "${it.bssid} ${it.ssid} ~${it.estimatedDistance.toFixed()}m"
                                })
                            }
                        }
                    }
                } else {
                    MissingPermScreen {
                        requestPerms()
                    }
                }
            }
        }
    }

    fun shareFiles(files: List<File>) {
        val shareIntent: Intent = Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putExtra(
                Intent.EXTRA_STREAM,
                ArrayList(files.map {
                    FileProvider.getUriForFile(
                        this@MainActivity,
                        "io.github.amedeoalf.wifiscanner.MyFileProvider",
                        it
                    )
                })
            )
            type = "application/json"
        }

        startActivity(Intent.createChooser(shareIntent, null))
    }

}
