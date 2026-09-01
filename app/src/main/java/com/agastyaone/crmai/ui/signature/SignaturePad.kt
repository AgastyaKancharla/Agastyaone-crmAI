package com.agastyaone.crmai.ui.signature

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * State for [SignaturePad], holding raw stroke points so [toBitmap] can re-render them
 * onto a plain Android [Bitmap] at capture time - reused across every signing step in
 * the digital intake flow (treatment-records consent, TPA data-sharing consent, and any
 * future signature capture) rather than each screen rolling its own drawing logic.
 */
class SignaturePadState {
    internal val strokes = mutableStateListOf<List<Offset>>()
    internal var currentStroke by mutableStateOf<List<Offset>>(emptyList())
    internal var canvasSize by mutableStateOf(IntSize.Zero)

    val isEmpty: Boolean get() = strokes.isEmpty() && currentStroke.isEmpty()

    fun clear() {
        strokes.clear()
        currentStroke = emptyList()
    }

    /** Renders the captured strokes onto a real bitmap, suitable for upload. */
    fun toBitmap(): Bitmap? {
        val size = canvasSize
        if (size.width <= 0 || size.height <= 0 || isEmpty) return null

        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)

        val paint = AndroidPaint().apply {
            color = AndroidColor.BLACK
            strokeWidth = 6f
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            isAntiAlias = true
        }

        for (stroke in strokes) {
            drawStrokeOnCanvas(canvas, stroke, paint)
        }
        return bitmap
    }

    private fun drawStrokeOnCanvas(canvas: AndroidCanvas, stroke: List<Offset>, paint: AndroidPaint) {
        if (stroke.size < 2) return
        for (i in 0 until stroke.size - 1) {
            val from = stroke[i]
            val to = stroke[i + 1]
            canvas.drawLine(from.x, from.y, to.x, to.y, paint)
        }
    }
}

@Composable
fun rememberSignaturePadState(): SignaturePadState = remember { SignaturePadState() }

/**
 * A blank canvas the patient/guardian signs on with a finger or stylus. Purely a
 * drawing surface - the caller decides what "confirm" and "clear" look like and calls
 * [SignaturePadState.toBitmap] when ready to upload.
 */
@Composable
fun SignaturePad(state: SignaturePadState, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .background(Color.White)
            .border(1.dp, Color.LightGray)
            .onSizeChanged { state.canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> state.currentStroke = listOf(offset) },
                    onDrag = { change, _ ->
                        change.consume()
                        state.currentStroke = state.currentStroke + change.position
                    },
                    onDragEnd = {
                        if (state.currentStroke.size >= 2) {
                            state.strokes.add(state.currentStroke)
                        }
                        state.currentStroke = emptyList()
                    },
                )
            },
    ) {
        val strokeStyle = Stroke(width = 6f, cap = StrokeCap.Round)
        for (stroke in state.strokes) {
            drawScopePath(stroke)?.let { path -> drawPath(path, Color.Black, style = strokeStyle) }
        }
        drawScopePath(state.currentStroke)?.let { path -> drawPath(path, Color.Black, style = strokeStyle) }
    }
}

private fun drawScopePath(points: List<Offset>): Path? {
    if (points.size < 2) return null
    val path = Path()
    path.moveTo(points.first().x, points.first().y)
    for (point in points.drop(1)) {
        path.lineTo(point.x, point.y)
    }
    return path
}

/** Placeholder label shown inside/under an empty [SignaturePad] - purely cosmetic. */
@Composable
fun SignaturePadHint(modifier: Modifier = Modifier) {
    Text("Sign above", modifier = modifier, textAlign = TextAlign.Center)
}
