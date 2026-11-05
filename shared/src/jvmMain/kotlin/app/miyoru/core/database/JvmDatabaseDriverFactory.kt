package app.miyoru.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.miyoru.db.MiyoruDatabase

class JvmDatabaseDriverFactory(private val dir: String) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        val driver = JdbcSqliteDriver("jdbc:sqlite:$dir/miyoru.db")
        MiyoruDatabase.Schema.create(driver)
        return driver
    }
}

class InMemoryJvmDriverFactory : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MiyoruDatabase.Schema.create(driver)
        return driver
    }
}
