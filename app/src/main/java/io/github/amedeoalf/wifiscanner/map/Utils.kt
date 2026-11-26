package io.github.amedeoalf.wifiscanner.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset

fun Offset.toIntOffset() = IntOffset(this.x.toInt(), this.y.toInt())

@Composable
fun rememberMapState(
    tileDb: TileDb,
    initialPosition: Offset = Offset.Zero,
    initialZoom: Float = 1f
) =
    remember { MapState(tileDb, initialPosition, initialZoom) }

fun computeRequestedSize(zoom: Float, smallerSize: Float) = (smallerSize / zoom).let { size ->
    TileDbImpl.tileSizes
        .windowed(2).find { (_, biggerTile) -> size < biggerTile }
        ?.first()
        ?: TileDbImpl.tileSizes.last()
}
