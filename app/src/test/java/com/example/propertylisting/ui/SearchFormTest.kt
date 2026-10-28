package com.example.propertylisting.ui

import com.example.propertylisting.data.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFormTest {

    @Test
    fun `empty form produces an unfiltered query`() {
        val result = SearchForm().validate()
        assertEquals(FormValidation.Valid(SearchQuery()), result)
    }

    @Test
    fun `fields are trimmed and parsed`() {
        val form = SearchForm(city = "  Reston ", keyword = " ", minPrice = "400000", maxPrice = "600000", minBedrooms = 3)
        val query = (form.validate() as FormValidation.Valid).query
        assertEquals(SearchQuery(minPrice = 400_000, maxPrice = 600_000, minBedrooms = 3, city = "Reston"), query)
    }

    @Test
    fun `min price above max price is rejected on the max field`() {
        val result = SearchForm(minPrice = "700000", maxPrice = "500000").validate()
        assertTrue(result is FormValidation.Invalid)
        assertEquals(setOf(FormField.MaxPrice), (result as FormValidation.Invalid).errors.keys)
    }

    @Test
    fun `equal min and max is allowed`() {
        assertTrue(SearchForm(minPrice = "500000", maxPrice = "500000").validate() is FormValidation.Valid)
    }

    @Test
    fun `query params omit unset filters but always include pagination`() {
        val params = SearchQuery(city = "Vienna", page = 2).toQueryParams()
        assertEquals(listOf("city" to "Vienna", "page" to "2", "pageSize" to "10"), params)
    }
}
