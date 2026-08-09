package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.exception.BaseException
import com.ua.astrumon.common.exception.DuplicateResourceException
import com.ua.astrumon.common.exception.ResourceNotFoundException
import com.ua.astrumon.common.exception.ValidationException
import com.ua.astrumon.presentation.bot.BotMessages

/**
 * Why adding a member to a group did not work, in the chat's language.
 *
 * Shared by the text command ([GroupController.addUserToGroup]) and the picker action
 * ([GroupPickerController.addUserToGroupById]) — the two surfaces used to carry the same `when`
 * twice, and a new failure kind had to be remembered in both (spovishun-172).
 */
internal fun addFailureReason(
    messages: BotMessages,
    exception: BaseException,
): String = when (exception) {
    is ValidationException -> messages.group.failureNotRegistered
    is DuplicateResourceException -> messages.group.failureAlreadyIn
    is ResourceNotFoundException -> messages.group.failureNotFound
    else -> messages.group.failureError
}
