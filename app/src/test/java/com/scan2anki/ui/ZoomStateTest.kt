package com.scan2anki.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ZoomStateTest {

    private val size = IntSize(1000, 2000)

    @Test
    fun startsUnzoomedAndUntranslated() {
        val state = ZoomState()
        assertThat(state.scale).isEqualTo(1f)
        assertThat(state.offset).isEqualTo(Offset.Zero)
        assertThat(state.isZoomed).isFalse()
    }

    @Test
    fun pinchingInMultipliesScale() {
        val state = ZoomState()
        state.apply(zoomChange = 2f, pan = Offset.Zero, containerSize = size)
        assertThat(state.scale).isEqualTo(2f)
        assertThat(state.isZoomed).isTrue()
    }

    @Test
    fun scaleNeverGoesBelowFitOrAboveTheMaximum() {
        val state = ZoomState(maxScale = 4f)
        state.apply(zoomChange = 0.1f, pan = Offset.Zero, containerSize = size)
        assertThat(state.scale).isEqualTo(1f)

        state.apply(zoomChange = 100f, pan = Offset.Zero, containerSize = size)
        assertThat(state.scale).isEqualTo(4f)
    }

    @Test
    fun panningIsIgnoredWhileFullyZoomedOut() {
        // At scale 1 the content exactly fills the viewport, so there is nothing to pan to and
        // the image must not be draggable off-centre.
        val state = ZoomState()
        state.apply(zoomChange = 1f, pan = Offset(500f, 500f), containerSize = size)
        assertThat(state.offset).isEqualTo(Offset.Zero)
    }

    @Test
    fun panIsClampedSoTheImageAlwaysCoversTheViewport() {
        val state = ZoomState()
        state.apply(zoomChange = 2f, pan = Offset.Zero, containerSize = size)
        // At 2x the content overflows by one viewport, so translation tops out at half of each
        // dimension: 1000/2 across and 2000/2 down.
        state.apply(zoomChange = 1f, pan = Offset(10_000f, 10_000f), containerSize = size)
        assertThat(state.offset).isEqualTo(Offset(500f, 1000f))

        state.apply(zoomChange = 1f, pan = Offset(-100_000f, -100_000f), containerSize = size)
        assertThat(state.offset).isEqualTo(Offset(-500f, -1000f))
    }

    @Test
    fun zoomingBackOutReClampsAnExistingPan() {
        val state = ZoomState()
        state.apply(zoomChange = 4f, pan = Offset(10_000f, 10_000f), containerSize = size)
        assertThat(state.offset).isEqualTo(Offset(1500f, 3000f))

        // Dropping back to 1x must pull the image back to centre rather than leaving it parked
        // off-screen at the old translation.
        state.apply(zoomChange = 0.25f, pan = Offset.Zero, containerSize = size)
        assertThat(state.scale).isEqualTo(1f)
        assertThat(state.offset).isEqualTo(Offset.Zero)
    }

    @Test
    fun resetReturnsToFit() {
        val state = ZoomState()
        state.apply(zoomChange = 3f, pan = Offset(200f, 200f), containerSize = size)
        state.reset()
        assertThat(state.scale).isEqualTo(1f)
        assertThat(state.offset).isEqualTo(Offset.Zero)
        assertThat(state.isZoomed).isFalse()
    }

    @Test
    fun touchTolerancesShrinkAsYouZoomIn() {
        // A 24px on-screen target must map to a smaller distance in content units once the
        // content is magnified, or grabbing the split gets coarser the further you zoom.
        val state = ZoomState()
        assertThat(state.touchSlopFor(24f)).isEqualTo(24f)
        state.apply(zoomChange = 3f, pan = Offset.Zero, containerSize = size)
        assertThat(state.touchSlopFor(24f)).isEqualTo(8f)
    }
}
