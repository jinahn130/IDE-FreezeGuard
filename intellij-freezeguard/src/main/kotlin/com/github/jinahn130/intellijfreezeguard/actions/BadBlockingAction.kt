package com.github.jinahn130.intellijfreezeguard.actions

import com.github.jinahn130.intellijfreezeguard.ActionEvent
import com.github.jinahn130.intellijfreezeguard.Bytes
import com.github.jinahn130.intellijfreezeguard.EventSender
import com.github.jinahn130.intellijfreezeguard.FG
import com.github.jinahn130.intellijfreezeguard.Notifier
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import java.lang.management.ManagementFactory
import java.time.Instant

/*
BadBlockingAction: does slow work on the EDT → intentional freeze.

To block interaction but still avoid freezing the EDT,
use a modal task (Task.Modal): the work still runs off the EDT, but the modal dialog prevents user interaction.
 */
class BadBlockingAction : AnAction("Freeze Guard: Run BAD Blocking Action") {
    private val log = Logger.getInstance(BadBlockingAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        // Intentionally runs on EDT to simulate a freeze
        val heapBefore = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
        val t0 = System.nanoTime()

        try {
            Thread.sleep(1200) // ~1.2s UI freeze
        } catch (_: InterruptedException) {}

        val durationMs = (System.nanoTime() - t0) / 1_000_000.0
        val heapAfter = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
        val heapDelta = (heapAfter - heapBefore)

        val (stalls, longest) = FG.monitor.snapshotAndReset()
        val event = ActionEvent(
            action = "BadBlockingAction",
            durationMs = durationMs,
            thread = "EDT",
            heapDeltaBytes = heapDelta,
            edtStalls = stalls,
            edtLongestStallMs = longest,
            tsIso = Instant.now().toString()
        )

        //in case the prometheus
        EventSender.ping().thenAccept { code ->
            // Balloon shows connectivity result (200 is good)
            Notifier.info(e.project, "Freeze Guard",
                "Collector /metrics HTTP $code")
        }
        EventSender.sendAsync(event)
        Notifier.warn(
            e.project, "Freeze Guard",
            "BadBlockingAction: %.1f ms (EDT) • heap %s → %s (Δ %s) • stalls %d (longest %.0f ms)".format(
                durationMs,
                Bytes.human(heapBefore), Bytes.human(heapAfter), Bytes.human(heapDelta),
                stalls, longest
            )
        )


        log.warn("BadBlockingAction executed (UI deliberately blocked)")
    }
}
