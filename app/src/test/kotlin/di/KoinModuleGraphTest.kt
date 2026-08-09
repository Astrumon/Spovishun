package com.ua.astrumon.di

import com.ua.astrumon.admin.config.AdminApiConfig
import com.ua.astrumon.admin.docker.DockerApiClient
import com.ua.astrumon.presentation.bot.CommandRegistry
import com.ua.astrumon.presentation.bot.handler.CallbackRouter
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Static verification of the whole Koin object graph (spovishun-156).
 *
 * This is NOT a unit test of module logic — the `app/CLAUDE.md` rule "do NOT unit test Koin modules"
 * targets that. `verify()` only reflects over constructor signatures and never instantiates anything,
 * so it stays out of `AppConfig`/`AdminApiConfig` env reading and creates no HTTP client or DB pool.
 * Its job is to turn "a forgotten binding blows up on production startup" into a failing test.
 *
 * Coverage boundary: `verify()` inspects the *declared* type of each definition. For an
 * interface-bound one — `single<MemberRepository> { MemberRepositoryImpl() }` — the declared type is
 * the interface, which has no constructor, so nothing about `MemberRepositoryImpl` is checked. That
 * is harmless while every `*RepositoryImpl` is parameterless; give one a dependency and that
 * dependency sits outside this net.
 */
class KoinModuleGraphTest {
    /**
     * Composed from [appModules] — the same list `Application.initializeKoin()` starts Koin with, so a
     * module cannot be added to production wiring without also being verified here. `verify()` follows
     * `includes`, which is what makes cross-module dependencies (`presentationModule` →
     * `serviceModule`) resolvable.
     */
    private val allModules = module { includes(appModules) }

    /**
     * `verify()` reflects over each declared type's primary constructor, even when the definition
     * body is a factory call or a `getAll()` collection lookup. These parameters are supplied by the
     * definition itself, not resolved from the graph, so they are declared per-definition here rather
     * than whitelisted globally via `extraTypes` — a blanket `String::class` would silence a genuinely
     * missing binding anywhere in the graph.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun should_resolveEveryDependency_when_allModulesAreComposed() {
        allModules.verify(
            injections = injectedParameters(
                // Built by AdminApiConfig.fromEnv() — its primitives come from the environment.
                definition<AdminApiConfig>(Boolean::class, String::class, Int::class),
                // Docker API url, read off AdminApiConfig at binding time.
                definition<DockerApiClient>(String::class),
                // getAll() collection injection: List<BotCommand> / List<CallbackHandler>.
                definition<CommandRegistry>(List::class),
                definition<CallbackRouter>(List::class),
            ),
        )
    }
}
