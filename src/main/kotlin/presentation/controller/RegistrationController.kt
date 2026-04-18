package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.presentation.CommandResponse

class RegistrationController(
    private val autoRegisterService: AutoRegisterService,
) {
    suspend fun start(chatId: Long, userId: Long, username: String, firstName: String, userRole: MemberRole): CommandResponse {
        autoRegisterService.ensureUserRegistered(
            chatId = chatId,
            userId = userId,
            username = username,
            firstName = firstName,
            userRole = userRole
        )
        return CommandResponse.Success(buildWelcomeMessage())
    }

    /**
     * Registers a single user (used by StartCommand for admin sync).
     */
    suspend fun ensureUserRegistered(chatId: Long, userId: Long, username: String, firstName: String, userRole: MemberRole) {
        autoRegisterService.ensureUserRegistered(
            chatId = chatId,
            userId = userId,
            username = username,
            firstName = firstName,
            userRole = userRole
        )
    }

    /**
     * Handles /register: registers a single user and returns response.
     */
    suspend fun register(chatId: Long, userId: Long, username: String, firstName: String, userRole: MemberRole): CommandResponse {
        val alreadyRegistered = autoRegisterService.isUserRegistered(chatId, username)
        val result = autoRegisterService.ensureUserRegistered(chatId, userId, username, firstName, userRole)

        if (result.isFailure) {
            return CommandResponse.Error("$firstName, не вдалося зареєструватися. Спробуйте пізніше.")
        }

        val roleText = if (userRole == MemberRole.ADMIN) " як адміністратор \uD83D\uDD10" else ""
        return if (alreadyRegistered) {
            CommandResponse.Success("$firstName, ви вже зареєстровані в системі.")
        } else {
            CommandResponse.Success("$firstName, ви успішно зареєстровані в системі$roleText!")
        }
    }

    private fun buildWelcomeMessage(): String = """
        👋 <b>Spovishun на місці!</b> ${VersionInfo.getFullVersion()}

        📋 <b>Реєстрація:</b>
        • /register — зареєструватися в системі
        • Або просто напишіть будь-яке повідомлення

        Команди:
        • /all — сповістити всіх
        • /ping &lt;група&gt; [текст] — сповістити групу
        • /groups — список груп
        • /members — список учасників

        🔐 <b>Адмін:</b>
        • /newgroup &lt;назва&gt; — створити групу
        • /delgroup &lt;назва&gt; — видалити групу
        • /addtogroup &lt;назва&gt; @user1, @user2 — додати до групи
        • /removefromgroup &lt;назва&gt; @user1, @user2 — видалити з групи
        • /grantrole @user1, @user2 member|moderator|admin — призначити роль одному або кільком учасникам
          └ member: базовий доступ · moderator 🛡: керує групами · admin 🔐: повний доступ
    """.trimIndent()
}
