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
import javax.swing.SwingUtilities

/*
FreezeGuardAction: trivial timing example; runs very fast on EDT.
 */
class FreezeGuardAction : AnAction("Freeze Guard: Measure Current Action") {
    private val log = Logger.getInstance(FreezeGuardAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val heapBefore = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
        val t0 = System.nanoTime()

        // Minimal “measure” action
        val durationMs = (System.nanoTime() - t0) / 1_000_000.0
        val heapAfter = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
        val heapDelta = (heapAfter - heapBefore)

        val (stalls, longest) = FG.monitor.snapshotAndReset()
        val threadLabel = if (SwingUtilities.isEventDispatchThread()) "EDT" else "BGT"

        val event = ActionEvent(
            action = "FreezeGuardAction",
            durationMs = durationMs,
            thread = threadLabel,
            heapDeltaBytes = heapDelta,
            edtStalls = stalls,
            edtLongestStallMs = longest,
            tsIso = Instant.now().toString()
        )
        EventSender.sendAsync(event)
        Notifier.info(
            e.project, "Freeze Guard",
            "FreezeGuardAction: %.1f ms • heap %s → %s (Δ %s) • stalls %d (longest %.0f ms)".format(
                durationMs,
                Bytes.human(heapBefore), Bytes.human(heapAfter), Bytes.human(heapDelta),
                stalls, longest
            )
        )

        log.info("FreezeGuardAction event sent: $event")
    }
}
