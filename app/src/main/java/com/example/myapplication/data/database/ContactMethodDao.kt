package com.example.myapplication.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.data.models.ContactMethod
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactMethodDao {

    @Query("SELECT * FROM contact_methods WHERE contactId = :contactId")
    fun getContactMethods(contactId: String): Flow<List<ContactMethod>>

    @Query("SELECT * FROM contact_methods")
    fun getAllContactMethods(): Flow<List<ContactMethod>>

    @Query("SELECT * FROM contact_methods WHERE contactId = :contactId")
    suspend fun getContactMethodsOnce(contactId: String): List<ContactMethod>

    @Query("SELECT * FROM contact_methods WHERE id = :id")
    suspend fun getContactMethodById(id: String): ContactMethod?

    @Insert
    suspend fun insertContactMethod(method: ContactMethod)

    @Insert
    suspend fun insertContactMethods(methods: List<ContactMethod>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContactMethod(method: ContactMethod)

    @Update
    suspend fun updateContactMethod(method: ContactMethod)

    @Delete
    suspend fun deleteContactMethod(method: ContactMethod)

    @Query("DELETE FROM contact_methods WHERE id = :methodId")
    suspend fun deleteContactMethodById(methodId: String)

    @Query("DELETE FROM contact_methods WHERE contactId = :contactId")
    suspend fun deleteByContactId(contactId: String)

    @Query("DELETE FROM contact_methods WHERE contactId IN (:contactIds)")
    suspend fun deleteByContactIds(contactIds: List<String>)

    @Query("DELETE FROM contact_methods")
    suspend fun deleteAll()
}