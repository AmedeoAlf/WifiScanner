package io.github.amedeoalf.wifiscanner.cachelayer

import java.util.concurrent.ConcurrentHashMap

class RAMCacheLayer<T: Any>(
    var maxElements: Int,
    val innerLayer: CacheLayer<ULong, ByteArray>?,
    val deserialize: (ByteArray) -> T,
) {
    private val cache = ConcurrentHashMap<ULong, T>()

    private val appendList = ArrayDeque<ULong>()

    suspend fun get(k: ULong): T? {
        return cache[k] ?: innerLayer?.get(k)?.let {
            cache[k] = deserialize(it)
            appendList.add(k)
            if (appendList.size > maxElements) cache.remove(appendList.removeFirst())
            cache[k]
        }
    }

    fun getSync(k: ULong): T? {
        return cache[k]
    }
}