package dev.droidnix.engine.display

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * The framebuffer the Compose viewer draws. Mutating a Bitmap while the render
 * thread reads it can tear, so [apply] is only ever called on the main thread;
 * the network thread hands over a private copy of the dirty rectangle.
 */
class RfbFramebuffer {

    var bitmap: Bitmap? = null
        private set
    var width = 0
        private set
    var height = 0
        private set

    /** Increments whenever the pixels or the size change; read it to redraw. */
    var version by mutableLongStateOf(0L)
        private set

    private var image: ImageBitmap? = null

    fun clear() {
        bitmap = null
        image = null
        width = 0
        height = 0
        version++
    }

    fun reset(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            clear()
            return
        }
        if (this.width == width && this.height == height && bitmap != null) return
        val next = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        next.eraseColor(android.graphics.Color.BLACK)
        bitmap = next
        image = next.asImageBitmap()
        this.width = width
        this.height = height
        version++
    }

    /** [pixels] contains exactly width*height ARGB pixels for the dirty rect. */
    fun apply(x: Int, y: Int, width: Int, height: Int, pixels: IntArray) {
        val target = bitmap ?: return
        if (width <= 0 || height <= 0) return
        if (x < 0 || y < 0 || x + width > this.width || y + height > this.height) return
        target.setPixels(pixels, 0, width, x, y, width, height)
        version++
    }

    fun imageBitmap(): ImageBitmap? = image
}
