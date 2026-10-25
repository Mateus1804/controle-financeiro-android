package com.mateuswilliam.financas.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject

internal object CompatibilityFixtures {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    fun bytes(name: String) = instrumentation.context.assets.open("compatibility/$name").use { it.readBytes() }
    fun snapshot() = BackupSnapshot.decode(bytes("snapshot-v1.json"))
    fun database(context: Context, name: String): FinanceDatabase {
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("com.mateuswilliam.financas.data.FinanceDatabase/3.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val snapshot = JSONObject(bytes("snapshot-v1.json").toString(Charsets.UTF_8))
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (n in 0 until indices.length()) db.execSQL(indices.getJSONObject(n).getString("createSql").replace("\${TABLE_NAME}", table))
                val fields = entity.getJSONArray("fields")
                val columns = List(fields.length()) { fields.getJSONObject(it).getString("columnName") }
                val rows = snapshot.getJSONArray(table)
                for (n in 0 until rows.length()) {
                    val row = rows.getJSONObject(n)
                    val values = columns.map { column ->
                        val value = row.get(column)
                        when (value) { JSONObject.NULL -> null; is Boolean -> if (value) 1 else 0; else -> value }
                    }.toTypedArray()
                    db.execSQL("INSERT INTO $table (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})", values)
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = 3
        }
        return Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceDatabase.MIGRATION_3_4).build()
    }
}
