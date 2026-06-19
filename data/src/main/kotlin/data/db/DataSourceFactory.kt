package com.ua.astrumon.data.db

import com.zaxxer.hikari.HikariConfig

object DataSourceFactory {
    fun create(
        url: String,
        driver: String,
        username: String = "",
        password: String = "",
        poolSize: Int = 10,
    ): HikariConfig = HikariConfig().apply {
        jdbcUrl = url
        driverClassName = driver
        this.username = username
        this.password = password
        maximumPoolSize = poolSize
        minimumIdle = 1
        isAutoCommit = false
        idleTimeout = 600000
        connectionTimeout = 30000
        maxLifetime = 1800000
        leakDetectionThreshold = 60000
    }
}
