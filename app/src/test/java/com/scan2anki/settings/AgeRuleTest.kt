package com.scan2anki.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgeRuleTest {

    private val now = 2026

    @Test
    fun turningFourteenThisYear_isAdultOrTeen() {
        assertThat(AgeRule.evaluate(now - 14, now)).isEqualTo(AgeCheck.ADULT_OR_TEEN)
    }

    @Test
    fun adult_isAdultOrTeen() {
        assertThat(AgeRule.evaluate(1980, now)).isEqualTo(AgeCheck.ADULT_OR_TEEN)
    }

    @Test
    fun turningThirteenThisYear_isUnder13() {
        assertThat(AgeRule.evaluate(now - 13, now)).isEqualTo(AgeCheck.UNDER_13)
    }

    @Test
    fun twelve_isUnder13() {
        assertThat(AgeRule.evaluate(now - 12, now)).isEqualTo(AgeCheck.UNDER_13)
    }

    @Test
    fun bornThisYear_isUnder13() {
        assertThat(AgeRule.evaluate(now, now)).isEqualTo(AgeCheck.UNDER_13)
    }

    @Test
    fun futureYear_isInvalid() {
        assertThat(AgeRule.evaluate(now + 1, now)).isNull()
    }

    @Test
    fun moreThan120YearsAgo_isInvalid() {
        assertThat(AgeRule.evaluate(now - 121, now)).isNull()
        assertThat(AgeRule.evaluate(now - 120, now)).isEqualTo(AgeCheck.ADULT_OR_TEEN)
    }
}
