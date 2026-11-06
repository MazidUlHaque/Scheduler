package com.mazidulhaque.kokhon.core.model

/**
 * How much a plan matters. Each level maps to a default [ReminderChain];
 * the user can accept it, pick another level, or customise the chain.
 */
enum class Importance {
    LOW,
    NORMAL,
    IMPORTANT,
    CRITICAL,
}
