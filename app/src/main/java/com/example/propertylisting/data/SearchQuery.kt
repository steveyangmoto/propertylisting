package com.example.propertylisting.data

/** A validated set of search parameters, ready to send to `GET /listings/search`. */
data class SearchQuery(
    val minPrice: Long? = null,
    val maxPrice: Long? = null,
    val minBedrooms: Int? = null,
    val city: String? = null,
    val keyword: String? = null,
    val targetBudget: Long? = null,
    val page: Int = 1,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    /** Only parameters the user actually set are sent, so the server applies its own defaults. */
    fun toQueryParams(): List<Pair<String, String>> = buildList {
        minPrice?.let { add("minPrice" to it.toString()) }
        maxPrice?.let { add("maxPrice" to it.toString()) }
        minBedrooms?.let { add("minBedrooms" to it.toString()) }
        city?.let { add("city" to it) }
        keyword?.let { add("keyword" to it) }
        targetBudget?.let { add("targetBudget" to it.toString()) }
        add("page" to page.toString())
        add("pageSize" to pageSize.toString())
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 10
    }
}
