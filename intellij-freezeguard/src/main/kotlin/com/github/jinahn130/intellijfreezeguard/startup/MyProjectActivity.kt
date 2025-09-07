package com.github.jinahn130.intellijfreezeguard.startup

import com.github.jinahn130.intellijfreezeguard.FG
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {
    private val log = Logger.getInstance(MyProjectActivity::class.java)

    override suspend fun execute(project: Project) {
        // Light-weight: start the EDT heartbeat once per IDE session
        if (!FG.started) {
            FG.monitor.start()
            FG.started = true
            log.info("Freeze Guard: EDT stall monitor started (ProjectActivity)")
        }
    }
}
