package app.miyoru.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.miyoru.db.MiyoruDatabase

/**
 * JVM 驱动工厂。
 *
 * JdbcSqliteDriver 不托管 schema 版本（Android/Native 驱动才托管），因此这里显式读写
 * `PRAGMA user_version`：0 = 空库走 create；低于当前版本走 migrate；成功后把版本写回。
 * 少了这一步，第二次启动会重复建表并崩溃（table already exists）。
 */
class JvmDatabaseDriverFactory(private val dir: String) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        val driver = JdbcSqliteDriver("jdbc:sqlite:$dir/miyoru.db")
        val current = driver.readUserVersion()
        val target = MiyoruDatabase.Schema.version
        when {
            current == 0L -> MiyoruDatabase.Schema.create(driver)
            current < target -> MiyoruDatabase.Schema.migrate(driver, current, target)
            else -> return driver
        }
        driver.execute(null, "PRAGMA user_version = $target", 0)
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

private fun SqlDriver.readUserVersion(): Long =
    (executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor ->
            val hasRow = cursor.next().value
            QueryResult.Value(if (hasRow) cursor.getLong(0) ?: 0L else 0L)
        },
        parameters = 0,
    ) as QueryResult.Value).value
