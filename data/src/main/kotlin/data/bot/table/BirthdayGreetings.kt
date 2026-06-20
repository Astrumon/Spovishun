package com.ua.astrumon.data.bot.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object BirthdayGreetings : Table("birthday_greetings") {
    val memberId = long("member_id").references(Members.id)
    val year = integer("year")
    val sentAt = timestamp("sent_at")

    override val primaryKey = PrimaryKey(memberId, year)
}
