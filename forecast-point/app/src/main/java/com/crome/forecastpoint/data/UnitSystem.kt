package com.crome.forecastpoint.data

/** US customary or metric (SI) display and NWS request units. */
enum class UnitSystem {
    Us,
    Metric,
    ;

    val storageValue: String
        get() = if (this == Metric) "metric" else "us"

    val isMetric: Boolean
        get() = this == Metric

    companion object {
        fun fromStorage(raw: String?): UnitSystem =
            if (raw.equals("metric", ignoreCase = true)) Metric else Us
    }
}
