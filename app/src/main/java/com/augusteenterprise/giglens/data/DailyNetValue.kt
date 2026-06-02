package com.augusteenterprise.giglens.data

// Author: Claude (Anthropic)
// Last modified: DeepSeek (Ollama) - June 02 2026 - New projection class for analytics chart
// Maps Room @Query result: daily average net value grouped by calendar day

import androidx.room.ColumnInfo

data class DailyNetValue(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "avgNet") val avgNet: Double
)
