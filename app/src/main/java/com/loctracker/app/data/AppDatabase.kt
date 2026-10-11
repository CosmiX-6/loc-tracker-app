package com.loctracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [Recipient::class, ShareSession::class, DeliveryRecord::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipients(): RecipientDao
    abstract fun sessions(): SessionDao
    abstract fun deliveries(): DeliveryDao

    companion object {
        fun create(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DbKeyProvider(context).passphrase()
            return Room.databaseBuilder(context, AppDatabase::class.java, "loctracker.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
