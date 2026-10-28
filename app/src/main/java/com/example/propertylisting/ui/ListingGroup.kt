package com.example.propertylisting.ui

import com.example.propertylisting.data.Listing

/**
 * One property as shown in the list. The same property is often syndicated by more than one
 * MLS source; the server tags those copies with a shared `duplicateGroupId`.
 */
data class ListingGroup(
    val primary: Listing,
    val alternates: List<Listing> = emptyList(),
)

/**
 * Collapses listings that share a `duplicateGroupId` into a single group, keeping the first
 * occurrence (the highest-scored, since the server returns results ranked by score) as primary.
 * Only listings loaded so far can be merged, so a copy on a later page appears once that page loads.
 */
fun groupDuplicates(listings: List<Listing>): List<ListingGroup> {
    val groups = LinkedHashMap<String, MutableList<Listing>>()
    listings.forEach { listing ->
        val key = listing.duplicateGroupId ?: "id:${listing.id}"
        groups.getOrPut(key) { mutableListOf() }.add(listing)
    }
    return groups.values.map { ListingGroup(primary = it.first(), alternates = it.drop(1)) }
}

fun ungrouped(listings: List<Listing>): List<ListingGroup> = listings.map { ListingGroup(it) }
