package io.github.amedeoalf.wifiscanner.cachelayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL

class NetworkCacheLayer<K>(
    innerLayer: CacheLayer<K, ByteArray>?,
    private val keyToURL: (K) -> URL,
) :
    CacheLayer<K, ByteArray>(innerLayer) {

    override suspend fun get(k: K): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val conn = keyToURL(k).openConnection() as HttpURLConnection
                println("requesting ${conn.url}")
                conn.connectTimeout = 2000
                if (conn.responseCode == 200)
                    conn.getInputStream().use { return@withContext it.readBytes() }
                else {
                    println("could not get ${keyToURL(k)}, got error ${conn.responseCode}")
                    super.get(k)
                }
            } catch (e: ConnectException) {
                println(e)
                null
            }
        }
}