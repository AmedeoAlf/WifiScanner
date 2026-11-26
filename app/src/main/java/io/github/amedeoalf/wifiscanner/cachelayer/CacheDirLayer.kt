package io.github.amedeoalf.wifiscanner.cachelayer

import java.io.File

class CacheDirLayer<K>(
    innerLayer: CacheLayer<K, ByteArray>?,
    private val keyToFile: (K) -> File,
) : CacheLayer<K, ByteArray>(innerLayer) {
    override suspend fun get(k: K): ByteArray? {
        val file = keyToFile(k)
        if (file.exists()) {
            println("using disk-cached ${file.path}")
            return file.readBytes()
        }
        return super.get(k)?.also {
            file.parentFile!!.mkdirs()
            file.writeBytes(it)
        }
    }
}