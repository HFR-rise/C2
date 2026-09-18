package com.example.myapplication.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
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
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.ui.components.ContactSelectorRowSimple
import com.example.myapplication.ui.components.MaterialItem
import com.example.myapplication.ui.components.TotalBudgetCard
import com.example.myapplication.ui.components.WorkItemCard

@Composable
fun ProjectStep1(
    form: ProjectFormState,
    isReadOnly: Boolean,
    customerMethods: List<ContactMethod>,
    foremanMethods: List<ContactMethod>,
    managerMethods: List<ContactMethod>,
    onFormUpdate: (ProjectFormState) -> Unit,
    onShowContactSelector: (ContactRole) -> Unit,
    onShowContactInfo: (ContactRole) -> Unit
) {
    val foremanHasPhone = foremanMethods.hasPhoneNumber()
    val managerHasPhone = managerMethods.hasPhoneNumber()

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
        item { Text("Данные заказчика", fontWeight = FontWeight.Medium) }
        item {
            ContactSection(
                contact = form.selectedCustomer,
                isReadOnly = isReadOnly,
                onShowSelector = { onShowContactSelector(ContactRole.CUSTOMER) },
                onClear = { onFormUpdate(form.copy(selectedCustomer = null)) },
                onShowInfo = { onShowContactInfo(ContactRole.CUSTOMER) }
            )
        }

        item { Divider() }
        item { Text("Данные прораба", fontWeight = FontWeight.Medium) }
        item {
            ContactSection(
                contact = form.selectedForeman,
                isReadOnly = isReadOnly,
                onShowSelector = { onShowContactSelector(ContactRole.FOREMAN) },
                onClear = {
                    onFormUpdate(form.copy(selectedForeman = null, includeForeman = false))
                },
                onShowInfo = { onShowContactInfo(ContactRole.FOREMAN) }
            )
            IncludeInProjectCheckbox(
                checked = form.includeForeman,
                enabled = !isReadOnly && form.selectedForeman != null && foremanHasPhone,
                isReadOnly = isReadOnly,
                hasContact = form.selectedForeman != null,
                hasPhoneNumber = foremanHasPhone,
                onCheckedChange = { onFormUpdate(form.copy(includeForeman = it)) }
            )
        }

        item { Divider() }
        item { Text("Данные менеджера", fontWeight = FontWeight.Medium) }
        item {
            ContactSection(
                contact = form.selectedManager,
                isReadOnly = isReadOnly,
                onShowSelector = { onShowContactSelector(ContactRole.MANAGER) },
                onClear = {
                    onFormUpdate(form.copy(selectedManager = null, includeManager = false))
                },
                onShowInfo = { onShowContactInfo(ContactRole.MANAGER) }
            )
            IncludeInProjectCheckbox(
                checked = form.includeManager,
                enabled = !isReadOnly && form.selectedManager != null && managerHasPhone,
                isReadOnly = isReadOnly,
                hasContact = form.selectedManager != null,
                hasPhoneNumber = managerHasPhone,
                onCheckedChange = { onFormUpdate(form.copy(includeManager = it)) }
            )
        }
    }
}

@Composable
fun ProjectStep2(
    form: ProjectFormState,
    isReadOnly: Boolean,
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
private fun ContactSection(
    contact: Contact?,
    isReadOnly: Boolean,
    onShowSelector: () -> Unit,
    onClear: () -> Unit,
    onShowInfo: () -> Unit
) {
    if (isReadOnly) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = contact != null) { onShowInfo() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (contact != null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(contact.name, fontWeight = FontWeight.Medium)
                    if (contact.description.isNotBlank()) {
                        Text(
                            text = contact.description,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                Text("Не выбран", color = Color.Gray)
            }
        }
    } else {
        ContactSelectorRowSimple(
            label = "",
            selectedContact = contact,
            onSelect = { },
            onClear = onClear,
            onShowSelector = onShowSelector
        )
    }
}

@Composable
private fun IncludeInProjectCheckbox(
    checked: Boolean,
    enabled: Boolean,
    isReadOnly: Boolean,
    hasContact: Boolean,
    hasPhoneNumber: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled
            )
            Text(
                "Включить в проект",
                color = if (enabled || isReadOnly) Color.Unspecified else Color.Gray,
                modifier = Modifier.clickable(enabled = enabled) {
                    onCheckedChange(!checked)
                }
            )
        }

        if (!isReadOnly && hasContact && !hasPhoneNumber) {
            Text(
                text = "Для этого контакта не указан номер телефона",
                fontSize = 11.sp,
                color = Color.Red,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }
    }
}

private fun List<ContactMethod>.hasPhoneNumber(): Boolean {
    return any { method ->
        method.methodType.contains("телефон", ignoreCase = true) ||
                method.methodType.contains("phone", ignoreCase = true)
    }
}