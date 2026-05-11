package com.ua.astrumon.presentation.controller

import com.ua.astrumon.domain.service.ReleaseNotesService
import com.ua.astrumon.presentation.CommandResponse
import com.ua.astrumon.presentation.bot.BotMessages
import com.ua.astrumon.presentation.util.ReleaseNotesFormatter

class WhatsNewController(private val releaseNotesService: ReleaseNotesService) {

    suspend fun showLatest(): CommandResponse =
        releaseNotesService.getAll().fold(
            onSuccess = { CommandResponse.Success(ReleaseNotesFormatter.formatLatest(it)) },
            onFailure = { ex -> CommandResponse.Error(BotMessages.Error.prefixed(ex.userMessage)) }
        )

    suspend fun showHistory(): CommandResponse =
        releaseNotesService.getAll().fold(
            onSuccess = { CommandResponse.Success(ReleaseNotesFormatter.formatHistory(it)) },
            onFailure = { ex -> CommandResponse.Error(BotMessages.Error.prefixed(ex.userMessage)) }
        )
}
