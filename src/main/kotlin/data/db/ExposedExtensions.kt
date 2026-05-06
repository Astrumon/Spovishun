package com.ua.astrumon.data.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.LowerCase
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

infix fun Column<String>.eqIgnoreCase(value: String): Op<Boolean> =
    LowerCase(this) eq value.lowercase()
