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
import androidx.compose.ui.geometry.center
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
import kotlin.math.exp

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
fun Map(mapState: MapState, modifier: Modifier = Modifier) {
    val displayPos = remember { Animatable(mapState.position, Offset.VectorConverter) }

    val bitmaps = remember { mutableStateListOf<TileReq>() }
    var requestedSize by remember { mutableIntStateOf(0) }

    val decay = exponentialDecay<Offset>()
    var velocity by remember { mutableStateOf(Offset.Zero) }
    var centroidSize by remember { mutableFloatStateOf(0f) }
    var didFlick by remember { mutableStateOf(false) }
    var lastSwipeZoomTap by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(mapState.position, mapState.canvasSize, mapState.zoom) {
        val newRequestedSize = computeRequestedSize(mapState.zoom, mapState.canvasSize.minDimension)
        bitmaps.clear()
        bitmaps.addAll(mapState.requiredTiles())
        requestedSize = newRequestedSize
    }

    LaunchedEffect(didFlick) {
        if (didFlick) displayPos.animateDecay(velocity, decay) {
            mapState.setPos(value)
        }
    }

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

                        val event = awaitFirstDown().also { it.consume() }

                        var shouldSwipeZoom =
                            lastSwipeZoomTap.let {
                                it != null && event.uptimeMillis - it < 200
                            }

                        var justTapped = true

                        didFlick = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size == 1) {
                                val change = event.changes.first()
                                if (shouldSwipeZoom) {
                                    val y = change.position.y - change.previousPosition.y
                                    mapState.zoomBy(
                                        exp(y / mapState.canvasSize.height * 10f),
                                    )
                                    velocity = Offset.Zero
                                    change.consume()
                                    continue
                                } else if (justTapped && change.changedToUp())
                                    lastSwipeZoomTap = change.uptimeMillis
                            } else {
                                shouldSwipeZoom = false
                            }
                            justTapped = false
                            val size = event.calculateCentroidSize()
                            if (size > 0.01f) {
                                if (centroidSize != 0f)
                                    mapState.zoomBy(size / centroidSize, event.calculateCentroid())
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
                                                        it / ds.size.toFloat() / mapState.zoom
                                                    }
                                            }
                                    if (delta != null) {
                                        mapState.addToPos(delta)
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
                            displayPos.snapTo(mapState.position)
                        }
                    }
                }
            }
    ) {
        bitmaps.forEach {
            it.draw(this, if (didFlick) displayPos.value else mapState.position, mapState.zoom)
        }
        mapState.setCanvasSizeFrom(this)
    }
}

fun Offset.toIntOffset() = IntOffset(this.x.toInt(), this.y.toInt())

@Composable
fun rememberMapState(
    tileDb: TileDb,
    initialPosition: Offset = Offset.Zero,
    initialZoom: Float = 1f
) =
    remember { MapState(tileDb, initialPosition, initialZoom) }

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

    fun requiredTiles(): MutableList<TileReq> {
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
}