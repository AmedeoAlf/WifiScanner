package io.github.amedeoalf.wifiscanner

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class TileReq(
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
            var biggerSize = TileDb.biggerTileSize(size)
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
                biggerSize = TileDb.biggerTileSize(biggerSize)
            }
        }

    }
}

fun computeRequestedSize(zoom: Float, smallerSize: Float) = (smallerSize / zoom).let { size ->
    when {
        size < 1000f -> 500
        size < 2000f -> 1000
        size < 4000f -> 2000
        size < 8000f -> 4000
        else -> 8000
    }
}

@Composable
fun Map(tileDb: TileDb, modifier: Modifier = Modifier) {
    var pos by remember { mutableStateOf(Offset(5000f, 6000f)) }
    val displayPos = remember { Animatable(pos, Offset.VectorConverter) }
    var zoom by remember { mutableFloatStateOf(0.5f) }

    val bitmaps = remember { mutableStateListOf<TileReq>() }
    var requestedSize by remember { mutableIntStateOf(0) }

    val decay = exponentialDecay<Offset>()
    var velocity by remember { mutableStateOf(Offset.Zero) }
    var centroidSize by remember { mutableFloatStateOf(0f) }
    var didFlick by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    LaunchedEffect(canvasSize, pos, pos, zoom) {
        val newRequestedSize = computeRequestedSize(zoom, canvasSize.minDimension)
        bitmaps.clear()
        bitmaps.addAll(requiredTiles(pos, canvasSize, zoom, tileDb))
        requestedSize = newRequestedSize
    }
    LaunchedEffect(didFlick) {
        if (didFlick) displayPos.animateDecay(velocity, decay) {
            pos = value
        }
    }
//    Text(
//        "zoom: $zoom, minSize: ${canvasSize.minDimension}, ${bitmaps.size} bitmaps: " + bitmaps.take(
//            10
//        ).joinToString { "${it.x} ${it.y} ${it.size} ${it.bitmap != null}" })
    Canvas(
        modifier
            .background(Color.White)
            .clipToBounds()
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        var lastMovement = 0L
                        var releaseTime = 0L
                        var movementTime = 0L
                        val lastPointerCount = mutableIntStateOf(0)
                        awaitFirstDown().consume()
                        didFlick = false
                        do {
                            val event = awaitPointerEvent()
                            val size = event.calculateCentroidSize()
                            if (size > 0.01f) {
                                if (centroidSize != 0f) {
                                    val centroid = event.calculateCentroid()
                                    val zoomFactor = size / centroidSize
                                    zoom *= zoomFactor
                                    val clamped = zoom.coerceIn(0.05f..4f)
                                    if (zoomFactor != 1f && zoom == clamped) {
                                        pos += (centroid / zoom) * (zoomFactor - 1)
                                    }
                                    zoom = clamped
                                }
                                centroidSize = size
                            } else {
                                centroidSize = 0f
                            }

                            event.changes.filter { it.changedToUp() }
                                .maxOfOrNull { it.uptimeMillis }
                                ?.let {
                                    releaseTime = it
                                }

                            event.changes.filter { it.positionChanged() }
                                .maxOfOrNull { it.uptimeMillis }?.let {
                                    movementTime = it
                                }

                            event.motionEvent?.let { motionEv ->
                                val didFingersChange =
                                    onChange(lastPointerCount, motionEv.pointerCount) { true }
                                        ?: false
                                if (motionEv.action == MotionEvent.ACTION_MOVE && !didFingersChange) {
                                    val delta =
                                        event.changes.filter { it.pressed }
                                            .map { -it.positionChange() }
                                            .let { ds ->
                                                ds.filter { it.isSpecified }
                                                    .reduceOrNull { a, b -> a + b }?.let {
                                                        it / ds.size.toFloat() / zoom
                                                    }
                                            }
                                    if (delta != null) {
                                        pos += delta
                                        val dt =
                                            (motionEv.eventTime - lastMovement).toFloat() / 1000f
                                        if (dt > 0.001f)
                                            velocity = delta / dt
                                        lastMovement = motionEv.eventTime
                                    }
                                }
                            }
                            event.changes.maxBy { it.uptimeMillis } // why is this here??
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        didFlick = (releaseTime - movementTime) < 20
                        this@coroutineScope.launch {
                            displayPos.snapTo(pos)
                        }
                    }
                }
            }
    ) {
        bitmaps.forEach {
            it.draw(this, if (didFlick) displayPos.value else pos, zoom)
        }
        canvasSize = size
    }
}

fun Offset.toIntOffset() = IntOffset(this.x.toInt(), this.y.toInt())

fun requiredTiles(
    position: Offset,
    size: Size,
    zoom: Float,
    tileDb: TileDb
): MutableList<TileReq> {
    val ret = mutableListOf<TileReq>()

    val reqSize = computeRequestedSize(zoom, size.minDimension)

    val xPos = position.x.toInt()
    val yPos = position.y.toInt()

    val size = size / zoom
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