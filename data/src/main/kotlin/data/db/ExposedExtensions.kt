package com.ua.astrumon.data.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.LowerCase
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList

infix fun Column<String>.eqIgnoreCase(value: String): Op<Boolean> = LowerCase(this) eq value.lowercase()

/** Batch counterpart of [eqIgnoreCase] — same case folding, one round trip instead of N. */
infix fun Column<String>.inListIgnoreCase(values: Collection<String>): Op<Boolean> = LowerCase(this) inList values.map { it.lowercase() }
