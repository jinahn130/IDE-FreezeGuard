package com.github.jinahn130.intellijfreezeguard

/**
 * BYTES - Human-Readable Memory Formatting Utility
 * 
 * PURPOSE:
 * This utility converts raw byte counts into human-readable memory units (KB, MB, GB, etc.).
 * Instead of showing users "heap changed by 524288 bytes", we show "heap changed by +512 KB",
 * which is much easier to understand and interpret.
 * 
 * WHY THIS MATTERS:
 * Memory usage in programs is typically measured in bytes, but raw byte counts are hard
 * for humans to interpret quickly:
 * - 1048576 bytes → What does this mean? Hard to tell at a glance
 * - 1.0 MB → Immediately understandable as "one megabyte"
 * 
 * JVM memory management works in bytes, but users think in KB/MB/GB, so this utility
 * bridges that gap by converting between machine units and human units.
 * 
 * BINARY vs DECIMAL UNITS:
 * This utility uses BINARY units (powers of 1024), not decimal units (powers of 1000):
 * - 1 KiB = 1024 bytes (binary, used by operating systems and memory)
 * - 1 KB = 1000 bytes (decimal, used by storage manufacturers)
 * 
 * We use binary units because:
 * 1. JVM heap memory is allocated in binary chunks
 * 2. Operating system memory is measured in binary units  
 * 3. More accurate for actual memory usage measurements
 * 4. Standard practice in system monitoring tools
 * 
 * UNIT PROGRESSION:
 * - B (bytes): 1 byte
 * - KiB (kibibytes): 1,024 bytes  
 * - MiB (mebibytes): 1,048,576 bytes (1024²)
 * - GiB (gibibytes): 1,073,741,824 bytes (1024³)
 * - TiB (tebibytes): 1,099,511,627,776 bytes (1024⁴)
 * 
 * POSITIVE AND NEGATIVE HANDLING:
 * The utility handles both memory increases and decreases:
 * - Positive values: Memory allocated ("+512 KiB")
 * - Negative values: Memory freed ("-1.2 MiB")  
 * - Zero values: No change ("0 B")
 * 
 * PRACTICAL EXAMPLES FROM FREEZEGUARD:
 * - FreezeGuardAction: Usually 0 B (minimal memory impact)
 * - BadBlockingAction: Often +512 KiB to +2 MiB (creates telemetry objects)
 * - BackgroundFixAction: Similar to BadBlockingAction but on background thread
 * 
 * USAGE IN NOTIFICATIONS:
 * This utility is used in all performance notifications to show memory deltas:
 * "heap 45.2 MiB → 45.7 MiB (Δ +512 KiB)" instead of "heap 47447040 → 47971328 (Δ 524288)"
 */
object Bytes {
    /**
     * HUMAN READABLE MEMORY FORMATTER
     * 
     * Converts a raw byte count into a human-readable string with appropriate units.
     * Automatically selects the most appropriate unit (B, KiB, MiB, GiB, TiB) based
     * on the magnitude of the input value.
     * 
     * ALGORITHM BREAKDOWN:
     * 1. Handle negative values by extracting sign and working with absolute value
     * 2. Start with bytes (B) as the base unit  
     * 3. While value ≥ 1024 and we haven't reached the largest unit:
     *    - Divide by 1024 to move to next larger unit
     *    - Increment unit index (B → KiB → MiB → GiB → TiB)
     * 4. Format with one decimal place for precision
     * 5. Add sign back for negative values
     * 
     * EXAMPLES:
     * - human(0) → "0 B"
     * - human(512) → "512 B"  
     * - human(1024) → "1.0 KiB"
     * - human(1536) → "1.5 KiB" (1024 + 512)
     * - human(1048576) → "1.0 MiB"
     * - human(-2097152) → "-2.0 MiB" (negative = memory freed)
     * - human(524288) → "512.0 KiB" (common heap allocation size)
     * 
     * PRECISION CHOICE:
     * Uses one decimal place (%.1f) as a balance between:
     * - Accuracy: Shows meaningful precision without excessive detail  
     * - Readability: Easy to scan and understand quickly
     * - Consistency: All memory values formatted the same way
     * 
     * @param n Raw byte count (can be positive, negative, or zero)
     * @return Human-readable string with appropriate unit and sign
     * 
     * THREAD SAFETY:
     * This function is pure (no side effects) and thread-safe. It can be called
     * from any thread simultaneously without synchronization concerns.
     */
    fun human(n: Long): String {
        // UNIT DEFINITIONS: Binary units (powers of 1024)
        val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
        
        // HANDLE NEGATIVE VALUES: Work with absolute value, preserve sign
        var v = kotlin.math.abs(n.toDouble())  // Convert to double for division precision
        var i = 0  // Index into units array
        
        // UNIT SELECTION: Find the largest appropriate unit
        // Stop when: value < 1024 OR we've reached the largest unit (TiB)
        while (v >= 1024 && i < units.lastIndex) { 
            v /= 1024  // Move to next larger unit (B → KiB → MiB → etc.)
            i++        // Advance unit index
        }
        
        // SIGN HANDLING: Restore original sign for display
        val sign = if (n < 0) "-" else ""
        
        // FORMAT RESULT: Sign + value with 1 decimal + unit
        return "%s%.1f %s".format(sign, v, units[i])
    }
}
