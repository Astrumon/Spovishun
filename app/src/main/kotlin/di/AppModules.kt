package com.ua.astrumon.di

import org.koin.core.module.Module

/**
 * The composition root's module set — the single source of truth for both `startKoin` and the
 * graph verification test (spovishun-156).
 *
 * Kept in one place so a module added here cannot slip past `KoinModuleGraphTest`: a list written
 * out separately in the test would let a new module go unverified, or an already-removed one keep
 * the test green while the production graph is incomplete.
 */
internal val appModules: List<Module> = listOf(
    configModule,
    repositoryModule,
    serviceModule,
    presentationModule,
    adminApiDiModule,
)
