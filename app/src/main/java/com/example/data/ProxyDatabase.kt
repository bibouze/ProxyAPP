package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProxySettings::class, ProxyLogEntity::class], version = 2, exportSchema = false)
abstract class ProxyDatabase : RoomDatabase() {
    abstract fun proxyDao(): ProxyDao

    companion object {
        @Volatile
        private var INSTANCE: ProxyDatabase? = null

        fun getDatabase(context: Context): ProxyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ProxyDatabase::class.java,
                    "proxy_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
