package io.github.amedeoalf.wifiscanner.map

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.amedeoalf.wifiscanner.R

class DummyTileDb(resources: Resources) : TileDb {
    val tiles = listOf(
        500 to R.drawable.map_placeholder_500,
        1000 to R.drawable.map_placeholder_1000,
        2000 to R.drawable.map_placeholder_2000,
        4000 to R.drawable.map_placeholder_4000,
        8000 to R.drawable.map_placeholder_8000,
    ).associate { (size, res) ->
        size to BitmapFactory.decodeResource(resources, res)
    }

    override suspend fun get(
        x: UInt,
        y: UInt,
        size: Int
    ): Bitmap? = tiles[size]

    override fun getSync(
        x: UInt,
        y: UInt,
        size: Int
    ): Bitmap? = tiles[size]
}