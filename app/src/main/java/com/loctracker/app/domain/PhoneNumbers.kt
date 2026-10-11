package com.loctracker.app.domain

object PhoneNumbers {
    /**
     * Normalises user input to international digits without "+" (what wa.me expects).
     * Numbers in national format (leading 0) are rejected because the country code is unknown.
     * Returns null when the number cannot be valid (E.164 allows 8-15 digits).
     */
    fun normalize(input: String): String? {
        val t = input.trim()
        var digits = t.filter { it.isDigit() }
        if (t.startsWith("00")) digits = digits.drop(2)
        if (digits.startsWith("0")) return null
        return digits.takeIf { it.length in 8..15 }
    }

    fun display(digits: String) = "+$digits"
}
