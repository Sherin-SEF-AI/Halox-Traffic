package com.haloxtraffic.core.sensors.camera

import java.util.ArrayDeque

/**
 * Bounded, thread-safe ring buffer of recent items. Backs the pre/post-event frame buffer (§10): a
 * violation COMMIT reaches backward into [snapshot] for context frames. Capacity is bounded by BOTH a
 * count and a total byte budget (via [sizeOf]) so memory stays within the device tier's envelope.
 */
class RingBuffer<T>(
    private val maxItems: Int,
    private val maxBytes: Long = Long.MAX_VALUE,
    private val sizeOf: (T) -> Long = { 0L },
) {
    private val deque = ArrayDeque<T>(maxItems)
    private var bytes = 0L
    private val lock = Any()

    fun add(item: T) {
        synchronized(lock) {
            deque.addLast(item)
            bytes += sizeOf(item)
            evict()
        }
    }

    private fun evict() {
        while (deque.size > maxItems || (bytes > maxBytes && deque.size > 1)) {
            val removed = deque.pollFirst() ?: break
            bytes -= sizeOf(removed)
        }
    }

    /** Immutable point-in-time copy, oldest first. */
    fun snapshot(): List<T> = synchronized(lock) { deque.toList() }

    fun clear() = synchronized(lock) {
        deque.clear()
        bytes = 0
    }

    val size: Int get() = synchronized(lock) { deque.size }
    val byteSize: Long get() = synchronized(lock) { bytes }
}
