package com.scan2anki.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.scan2anki.R

/**
 * Pan/zoom state for a page image.
 *
 * [scale] and [offset] are applied through a `graphicsLayer` on the *content* node, which means
 * any gesture detector living inside the content still receives pointer positions in the
 * content's own (untransformed) coordinate space -- Compose inverts the layer transform when it
 * routes pointer events. Overlay hit-testing therefore needs no zoom-awareness beyond scaling
 * its touch tolerances by [scale] (see [touchSlopFor]).
 */
@Stable
class ZoomState(private val maxScale: Float = 6f) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean get() = scale > 1.01f

    /** Converts an on-screen touch tolerance to the content-local distance that matches it. */
    fun touchSlopFor(px: Float): Float = px / scale

    fun apply(zoomChange: Float, pan: Offset, containerSize: IntSize) {
        val newScale = (scale * zoomChange).coerceIn(1f, maxScale)
        // Pan in content units so a drag tracks the finger regardless of current zoom.
        val newOffset = offset + pan
        scale = newScale
        offset = clamp(newOffset, newScale, containerSize)
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private fun clamp(candidate: Offset, forScale: Float, size: IntSize): Offset {
        // graphicsLayer uses a centered transform origin, so the content stays edge-to-edge as
        // long as each translation component is within half of the overflow it created.
        val maxX = (forScale - 1f) * size.width / 2f
        val maxY = (forScale - 1f) * size.height / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }
}

@Composable
fun rememberZoomState(maxScale: Float = 6f): ZoomState = remember { ZoomState(maxScale) }

/**
 * Wraps [content] in a pinch-to-zoom / drag-to-pan viewport.
 *
 * Multi-touch gestures are intercepted on [PointerEventPass.Initial] by this container, so they
 * never reach [content]; single-pointer gestures are left entirely alone and fall through to
 * whatever the content installs. That split is what lets the zone editor keep one-finger
 * direct manipulation while two fingers zoom.
 */
@Composable
fun ZoomableBox(
    state: ZoomState,
    modifier: Modifier = Modifier,
    showResetControl: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        // Two fingers pan and zoom; one finger always belongs to the content, so
                        // editing gestures stay available at every zoom level.
                        if (pressed.size >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                state.apply(zoom, pan, size)
                                // Consuming on the Initial pass keeps the pinch away from the
                                // content's own single-pointer drag handler.
                                pressed.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.offset.x
                    translationY = state.offset.y
                },
            content = content,
        )
        if (showResetControl && state.isZoomed) {
            FilledTonalIconButton(
                onClick = state::reset,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp),
            ) {
                Icon(Icons.Filled.ZoomOutMap, contentDescription = stringResource(R.string.cd_reset_zoom))
            }
        }
    }
}
