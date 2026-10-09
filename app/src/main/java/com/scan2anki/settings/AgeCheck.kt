package com.scan2anki.settings

/** Result of the neutral age screen that gates Google Cloud Vision. The birth year is never stored. */
enum class AgeCheck { UNKNOWN, ADULT_OR_TEEN, UNDER_13 }

object AgeRule {
    private const val MAX_AGE = 120

    /**
     * Only the birth year is asked, so anyone who turns 13 during [currentYear] may still be 12:
     * they are treated as under 13. Returns null for a year that can't be a real birth year.
     */
    fun evaluate(birthYear: Int, currentYear: Int): AgeCheck? = when {
        birthYear > currentYear || birthYear < currentYear - MAX_AGE -> null
        currentYear - birthYear >= 14 -> AgeCheck.ADULT_OR_TEEN
        else -> AgeCheck.UNDER_13
    }
}
