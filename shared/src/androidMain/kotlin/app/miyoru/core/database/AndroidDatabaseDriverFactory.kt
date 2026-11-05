package app.miyoru.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.miyoru.db.MiyoruDatabase

class AndroidDatabaseDriverFactory(private val context: Context) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver =
        AndroidSqliteDriver(MiyoruDatabase.Schema, context, "miyoru.db")
}
