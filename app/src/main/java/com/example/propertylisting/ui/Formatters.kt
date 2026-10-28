package com.example.propertylisting.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
    maximumFractionDigits = 0
}

private val integerFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatPrice(price: Double): String = currencyFormat.format(price)

fun formatCount(value: Int): String = integerFormat.format(value)

/** 2.0 -> "2", 1.5 -> "1.5". */
fun formatBaths(bathrooms: Double): String =
    if (bathrooms % 1.0 == 0.0) bathrooms.toInt().toString() else bathrooms.toString()

/** Formats an ISO date such as "2026-09-04"; falls back to the raw text if it isn't one. */
fun formatListedDate(isoDate: String): String = try {
    LocalDate.parse(isoDate).format(dateFormat)
} catch (e: DateTimeParseException) {
    isoDate
}
