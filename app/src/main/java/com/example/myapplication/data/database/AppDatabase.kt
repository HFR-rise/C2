package com.example.myapplication.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.WorkItem

@Database(
    entities = [
        Project::class,
        Material::class,
        WorkItem::class,
        Contact::class,
        ContactMethod::class,
        ObjectModel::class,
        SyncOperationEntity::class,
        EstimateDraftEntity::class,
        ChangeRequestEntity::class
    ],
    version = 34,
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
    abstract fun estimateDraftDao(): EstimateDraftDao
    abstract fun changeRequestDao(): ChangeRequestDao

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