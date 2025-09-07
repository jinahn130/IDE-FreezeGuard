package com.github.jinahn130.intellijfreezeguard

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Posts a tiny runnable (hearbeat) to the EDT every [periodMs].
 * If the runnable runs late by >= [stallThresholdMs], we count a stall and track the longest.

i.e.
The monitor posts a probe runnable to the EDT every period = 50 ms.
Any probe that actually runs ≥ threshold = 100 ms late is counted as a stall.
During a T = 1200 ms freeze, all probes posted early in the freeze are delayed a lot; the ones posted near the end are delayed less.

Longest stall reported ~ ≈ T (about 1200 ms).
 */
class EdtStallMonitor(
    private val periodMs: Long = 50,
    private val stallThresholdMs: Long = 100
) {
    private val stallCount = AtomicInteger(0)
    private val longestStallMs = AtomicLong(0)
    private var scheduler: ScheduledExecutorService? = null

    fun start() {
        if (scheduler != null) return
        scheduler = Executors.newSingleThreadScheduledExecutor(ThreadFactory { r ->
            Thread(r, "FreezeGuard-EDT-Heartbeat").apply { isDaemon = true }
        })
        scheduler!!.scheduleAtFixedRate({
            val expectedNs = System.nanoTime()
            SwingUtilities.invokeLater {
                val actualNs = System.nanoTime()
                val delayMs = (actualNs - expectedNs) / 1_000_000.0
                if (delayMs >= stallThresholdMs) {
                    stallCount.incrementAndGet()
                    val rounded = delayMs.roundToLong()
                    longestStallMs.updateAndGet { prev -> max(prev, rounded) }
                }
            }
        }, periodMs, periodMs, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        scheduler?.shutdownNow()
        scheduler = null
    }

    @Synchronized
    fun snapshotAndReset(): Pair<Int, Double> {
        val count = stallCount.getAndSet(0)
        val longest = longestStallMs.getAndSet(0).toDouble()
        return count to longest
    }
}
