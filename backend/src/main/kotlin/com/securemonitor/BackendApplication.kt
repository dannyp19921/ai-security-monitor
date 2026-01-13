// backend/src/main/kotlin/com/securemonitor/BackendApplication.kt
package com.securemonitor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [
    DataSourceAutoConfiguration::class,
    HibernateJpaAutoConfiguration::class
])
class BackendApplication

fun main(args: Array<String>) {
    runApplication<BackendApplication>(*args)
}