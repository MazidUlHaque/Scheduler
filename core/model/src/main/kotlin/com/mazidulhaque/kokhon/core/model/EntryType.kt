package com.mazidulhaque.kokhon.core.model

/**
 * The kind of plan an entry represents. Each type has a default importance
 * level, which the user can change in Settings.
 */
enum class EntryType(
    val defaultImportance: Importance,
) {
    MEETING(Importance.NORMAL),
    EVENT(Importance.NORMAL),
    TASK(Importance.NORMAL),
    DINNER(Importance.NORMAL),
    DINNER_DATE(Importance.IMPORTANT),
    TOUR(Importance.IMPORTANT),
    MATCH(Importance.IMPORTANT),
    BIRTHDAY(Importance.IMPORTANT),
    APPOINTMENT(Importance.IMPORTANT),
    FLIGHT(Importance.CRITICAL),
    EXAM(Importance.CRITICAL),
    OTHER(Importance.NORMAL),
}
