package com.dtraas.homestock.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers [HouseholdCapacityInfo.isAtOrNearLimit] — drives when HouseholdSettingsScreen should
 *  start nudging a non-Premium household toward Premium (see HouseholdSection). */
class HouseholdCapacityInfoTest {

    @Test
    fun `a Premium household (no limit) is never near the limit`() {
        val info = HouseholdCapacityInfo(memberCount = 50, limit = null, isPremium = true)
        assertFalse(info.isAtOrNearLimit)
    }

    @Test
    fun `well under the free-tier limit is not near it`() {
        val info = HouseholdCapacityInfo(memberCount = 0, limit = HouseholdMembersRepository.FREE_MEMBER_LIMIT, isPremium = false)
        assertFalse(info.isAtOrNearLimit)
    }

    @Test
    fun `one below the limit already counts as near it`() {
        val info = HouseholdCapacityInfo(memberCount = 1, limit = HouseholdMembersRepository.FREE_MEMBER_LIMIT, isPremium = false)
        assertTrue(info.isAtOrNearLimit)
    }

    @Test
    fun `at the limit counts as at it`() {
        val info = HouseholdCapacityInfo(
            memberCount = HouseholdMembersRepository.FREE_MEMBER_LIMIT,
            limit = HouseholdMembersRepository.FREE_MEMBER_LIMIT,
            isPremium = false,
        )
        assertTrue(info.isAtOrNearLimit)
    }
}
