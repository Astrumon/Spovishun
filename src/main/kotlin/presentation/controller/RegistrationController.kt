package com.ua.astrumon.presentation.controller

import com.ua.astrumon.common.util.VersionInfo
import com.ua.astrumon.domain.model.MemberRole
import com.ua.astrumon.domain.service.AutoRegisterService
import com.ua.astrumon.domain.service.MemberService
import com.ua.astrumon.presentation.CommandResponse

class RegistrationController(
    private val memberService: MemberService,
    private val autoRegisterService: AutoRegisterService,
) {
    /**
     * Registers the trigger user and returns the welcome message.
     * Admin sync and invitation sending are handled by StartCommand before this is called.
     */
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
        val result = memberService.createMember(
            chatId = chatId,
            userId = userId,
            username = username,
            firstName = firstName,
            role = userRole
        )

        val roleText = if (userRole == MemberRole.ADMIN) " як адміністратор \uD83D\uDD10" else ""
        return if (result.isSuccess) {
            CommandResponse.Success("$firstName, ви успішно зареєстровані в системі$roleText!")
        } else {
            CommandResponse.Success("$firstName, ви вже зареєстровані в системі.")
        }
    }

    private fun buildWelcomeMessage(): String = """
        👋 <b>Spovishun активний!</b> ${VersionInfo.getFullVersion()}

        📋 <b>Реєстрація:</b>
        • /register — зареєструватися в системі
        • Або просто напишіть будь-яке повідомлення

        Команди:
        • /all — пінгнути всіх
        • /ping &lt;група&gt; [текст] — пінгнути групу
        • /groups — список груп
        • /members — список учасників

        🔐 <b>Адмін:</b>
        • /newgroup &lt;ключ&gt; &lt;назва&gt; — створити групу
        • /delgroup &lt;ключ&gt; — видалити групу
        • /addtogroup &lt;ключ&gt; @user — додати до групи
        • /removefromgroup &lt;ключ&gt; @user — видалити з групи
        • /grantrole @user &lt;роль&gt; — призначити роль учаснику
    """.trimIndent()
}
