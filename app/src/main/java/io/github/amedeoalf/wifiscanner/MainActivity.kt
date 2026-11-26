package io.github.amedeoalf.wifiscanner

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.amedeoalf.wifiscanner.map.Map
import io.github.amedeoalf.wifiscanner.map.TileDbImpl
import io.github.amedeoalf.wifiscanner.map.rememberMapState
import io.github.amedeoalf.wifiscanner.ui.theme.WifiScannerTheme
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
                WifiScannerTheme {
                    MissingPermScreen {
                        requestPerms()
                    }
                }
                return@setContent
            }

            var scanner = remember { WifiScanner(this) }
            WifiScannerTheme {
                var result by remember { mutableStateOf(emptyList<ScanResult>()) }
                val handler = Handler(Looper.getMainLooper())
                LaunchedEffect(Unit) {
                    handler.runOnInterval(500) {
                        result = scanner.getNetworks().sortedBy { it.estimatedDistance }
                    }
                }
                MainScreen(
                    rememberMapState(TileDbImpl(this), initialPosition = Offset(5000f, 6000f)),
                    filesDir.resolve("dumps/"),
                    networks = result,
                    updateScanner = { scanner = WifiScanner(this) },
                    toast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() },
                    shareFiles = { shareFiles(this, it) },
                )
            }
        }
    }


}

fun Handler.runOnInterval(onEveryMs: Long, fn: () -> Unit) {
    val updateResult =
        object : Runnable {
            override fun run() {
                fn()
                postDelayed(this, onEveryMs)
            }
        }
    updateResult.run()
}

fun shareFiles(ctx: Context, files: List<File>) {
    val shareIntent: Intent = Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {
        action = Intent.ACTION_SEND_MULTIPLE
        putExtra(
            Intent.EXTRA_STREAM,
            ArrayList(files.map {
                FileProvider.getUriForFile(
                    ctx,
                    "io.github.amedeoalf.wifiscanner.MyFileProvider",
                    it
                )
            })
        )
        type = "application/json"
    }

    ctx.startActivity(Intent.createChooser(shareIntent, null))
}
