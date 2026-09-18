package com.example.myapplication.ui.theme.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod

@Composable
fun ContactCard(
    contact: Contact,
    onContactClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicateClick: () -> Unit,
    hasDuplicate: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onContactClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (contact.description.isNotBlank()) {
                    Text(
                        contact.description,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            Row {
                if (hasDuplicate) {
                    IconButton(onClick = onDuplicateClick) {
                        Icon(Icons.Default.Warning, "Дубликаты", tint = Color.Red)
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Редактировать", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ContactDetailsDialog(
    contact: Contact,
    methods: List<ContactMethod>,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(contact.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                if (contact.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(contact.description, fontSize = 14.sp, color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                Text("Способы связи", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))

                if (methods.isEmpty()) {
                    Text("Нет способов связи", fontSize = 14.sp, color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(methods) { method ->
                            MethodItem(method) {
                                clipboardManager.setText(AnnotatedString(method.value))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Закрыть")
                }
            }
        }
    }
}

@Composable
fun MethodItem(method: ContactMethod, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MethodIcon(method.methodType)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(method.methodType, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text(method.value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Icon(Icons.Default.ContentCopy, "Копировать", modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun MethodIcon(methodType: String, size: Int = 20) {
    when (val icon = getIconForMethod(methodType)) {
        is String -> Text(icon, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        else -> Icon(icon as ImageVector, null, modifier = Modifier.size(size.dp),
            tint = MaterialTheme.colorScheme.primary)
    }
}

fun getIconForMethod(methodType: String): Any {
    return when (methodType.lowercase()) {
        "телефон", "phone" -> Icons.Default.Phone
        "telegram" -> Icons.Default.Send
        "vk" -> "VK"
        "email", "почта" -> Icons.Default.Email
        "иное" -> Icons.Default.Edit
        else -> Icons.Default.Link
    }
}