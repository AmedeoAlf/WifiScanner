package io.github.amedeoalf.wifiscanner

import android.net.http.NetworkException
import androidx.collection.mutableLongObjectMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import kotlin.text.toLong

abstract class CacheLayer<K, V>(private val innerLayer: CacheLayer<K, V>?) {
    open suspend fun get(k: K): V? = innerLayer?.get(k)
}

class NetworkCacheLayer<K>(
    innerLayer: CacheLayer<K, ByteArray>?,
    private val keyToURL: (K) -> URL,
) :
    CacheLayer<K, ByteArray>(innerLayer) {

    override suspend fun get(k: K): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val conn = keyToURL(k).openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                if (conn.responseCode == 200)
                    conn.getInputStream().use { return@withContext it.readBytes() }
                else {
                    println("could not get ${keyToURL(k)}, got error ${conn.responseCode}")
                    super.get(k)
                }
            } catch(e: ConnectException) {
                println(e)
                null
            }
        }
}

class CacheDirLayer<K>(
    innerLayer: CacheLayer<K, ByteArray>?,
    private val keyToFile: (K) -> File,
) : CacheLayer<K, ByteArray>(innerLayer) {
    override suspend fun get(k: K): ByteArray? {
        val file = keyToFile(k)
        if (file.exists()) return file.readBytes()
        return super.get(k)?.also {
            file.parentFile!!.mkdirs()
            file.writeBytes(it)
        }
    }
}

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
}
