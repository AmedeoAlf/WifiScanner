package io.github.amedeoalf.wifiscanner

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

@Composable
fun Map(tileDb: TileDb, modifier: Modifier = Modifier) {
    var pos by remember { mutableStateOf(Offset(5000f, 6000f)) }
    val displayPos = remember { Animatable(pos, Offset.VectorConverter) }
    var zoom by remember { mutableFloatStateOf(0.5f) }

    data class TileReq(val x: UInt, val y: UInt, val size: Int, val bitmap: Bitmap? = null) {
        suspend fun doReq(tileDb: TileDb) = TileReq(x, y, size, tileDb.get(x, y, size))
    }

    val bitmaps = remember { mutableStateListOf<TileReq>() }

    LaunchedEffect(Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        for (x in 0..5) {
            for (y in 0..5) {
                val idx = bitmaps.size
                bitmaps.add(TileReq(5000u + (x * 1000).toUInt(), 3000u + (y * 1000).toUInt(), 1000))
                scope.launch {
                    bitmaps[idx] = bitmaps[idx].doReq(tileDb)
                }
            }
        }
    }

    // TODO: swipe than stop should set velocity to zero
    val decay = exponentialDecay<Offset>()
    var holding by remember { mutableIntStateOf(0) }
    var velocity by remember { mutableStateOf(Offset.Zero) }
    var lastUpdate by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(if (holding > 0) pos else null) {
        if (holding > 0) {
            displayPos.snapTo(pos)
        } else if (velocity.getDistanceSquared() > 4f) {
            displayPos.animateDecay(velocity, decay) {
                pos = value
            }
        }
    }
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTransformGestures(true) { centroid, pan, newZoom, rotation ->
                    val now = Instant.now()
                    pos += (centroid * (newZoom - 1) - pan) / zoom
                    zoom *= newZoom
                    velocity = (-pan / zoom) / (Duration.between(lastUpdate, now).toMillis()
                        .toFloat() / 1000f)
                    lastUpdate = now
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val input = awaitFirstDown(false)
                    holding += 1
                    drag(input.id) {}
                    holding -= 1

                }
            }
    ) {
        for ((x, y, size, bitmap) in bitmaps) {
            if (bitmap != null) drawImage(
                bitmap.asImageBitmap(),
                dstOffset = ((Offset(
                    x.toFloat(),
                    y.toFloat()
                ) - displayPos.value) * zoom * 0.999f).toIntOffset(),
                dstSize = (size * zoom).toInt().let {
                    IntSize(it, it)
                }
            )
        }
    }

}

fun Offset.toIntOffset() = IntOffset(this.x.toInt(), this.y.toInt())