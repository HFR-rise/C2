package com.example.myapplication.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.ui.components.MaterialItem
import com.example.myapplication.ui.components.TotalBudgetCard
import com.example.myapplication.ui.components.WorkItemCard

@Composable
fun ProjectStep1(
    form: ProjectFormState,
    isReadOnly: Boolean,
    canManageMembers: Boolean,
    showCustomerHint: Boolean,
    onFormUpdate: (ProjectFormState) -> Unit,
    onShowMemberSelector: (MemberRole) -> Unit
) {
    val membersLocked = isReadOnly || !canManageMembers

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = form.name,
                onValueChange = { if (!isReadOnly) onFormUpdate(form.copy(name = it)) },
                label = { Text("Название сметы*") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isReadOnly
            )
        }

        item {
            OutlinedTextField(
                value = form.description,
                onValueChange = { if (!isReadOnly) onFormUpdate(form.copy(description = it)) },
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !isReadOnly
            )
        }

        item { Divider() }
        item {
            Text(
                "Участники проекта",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        item {
            MemberRow(
                label = "Заказчик",
                contact = form.memberCustomer,
                isReadOnly = membersLocked,
                onSelect = { onShowMemberSelector(MemberRole.CUSTOMER) },
                onClear = { onFormUpdate(form.copy(memberCustomer = null)) }
            )
        }

        item {
            MemberRow(
                label = "Сметчик",
                contact = form.memberEstimator,
                isReadOnly = membersLocked,
                onSelect = { onShowMemberSelector(MemberRole.ESTIMATOR) },
                onClear = { onFormUpdate(form.copy(memberEstimator = null)) }
            )
        }

        item {
            BuildersRow(
                builders = form.memberBuilders,
                isReadOnly = membersLocked,
                onAdd = { onShowMemberSelector(MemberRole.BUILDERS) },
                onRemove = { contact ->
                    onFormUpdate(
                        form.copy(memberBuilders = form.memberBuilders.filter { it.id != contact.id })
                    )
                }
            )
        }

        if (showCustomerHint) {
            item {
                Text(
                    "Если заказчик не выбран — им станет создатель сметы",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun ProjectStep2(
    form: ProjectFormState,
    isReadOnly: Boolean,
    onEditMaterial: (Material) -> Unit,
    onEditWorkItem: (WorkItem) -> Unit,
    onDeleteMaterial: (Material) -> Unit,
    onDeleteWorkItem: (WorkItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Материалы", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        if (form.materials.isEmpty()) {
            item { Text("Нет добавленных материалов", color = Color.Gray) }
        } else {
            items(form.materials, key = { it.id }) { material ->
                MaterialItem(
                    material = material,
                    onEdit = if (isReadOnly) null else { { onEditMaterial(material) } },
                    onDelete = if (isReadOnly) null else { { onDeleteMaterial(material) } }
                )
            }
        }

        item {
            Text("Работы", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        if (form.workItems.isEmpty()) {
            item { Text("Нет добавленных работ", color = Color.Gray) }
        } else {
            items(form.workItems, key = { it.id }) { work ->
                WorkItemCard(
                    workItem = work,
                    onEdit = if (isReadOnly) null else { { onEditWorkItem(work) } },
                    onDelete = if (isReadOnly) null else { { onDeleteWorkItem(work) } }
                )
            }
        }

        item {
            TotalBudgetCard(total = form.grandTotal)
        }
    }
}

@Composable
private fun MemberRow(
    label: String,
    contact: Contact?,
    isReadOnly: Boolean,
    onSelect: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(label, fontSize = 12.sp, color = Color.Gray)

            if (contact != null) {
                Text(
                    text = contact.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
                if (contact.description.isNotBlank()) {
                    Text(
                        text = contact.description,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            } else {
                Text("Не выбран", color = Color.Gray)
            }
        }

        if (!isReadOnly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (contact != null) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Очистить",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                OutlinedButton(onClick = onSelect) {
                    Text(if (contact != null) "Изменить" else "Выбрать")
                }
            }
        }
    }
}

@Composable
private fun BuildersRow(
    builders: List<Contact>,
    isReadOnly: Boolean,
    onAdd: () -> Unit,
    onRemove: (Contact) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Строители", fontSize = 12.sp, color = Color.Gray)
            if (!isReadOnly) {
                OutlinedButton(onClick = onAdd) {
                    Text("Добавить")
                }
            }
        }

        if (builders.isEmpty()) {
            Text(
                "Нет строителей",
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            builders.forEach { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                        if (contact.description.isNotBlank()) {
                            Text(
                                text = contact.description,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    if (!isReadOnly) {
                        IconButton(onClick = { onRemove(contact) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}