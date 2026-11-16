package io.github.amedeoalf.wifiscanner

import androidx.compose.runtime.MutableState

fun <T, R> onChange(v: MutableState<T>, newVal: T, cb: (T) -> R): R? {
    if (newVal == v.value) return null
    return cb(newVal).also {
        v.value = newVal
    }
}

fun UInt.roundTo(n: Int) = this / n.toUInt() * n.toUInt()
