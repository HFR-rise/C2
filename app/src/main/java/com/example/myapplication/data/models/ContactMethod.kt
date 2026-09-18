package com.example.myapplication.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "contact_methods",
    foreignKeys = [
        ForeignKey(
            entity = Contact::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["contactId"])]
)
data class ContactMethod(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val contactId: String,
    val methodType: String,
    val value: String,
    val userId: String = ""
)
