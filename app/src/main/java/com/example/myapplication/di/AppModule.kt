package com.example.myapplication.di

import android.content.Context
import com.example.myapplication.data.database.AppDatabase
import com.example.myapplication.data.database.ChangeRequestDao
import com.example.myapplication.data.database.ContactDao
import com.example.myapplication.data.database.ContactMethodDao
import com.example.myapplication.data.database.EstimateDraftDao
import com.example.myapplication.data.database.MaterialDao
import com.example.myapplication.data.database.ObjectDao
import com.example.myapplication.data.database.ProjectDao
import com.example.myapplication.data.database.SyncOperationDao
import com.example.myapplication.data.database.WorkItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    @Singleton
    fun provideChangeRequestDao(db: AppDatabase): ChangeRequestDao = db.changeRequestDao()

    @Provides
    @Singleton
    fun provideEstimateDraftDao(db: AppDatabase): EstimateDraftDao = db.estimateDraftDao()

    @Provides
    @Singleton
    fun provideMaterialDao(db: AppDatabase): MaterialDao = db.materialDao()

    @Provides
    @Singleton
    fun provideWorkItemDao(db: AppDatabase): WorkItemDao = db.workItemDao()

    @Provides
    @Singleton
    fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()

    @Provides
    @Singleton
    fun provideContactMethodDao(db: AppDatabase): ContactMethodDao = db.contactMethodDao()

    @Provides
    @Singleton
    fun provideObjectDao(db: AppDatabase): ObjectDao = db.objectDao()

    @Provides
    @Singleton
    fun provideSyncOperationDao(db: AppDatabase): SyncOperationDao = db.syncOperationDao()
}
