package com.example.propertylisting.data

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val results: List<Listing>,
    val page: Int,
    val pageSize: Int,
    val totalResults: Int,
    val totalPages: Int,
)

@Serializable
data class Listing(
    val id: String,
    val source: String? = null,
    val address: String,
    val city: String,
    val state: String? = null,
    val zip: String? = null,
    val price: Double,
    val bedrooms: Int,
    val bathrooms: Double,
    val sqft: Int? = null,
    val listedDate: String? = null,
    val status: String? = null,
    val description: String? = null,
    val amenities: Amenities? = null,
    val duplicateGroupId: String? = null,
    val nearDuplicate: NearDuplicate? = null,
    val score: Double? = null,
)

@Serializable
data class Amenities(
    val petFriendly: Boolean? = null,
    val parking: Boolean? = null,
)

@Serializable
data class NearDuplicate(
    val ofId: String,
    val confidence: Double,
)

@Serializable
data class ErrorBody(val error: String)
