package com.zero.crm.security

/**
 * Enterprise Bulk Activation Engine & Security Manager
 * Handles offline cryptographic verification of organization license codes.
 */
object SecurityManager {
    /**
     * Verifies if a 16-character string contains a valid mathematical checksum and a GCC prefix.
     * Format: GCCPrefix-Part1-Part2-Part3, e.g., "KW-1234-5678-ABC" (exactly 16 characters).
     */
    fun verifyEnterpriseBulkKey(key: String): Boolean {
        val cleaned = key.trim().uppercase()
        if (cleaned.length != 16) return false

        // Valid prefixes: KW- (Kuwait), SA- (Saudi Arabia), AE- (UAE), BH- (Bahrain), QA- (Qatar), OM- (Oman)
        val gccPrefixes = listOf("KW-", "SA-", "AE-", "BH-", "QA-", "OM-")
        val hasGccPrefix = gccPrefixes.any { cleaned.startsWith(it) }
        if (!hasGccPrefix) return false

        // Ensure proper format with dashes: e.g., "KW-1234-5678-ABC"
        if (cleaned[7] != '-' || cleaned[12] != '-') return false

        val part1 = cleaned.substring(3, 7)
        val part2 = cleaned.substring(8, 12)
        val part3 = cleaned.substring(13, 16)

        // Mathematical checksum verification
        val sumPart1 = part1.map { it.code }.sum()
        val sumPart2 = part2.map { it.code }.sum()
        val sumPart3 = part3.map { it.code }.sum()

        // Offline checksum algorithm: (sumPart1 * 3 + sumPart2 * 7) % 19 == sumPart3 % 19
        return (sumPart1 * 3 + sumPart2 * 7) % 19 == sumPart3 % 19
    }
}
