package com.ua.astrumon.presentation.controller

import com.ua.astrumon.presentation.CommandResponse

/** A single inline-picker button: [id] is encoded into callback-data, [label] is shown to the user. */
data class PickerOption(
    val id: Long,
    val label: String,
)

/**
 * Result of a controller picker-listing call. Carries picker data without leaking Telegram types into
 * the controller layer, and keeps the picker cases out of the universal [CommandResponse.toText].
 */
sealed interface PickerListing {
    /** Options to render as an inline keyboard. May be empty (caller decides the empty-state message). */
    data class Show(
        val options: List<PickerOption>,
    ) : PickerListing

    /** Access was denied or loading failed — render [response] as text instead of a keyboard. */
    data class Reject(
        val response: CommandResponse,
    ) : PickerListing
}
