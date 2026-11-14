package io.github.amedeoalf.wifiscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.URL

class TileDb(private val ctx: Context) {
    private val cache = RAMCacheLayer(
        1000,
        CacheDirLayer(
            NetworkCacheLayer(null) { URL("http://10.42.0.1:3500/${pathOf(it)}") }
        ) { ctx.cacheDir.resolve("tiles/${pathOf(it)}") }) {
        it.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    suspend fun get(x: UInt, y: UInt, size: Int) = cache.get(toKey(x, y, size))

    companion object {
        private fun toKey(x: UInt, y: UInt, size: Int) =
            x.toULong() shl 48 or ((y and 0xffffu).toULong() shl 32) or (size / 100).toULong()

        private fun keyX(key: ULong) = key shr 48
        private fun keyY(key: ULong) = (key shr 32) and 0xffffu
        private fun keySize(key: ULong) = (key and 0xffffu) * 100u

        private fun pathOf(key: ULong): String {
            val x = keyX(key)
            val y = keyY(key)
            val size = keySize(key)
            return "${size}x${size}/Q155+$x+$y.png"
        }

    }
}