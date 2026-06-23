package com.haloxtraffic.core.sensors

import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.sensors.camera.RingBuffer
import org.junit.Test

class RingBufferTest {

    @Test fun `evicts oldest beyond max count`() {
        val rb = RingBuffer<Int>(maxItems = 3)
        (1..5).forEach { rb.add(it) }
        assertThat(rb.snapshot()).containsExactly(3, 4, 5).inOrder()
        assertThat(rb.size).isEqualTo(3)
    }

    @Test fun `evicts by byte budget`() {
        // each item "costs" 10 bytes; budget 25 keeps the last 2 (item 1 stays at min size 1 then trims).
        val rb = RingBuffer<Int>(maxItems = 100, maxBytes = 25, sizeOf = { 10L })
        (1..5).forEach { rb.add(it) }
        assertThat(rb.snapshot()).containsExactly(4, 5).inOrder()
        assertThat(rb.byteSize).isAtMost(25)
    }

    @Test fun `clear empties the buffer`() {
        val rb = RingBuffer<Int>(maxItems = 3)
        rb.add(1); rb.add(2)
        rb.clear()
        assertThat(rb.size).isEqualTo(0)
        assertThat(rb.snapshot()).isEmpty()
    }
}
