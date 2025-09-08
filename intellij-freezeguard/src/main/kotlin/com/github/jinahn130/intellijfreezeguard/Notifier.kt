package com.github.jinahn130.intellijfreezeguard

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * NOTIFIER - User Interface Feedback System for Performance Metrics
 * 
 * PURPOSE:
 * This singleton provides a simple, consistent way to show performance measurements
 * to users via IntelliJ's notification system (the balloon popups that appear in 
 * the bottom right corner of the IDE).
 * 
 * WHY USER NOTIFICATIONS?
 * While telemetry data goes to monitoring systems for analysis, users also need
 * immediate feedback about performance issues. These notifications serve multiple purposes:
 * 1. Immediate feedback - Users see performance results right after actions complete
 * 2. Educational - Shows users what good vs bad performance looks like
 * 3. Debugging - Helps developers understand what their actions are doing
 * 4. Engagement - Makes the monitoring system visible and interactive
 * 
 * NOTIFICATION TYPES EXPLAINED:
 * IntelliJ has three main notification types, each with different visual styling:
 * - INFORMATION (blue icon): Normal, expected results (FreezeGuardAction, BackgroundFixAction)
 * - WARNING (yellow icon): Performance problems detected (BadBlockingAction)  
 * - ERROR (red icon): Serious failures (network errors, crashes) - not used in current code
 * 
 * VISUAL APPEARANCE:
 * Notifications appear as balloon popups in the bottom right corner showing:
 * - Title: "Freeze Guard" (identifies the source)
 * - Content: Performance metrics (duration, memory usage, stall count, etc.)
 * - Icon: Blue (i), yellow (⚠), or red (x) indicating severity
 * - Auto-dismiss: Disappear after a few seconds or when user clicks them
 * 
 * GROUP_ID SYSTEM:
 * IntelliJ groups notifications by ID for organization and user control.
 * Users can configure notification behavior per group in IDE settings:
 * - Show as balloon (default)
 * - Show in log only  
 * - Completely disable
 * 
 * THREAD SAFETY:
 * IntelliJ's notification system is thread-safe, so these methods can be called
 * from any thread (EDT, background threads, etc.) without synchronization concerns.
 * 
 * REAL-WORLD EXAMPLES:
 * - Good performance: "FreezeGuardAction: 0.8 ms • heap 45.2 MB → 45.2 MB (Δ 0 B) • stalls 0"
 * - Bad performance: "BadBlockingAction: 1205.3 ms (EDT) • heap 45.2 MB → 45.7 MB (Δ +512 KB) • stalls 24 (longest 1180 ms)"
 * - Fixed performance: "BackgroundFixAction: 1201.1 ms (BGT) • heap 45.2 MB → 45.7 MB (Δ +512 KB) • stalls 0"
 */
object Notifier {
    /**
     * GROUP_ID - Notification Category Identifier
     * 
     * This constant defines the notification group name that appears in IntelliJ settings.
     * Users can find "FreezeGuard" in Settings → Appearance & Behavior → Notifications
     * and customize how these notifications behave (balloon, log, disabled, etc.).
     * 
     * IMPORTANT: This must match the group ID defined in plugin.xml:
     * <notificationGroup id="FreezeGuard" displayType="BALLOON"/>
     */
    private const val GROUP_ID = "FreezeGuard"

    /**
     * INFO NOTIFICATION - Display Normal Performance Results
     * 
     * Used for showing baseline or good performance measurements. These appear
     * with a blue information icon and indicate that everything is working normally.
     * 
     * WHEN TO USE:
     * - FreezeGuardAction results (fast, no stalls)
     * - BackgroundFixAction results (proper threading, no EDT stalls)
     * - Successful network connectivity tests
     * - Any "good news" performance metrics
     * 
     * VISUAL STYLE:
     * - Blue (i) icon
     * - Normal text color
     * - Standard balloon appearance
     * - Neutral, informational tone
     * 
     * @param project IntelliJ project context (can be null for global notifications)
     * @param title Notification title (usually "Freeze Guard")
     * @param content Performance metrics and details to show user
     * 
     * EXAMPLE USAGE:
     * Notifier.info(project, "Freeze Guard", "BackgroundFixAction: 1.2s (BGT) • stalls 0")
     */
    fun info(project: Project?, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)              // Get our configured notification group
            .createNotification(title, content, NotificationType.INFORMATION)  // Blue info balloon
            .notify(project)                             // Show to user (project context for proper placement)
    }

    /**
     * WARNING NOTIFICATION - Display Performance Problems
     * 
     * Used for showing problematic performance measurements that users should
     * be aware of. These appear with a yellow warning icon to draw attention
     * to potential issues that need investigation.
     * 
     * WHEN TO USE:
     * - BadBlockingAction results (EDT blocking, high stall counts)
     * - Network connectivity failures
     * - Unexpected high memory usage
     * - Any performance metrics indicating problems
     * 
     * VISUAL STYLE:  
     * - Yellow (⚠) warning icon
     * - Attention-grabbing appearance
     * - Standard balloon with warning styling
     * - Indicates something needs attention
     * 
     * @param project IntelliJ project context (can be null for global notifications)
     * @param title Notification title (usually "Freeze Guard")  
     * @param content Performance metrics showing the problem details
     * 
     * EXAMPLE USAGE:
     * Notifier.warn(project, "Freeze Guard", "BadBlockingAction: 1205ms (EDT) • stalls 24 (longest 1180ms)")
     * 
     * WHY WARNING AND NOT ERROR?
     * BadBlockingAction is an intentional demonstration of bad performance, not an
     * actual error. It's working "as designed" to show performance problems, so
     * WARNING is more appropriate than ERROR level.
     */
    fun warn(project: Project?, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)              // Get our configured notification group  
            .createNotification(title, content, NotificationType.WARNING)  // Yellow warning balloon
            .notify(project)                             // Show to user (project context for proper placement)
    }
}
