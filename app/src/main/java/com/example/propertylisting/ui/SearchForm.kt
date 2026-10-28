package com.example.propertylisting.ui

import com.example.propertylisting.data.SearchQuery

enum class FormField { MinPrice, MaxPrice, TargetBudget }

/** Raw, unvalidated text exactly as the user typed it. */
data class SearchForm(
    val city: String = "",
    val keyword: String = "",
    val minPrice: String = "",
    val maxPrice: String = "",
    val minBedrooms: Int? = null,
    val targetBudget: String = "",
) {
    val isEmpty: Boolean
        get() = this == SearchForm()

    /**
     * Mirrors the server's own validation so obvious mistakes are caught before a round trip.
     * The server remains the source of truth: any 400 it returns is still surfaced to the user.
     */
    fun validate(): FormValidation {
        val errors = mutableMapOf<FormField, String>()

        fun parseAmount(field: FormField, text: String): Long? {
            if (text.isBlank()) return null
            val value = text.trim().toLongOrNull()
            if (value == null || value < 0) errors[field] = "Enter a whole dollar amount"
            return value
        }

        val min = parseAmount(FormField.MinPrice, minPrice)
        val max = parseAmount(FormField.MaxPrice, maxPrice)
        val budget = parseAmount(FormField.TargetBudget, targetBudget)

        if (min != null && max != null && min > max) {
            errors[FormField.MaxPrice] = "Must be at least the minimum price"
        }

        if (errors.isNotEmpty()) return FormValidation.Invalid(errors)

        return FormValidation.Valid(
            SearchQuery(
                minPrice = min,
                maxPrice = max,
                minBedrooms = minBedrooms,
                city = city.trim().ifEmpty { null },
                keyword = keyword.trim().ifEmpty { null },
                targetBudget = budget,
            ),
        )
    }
}

sealed interface FormValidation {
    data class Valid(val query: SearchQuery) : FormValidation
    data class Invalid(val errors: Map<FormField, String>) : FormValidation
}
