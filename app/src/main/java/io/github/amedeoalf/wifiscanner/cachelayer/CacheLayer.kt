package io.github.amedeoalf.wifiscanner.cachelayer

abstract class CacheLayer<K, V>(private val innerLayer: CacheLayer<K, V>?) {
    open suspend fun get(k: K): V? = innerLayer?.get(k)
}