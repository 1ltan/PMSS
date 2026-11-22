package com.example.lr_1.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SensorRecord::class], version = 2, exportSchema = true)
abstract class SmartBlindDatabase : RoomDatabase() {
    abstract fun sensorRecordDao(): SensorRecordDao

    companion object {
        @Volatile
        private var instance: SmartBlindDatabase? = null

        fun getInstance(context: Context): SmartBlindDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SmartBlindDatabase::class.java,
                "smart_blind.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sensor_records ADD COLUMN deviceId TEXT NOT NULL DEFAULT 'SMART-BLIND-19'")
                db.execSQL("ALTER TABLE sensor_records ADD COLUMN sensorType TEXT NOT NULL DEFAULT 'incidence_angle'")
                db.execSQL("ALTER TABLE sensor_records ADD COLUMN x REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sensor_records ADD COLUMN y REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sensor_records ADD COLUMN z REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sensor_records ADD COLUMN stateLabel TEXT")
                db.execSQL("ALTER TABLE sensor_records ADD COLUMN confidence REAL")
                db.execSQL("ALTER TABLE sensor_records ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE sensor_records SET synced = 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sensor_records_synced ON sensor_records(synced)")
            }
        }
    }
}
