package com.mazidulhaque.kokhon.core.model

/** A reminder chain the user saved under a name ("Exam", "Gentle") to reuse on other plans. */
data class ReminderPreset(
    val name: String,
    val chain: ReminderChain,
)
