package io.github.amedeoalf.wifiscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class TileDb(private val ctx: Context) {
    private val cache = RAMCacheLayer(
        1000,
        CacheDirLayer(
            NetworkCacheLayer(null) { URL("http://172.24.163.134:3500/${pathOf(it)}") }
        ) { ctx.cacheDir.resolve("tiles/${pathOf(it)}") }) {
        it.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    private val getJobs = ConcurrentHashMap<ULong, Deferred<Bitmap?>>()

    suspend fun get(x: UInt, y: UInt, size: Int): Bitmap? {
        val key = toKey(x, y, size)
        cache.getSync(key)?.let {
            return@get it
        }
        return coroutineScope {
            (getJobs.getOrPut(key) { async { cache.get(key) } }).await()
        }
    }

    fun getSync(x: UInt, y: UInt, size: Int) = cache.getSync(toKey(x, y, size))

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

        val tileSizes = listOf(500, 1000, 2000, 4000)

        fun biggerTileSize(tileSize: Int) =
            tileSizes.indexOf(tileSize).let { if (it == -1) null else tileSizes.getOrNull(it + 1) }
    }
}