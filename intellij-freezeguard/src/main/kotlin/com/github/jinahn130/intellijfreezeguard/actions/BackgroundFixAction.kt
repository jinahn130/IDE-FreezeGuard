package com.github.jinahn130.intellijfreezeguard.actions

import com.github.jinahn130.intellijfreezeguard.ActionEvent
import com.github.jinahn130.intellijfreezeguard.EventSender
import com.github.jinahn130.intellijfreezeguard.FG
import com.github.jinahn130.intellijfreezeguard.Notifier
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import java.lang.management.ManagementFactory
import java.time.Instant

/*
BackgroundFixAction: hands off heavy or blocking work like thread.sleep to
Task.Backgroundable (a pooled thread) → no freeze; only small UI updates happen on the EDT.
(EDT Happens during the invokeLater block)
 */
class BackgroundFixAction : AnAction("Freeze Guard: Run FIXED Background Action") {
    private val log = Logger.getInstance(BackgroundFixAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val heapBefore = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
        val t0 = System.nanoTime()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Freeze Guard: Background Work", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                // Same simulated workload but off EDT
                try {
                    Thread.sleep(1200)
                } catch (_: InterruptedException) {}

                val durationMs = (System.nanoTime() - t0) / 1_000_000.0
                val heapAfter = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
                val heapDelta = (heapAfter - heapBefore)

                val (stalls, longest) = FG.monitor.snapshotAndReset()
                val event = ActionEvent(
                    action = "BackgroundFixAction",
                    durationMs = durationMs,
                    thread = "BGT",
                    heapDeltaBytes = heapDelta,
                    edtStalls = stalls,
                    edtLongestStallMs = longest,
                    tsIso = Instant.now().toString()
                )
                EventSender.sendAsync(event)

                ApplicationManager.getApplication().invokeLater {
                    Notifier.info(e.project, "Freeze Guard",
                        "BackgroundFixAction: %.1f ms (BGT) • heap Δ %d • stalls %d (longest %.0f ms)".format(
                            durationMs, heapDelta, stalls, longest
                        )
                    )
                    log.info("BackgroundFixAction finished (no UI block)")
                }
            }
        })
    }
}
