package com.example.myapplication.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.myapplication.data.models.*

@Database(
    entities = [
        Project::class,
        Material::class,
        WorkItem::class,
        Contact::class,
        ContactMethod::class,
        ObjectModel::class,
        SyncOperationEntity::class
    ],
    version = 24,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun materialDao(): MaterialDao
    abstract fun workItemDao(): WorkItemDao
    abstract fun contactDao(): ContactDao
    abstract fun contactMethodDao(): ContactMethodDao
    abstract fun objectDao(): ObjectDao
    abstract fun syncOperationDao(): SyncOperationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_app.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}