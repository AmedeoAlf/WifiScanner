package io.github.amedeoalf.wifiscanner.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalResources
import io.github.amedeoalf.wifiscanner.roundTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MapState(val tileDb: TileDb, initialPosition: Offset, initialZoom: Float) {
    var position by mutableStateOf(initialPosition)
        private set
    var zoom by mutableFloatStateOf(initialZoom)
        private set
    var canvasSize by mutableStateOf(Size.Zero)
        private set

    fun addToPos(offset: Offset) {
        position += offset
    }

    fun setPos(newPos: Offset) {
        position = newPos
    }

    fun zoomBy(zoomFactor: Float, screenPos: Offset = canvasSize.center) {
        zoom *= zoomFactor
        val clamped = zoom.coerceIn(0.05f..4f)
        if (zoomFactor != 1f && zoom == clamped) {
            position += (screenPos / zoom) * (zoomFactor - 1)
        }
        zoom = clamped
    }

    fun setCanvasSizeFrom(canvas: DrawScope) {
        canvasSize = canvas.size
    }

    internal fun requiredTiles(): MutableList<TileReq> {
        val ret = mutableListOf<TileReq>()

        val reqSize = computeRequestedSize(zoom, canvasSize.minDimension)

        val xPos = position.x.toInt()
        val yPos = position.y.toInt()

        val size = canvasSize / zoom
        for (x in 0..(size.width.toInt() + reqSize) step reqSize) {
            for (y in 0..(size.height.toInt() + reqSize) step reqSize) {
                ret.add(
                    TileReq(
                        (x + xPos).toUInt().roundTo(reqSize),
                        (y + yPos).toUInt().roundTo(reqSize),
                        reqSize,
                        tileDb
                    )
                )
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            ret.parallelStream().forEach { launch { it.doReq() } }
        }

        return ret
    }

    companion object {
        @Composable fun previewMapState() = rememberMapState(DummyTileDb(LocalResources.current))
    }
}
