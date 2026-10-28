package com.example.propertylisting.ui

import com.example.propertylisting.data.Listing
import org.junit.Assert.assertEquals
import org.junit.Test

class ListingGroupTest {

    private fun listing(id: String, group: String? = null) = Listing(
        id = id, address = "1 Main St", city = "Reston", price = 1.0, bedrooms = 1, bathrooms = 1.0,
        duplicateGroupId = group,
    )

    @Test
    fun `listings sharing a duplicate group collapse under the first occurrence`() {
        val groups = groupDuplicates(
            listOf(listing("A1", "g1"), listing("A2"), listing("B7", "g1"), listing("A3")),
        )
        assertEquals(listOf("A1", "A2", "A3"), groups.map { it.primary.id })
        assertEquals(listOf("B7"), groups.first().alternates.map { it.id })
    }

    @Test
    fun `listings without a group are never merged`() {
        val groups = groupDuplicates(listOf(listing("A1"), listing("A2")))
        assertEquals(2, groups.size)
        assertEquals(true, groups.all { it.alternates.isEmpty() })
    }
}
