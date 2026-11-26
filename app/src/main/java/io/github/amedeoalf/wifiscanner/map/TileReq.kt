package io.github.amedeoalf.wifiscanner.map

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import io.github.amedeoalf.wifiscanner.roundTo

internal data class TileReq(
    val x: UInt,
    val y: UInt,
    val size: Int,
    val db: TileDb,
    var bitmap: Bitmap? = null,
) {
    suspend fun doReq() {
        if (x < 100000u && y < 100000u)
            bitmap = db.get(x, y, size)
    }

    fun draw(scope: DrawScope, position: Offset, zoom: Float) {
        val cords = Offset(
            x.toFloat(),
            y.toFloat()
        )

        val bm = bitmap
        if (bm != null) scope.drawImage(
            bm.asImageBitmap(),
            dstOffset = ((cords - position) * zoom).toIntOffset(),
            dstSize = (size * zoom).toInt().let {
                IntSize(it, it)
            }
        ) else {
            var biggerSize = TileDbImpl.biggerTileSize(size)
            while (biggerSize != null) {
                val newX = x.roundTo(biggerSize)
                val newY = y.roundTo(biggerSize)
                db.getSync(newX, newY, biggerSize)?.let { bitmap ->
                    scope.drawImage(
                        bitmap.asImageBitmap(),
                        dstOffset = ((Offset(
                            newX.toFloat(),
                            newY.toFloat()
                        ) - position) * zoom).toIntOffset(),
                        dstSize = (biggerSize * zoom).toInt().let {
                            IntSize(it, it)
                        }
                    )
                    return@draw
                }
                biggerSize = TileDbImpl.biggerTileSize(biggerSize)
            }
        }

    }
}

