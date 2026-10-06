package com.example.myapplication.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditDialog(
    contact: Contact?,
    methods: List<ContactMethod>,
    onDismiss: () -> Unit,
    onSave: (String, String, List<ContactMethod>, List<ContactMethod>, List<ContactMethod>) -> Unit
) {
    var name by remember { mutableStateOf(contact?.name ?: "") }
    var description by remember { mutableStateOf(contact?.description ?: "") }

    val originalName = remember(contact) { contact?.name ?: "" }
    val originalDescription = remember(contact) { contact?.description ?: "" }
    val originalMethods = remember(methods) { methods.toList() }

    var localMethods by remember(contact) { mutableStateOf(methods.toList()) }
    var hasUserInteracted by remember(contact) { mutableStateOf(false) }

    LaunchedEffect(contact?.id, methods) {
        if (!hasUserInteracted && methods.isNotEmpty()) {
            localMethods = methods.toList()
        }
    }

    val addedMethods = remember { mutableStateListOf<ContactMethod>() }
    val updatedMethods = remember { mutableStateListOf<ContactMethod>() }
    val deletedMethods = remember { mutableStateListOf<ContactMethod>() }

    var showAddMethodDialog by remember { mutableStateOf(false) }
    var editingMethod by remember { mutableStateOf<ContactMethod?>(null) }

    fun addMethodLocally(methodType: String, value: String) {
        hasUserInteracted = true
        val newMethod = ContactMethod(
            contactId = contact?.id ?: "temp_${System.currentTimeMillis()}",
            methodType = methodType,
            value = value
        )
        localMethods = localMethods + newMethod
        addedMethods.add(newMethod)
    }

    fun updateMethodLocally(oldMethod: ContactMethod, newMethod: ContactMethod) {
        hasUserInteracted = true
        localMethods = localMethods.map {
            if (it.id == oldMethod.id) newMethod else it
        }
        if (addedMethods.contains(oldMethod)) {
            val index = addedMethods.indexOf(oldMethod)
            addedMethods[index] = newMethod
        } else if (!deletedMethods.contains(oldMethod)) {
            updatedMethods.removeAll { it.id == oldMethod.id }
            updatedMethods.add(newMethod)
        }
    }

    fun deleteMethodLocally(method: ContactMethod) {
        hasUserInteracted = true
        localMethods = localMethods.filter { it.id != method.id }
        if (addedMethods.contains(method)) {
            addedMethods.remove(method)
        } else {
            deletedMethods.add(method)
            updatedMethods.removeAll { it.id == method.id }
        }
    }

    fun cancelChanges() {
        name = originalName
        description = originalDescription
        localMethods = originalMethods.toList()
        addedMethods.clear()
        updatedMethods.clear()
        deletedMethods.clear()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::cancelChanges,
        title = {
            Text(if (contact == null) "Добавить контакт" else "Редактировать")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя*") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = name.isBlank(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )

                Divider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Способы связи",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    TextButton(
                        onClick = { showAddMethodDialog = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Добавить", fontSize = 12.sp)
                    }
                }

                if (localMethods.isEmpty()) {
                    Text(
                        text = "Нет способов связи",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        localMethods.forEach { method ->
                            EditableMethodItem(
                                method = method,
                                onEdit = { editingMethod = method },
                                onDelete = { deleteMethodLocally(method) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, description, addedMethods.toList(), updatedMethods.toList(), deletedMethods.toList())
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = ::cancelChanges) {
                Text("Отмена")
            }
        }
    )

    if (showAddMethodDialog) {
        MethodDialog(
            method = null,
            onDismiss = { showAddMethodDialog = false },
            onSave = { methodType, value ->
                addMethodLocally(methodType, value)
                showAddMethodDialog = false
            }
        )
    }

    if (editingMethod != null) {
        val oldMethod = editingMethod!!
        MethodDialog(
            method = oldMethod,
            onDismiss = { editingMethod = null },
            onSave = { methodType, value ->
                val updatedMethod = oldMethod.copy(
                    methodType = methodType,
                    value = value
                )
                updateMethodLocally(oldMethod, updatedMethod)
                editingMethod = null
            }
        )
    }
}

@Composable
private fun EditableMethodItem(
    method: ContactMethod,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = method.methodType,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = method.value,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Редактировать",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}