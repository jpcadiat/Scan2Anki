package com.scan2anki.util

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageUtilsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun decodeRotated_appliesExifRotation() {
        val file = File(tmp.root, "rotated.jpg")
        val bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val decoded = ImageUtils.decodeRotated(file.absolutePath)
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.width).isEqualTo(200)
        assertThat(decoded.height).isEqualTo(100)
    }

    @Test
    fun decodeRotated_noExif_keepsDimensions() {
        val file = File(tmp.root, "plain.jpg")
        val bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val decoded = ImageUtils.decodeRotated(file.absolutePath)
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.width).isEqualTo(100)
        assertThat(decoded.height).isEqualTo(200)
    }

    @Test
    fun decodeRotated_missingFile_returnsNull() {
        assertThat(ImageUtils.decodeRotated(File(tmp.root, "nope.jpg").absolutePath)).isNull()
    }
}
