package com.github.jinahn130.intellijfreezeguard

/*
Tiny singleton holder so every action can access the same EdtStallMonitor.
started just prevents starting the monitor twice if multiple projects open
 */
object FG {

    /*
    heartbeat inside EdtStallMonitor
    Every periodMs (50 ms), the monitor does:
    1. Record expected time (t_expected = now()).
    2. calls SwingUtilities.invokeLater { … }
        post a tiny runnable to the EDT that does almost nothing except:
        - read actual time (t_actual = now())
        - compute delay = t_actual - t_expected,
        - if delay >= stallThresholdMs (100 ms), count a stall and track the longest stall in this window.
    so, if anything blocks or heavily loads the EDT (Bad action, long painting, heavy listeners),
    the posted runnable runs late—that lateness is exactly the stall we count.
     */
    val monitor = EdtStallMonitor()
    @Volatile var started: Boolean = false
}
