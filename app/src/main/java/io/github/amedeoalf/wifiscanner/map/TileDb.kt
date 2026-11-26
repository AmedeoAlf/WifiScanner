package io.github.amedeoalf.wifiscanner.map

import android.graphics.Bitmap

interface TileDb {
    suspend fun get(x: UInt, y: UInt, size: Int): Bitmap?

    fun getSync(x: UInt, y: UInt, size: Int): Bitmap?

    companion object {
        val tileSizes = listOf(500, 1000, 2000, 4000, 8000)

        fun biggerTileSize(tileSize: Int) =
            tileSizes.indexOf(tileSize).let { if (it == -1) null else tileSizes.getOrNull(it + 1) }
    }
}