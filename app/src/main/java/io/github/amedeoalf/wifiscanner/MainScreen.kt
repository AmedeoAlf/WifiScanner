package io.github.amedeoalf.wifiscanner

import android.net.wifi.WifiManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Devices.PIXEL_9
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.amedeoalf.wifiscanner.map.Map
import io.github.amedeoalf.wifiscanner.map.MapState
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun MainScreen(
    mapState: MapState,
    dumpDir: File,
    networks: List<ScanResult>,
    updateScanner: () -> Unit,
    toast: (msg: String) -> Unit,
    shareFiles: (files: List<File>) -> Unit,
) {
    val crosshairIcon = ImageVector.vectorResource(R.drawable.outline_location_searching_24)
    val crosshairPainter = rememberVectorPainter(crosshairIcon)

    val pinIcon = ImageVector.vectorResource(R.drawable.outline_location_on_24)
    val pinPainter = rememberVectorPainter(pinIcon)

    var pinPosition by remember { mutableStateOf<Offset?>(null) }

    val cleared = remember { mutableStateOf(false) }

    var oldScans by remember { mutableStateOf<List<Offset>>(emptyList()) }

    LaunchedEffect(Unit) {
        getPreviousScanPositions(dumpDir).let { oldScans = it }
    }

    Surface {
        Box(Modifier, contentAlignment = Alignment.BottomCenter) {
            Map(
                mapState,
                Modifier.fillMaxSize()
            )
            Canvas(Modifier.fillMaxSize()) {
                with(crosshairPainter) {
                    translate(
                        left = (mapState.canvasSize.width - crosshairIcon.defaultWidth.toPx()) / 2f,
                        top = (mapState.canvasSize.height - crosshairIcon.defaultHeight.toPx()) / 2f
                    ) {
                        draw(crosshairPainter.intrinsicSize)
                    }
                }

                with(pinPainter) {
                    for (scan in oldScans) {
                        val center = (scan - mapState.position) * mapState.zoom
                        translate(
                            left = center.x - pinIcon.defaultWidth.toPx() / 2,
                            top = center.y - pinIcon.defaultHeight.toPx(),
                        ) {
                            draw(pinPainter.intrinsicSize, alpha = 0.3f)
                        }
                    }
                }

                pinPosition?.let { pinPosition ->
                    with(pinPainter) {
                        val center = (pinPosition - mapState.position) * mapState.zoom
                        translate(
                            left = center.x - pinIcon.defaultWidth.toPx() / 2,
                            top = center.y - pinIcon.defaultHeight.toPx(),
                        ) {
                            draw(pinPainter.intrinsicSize)
                        }
                    }
                }

            }
            Row(
                Modifier
                    .safeContentPadding()
                    .padding(bottom = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var showingSheet by remember { mutableStateOf(false) }
                IconButton(
                    { showingSheet = true },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(painterResource(R.drawable.outline_menu_24), "manage saves")
                }
                if (showingSheet) ManageNetworksSheet(
                    toast,
                    shareFiles,
                    cleared,
                    updateScanner,
                    dumpDir
                ) {
                    showingSheet = false
                }

                Button(
                    {
                        pinPosition =
                            mapState.position + (mapState.canvasSize.toOffset() / mapState.zoom) / 2f
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = ButtonDefaults.elevatedButtonElevation()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy((-10).dp)
                    ) {
                        Text(
                            "Segna posizione",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text("${networks.size} reti trovate")
                    }
                }

                IconButton(
                    {
                        val filename = saveScan(dumpDir, pinPosition!!, networks)
                        toast("saved '$filename'")
                        cleared.value = false
                        getPreviousScanPositions(dumpDir).let { oldScans = it }
                    },
                    enabled = pinPosition != null,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(painterResource(R.drawable.outline_download_24), "manage saves")
                }
            }
        }
    }
}

fun getPreviousScanPositions(dumpDir: File) =
    (dumpDir.listFiles() ?: emptyArray())
        .map { file ->
            runCatching {
                JSONObject(file.readText())
                    .getJSONArray("location")
                    .let {
                        Offset(it.getDouble(0).toFloat(), it.getDouble(1).toFloat())
                    }
            }.getOrNull()
        }
        .filterNotNull()
        .also { println("${it.size} posizioni di vecchi report trovate") }

fun Size.toOffset() = Offset(width, height)

@Composable
@Preview(device = PIXEL_9, showSystemUi = true)
fun MainScreenPreview() = MainScreen(
    MapState.previewMapState(),
    dumpDir = File(""),
    networks = emptyList(),
    updateScanner = {},
    toast = {},
    shareFiles = {},
)
