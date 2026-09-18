package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.models.ObjectModel

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Удалить",
    dismissText: String = "Отмена",
    isDestructive: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = if (isDestructive) Color.Red else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

@Composable
fun CreateTypeDialog(
    onDismiss: () -> Unit,
    onCreateObject: () -> Unit,
    onCreateProject: () -> Unit,
    isInsideObject: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать") },
        text = { Text("Что вы хотите создать?") },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCreateObject,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Объект")
                    }
                    Button(
                        onClick = onCreateProject,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Смета")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отмена")
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
fun CreateObjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var house by remember { mutableStateOf("") }
    var building by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var streetError by remember { mutableStateOf<String?>(null) }
    var houseError by remember { mutableStateOf<String?>(null) }
    var buildingError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать объект") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название объекта*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = name.isBlank()
                )

                OutlinedTextField(
                    value = street,
                    onValueChange = {
                        street = it
                        streetError = validateStreet(it)
                    },
                    label = { Text("Улица") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = streetError != null,
                    supportingText = streetError?.let { { Text(it, color = Color.Red) } }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = house,
                        onValueChange = {
                            house = it
                            houseError = validateHouse(it)
                        },
                        label = { Text("Дом") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = houseError != null,
                        supportingText = houseError?.let { { Text(it, color = Color.Red) } }
                    )

                    OutlinedTextField(
                        value = building,
                        onValueChange = {
                            building = it
                            buildingError = validateBuilding(it)
                        },
                        label = { Text("Корпус") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = buildingError != null,
                        supportingText = buildingError?.let { { Text(it, color = Color.Red) } }
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                AddressPreview(street, house, building)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name, street, house, building, description)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun EditObjectDialog(
    obj: ObjectModel,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(obj.name) }
    var street by remember { mutableStateOf(obj.street) }
    var house by remember { mutableStateOf(obj.house) }
    var building by remember { mutableStateOf(obj.building) }
    var description by remember { mutableStateOf(obj.description) }

    var streetError by remember { mutableStateOf<String?>(null) }
    var houseError by remember { mutableStateOf<String?>(null) }
    var buildingError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать объект") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название объекта*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = name.isBlank()
                )

                OutlinedTextField(
                    value = street,
                    onValueChange = {
                        street = it
                        streetError = validateStreet(it)
                    },
                    label = { Text("Улица") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = streetError != null,
                    supportingText = streetError?.let { { Text(it, color = Color.Red) } }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = house,
                        onValueChange = {
                            house = it
                            houseError = validateHouse(it)
                        },
                        label = { Text("Дом") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = houseError != null,
                        supportingText = houseError?.let { { Text(it, color = Color.Red) } }
                    )

                    OutlinedTextField(
                        value = building,
                        onValueChange = {
                            building = it
                            buildingError = validateBuilding(it)
                        },
                        label = { Text("Корпус") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = buildingError != null,
                        supportingText = buildingError?.let { { Text(it, color = Color.Red) } }
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                AddressPreview(street, house, building)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val isStreetValid = street.isBlank() || validateStreet(street) == null
                    val isHouseValid = house.isBlank() || validateHouse(house) == null
                    val isBuildingValid = building.isBlank() || validateBuilding(building) == null

                    if (name.isNotBlank() && isStreetValid && isHouseValid && isBuildingValid) {
                        onSave(name, street, house, building, description)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun AddressPreview(street: String, house: String, building: String) {
    if (street.isNotBlank() || house.isNotBlank() || building.isNotBlank()) {
        val preview = buildString {
            if (street.isNotBlank()) append("ул. $street")
            if (house.isNotBlank()) {
                if (isNotEmpty()) append(", ")
                append("д. $house")
            }
            if (building.isNotBlank()) {
                if (isNotEmpty()) append(", ")
                append("к. $building")
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                text = "Предпросмотр: $preview",
                modifier = Modifier.padding(12.dp),
                fontSize = 12.sp
            )
        }
    }
}

private fun validateStreet(value: String): String? {
    if (value.isBlank()) return null
    if (value.any { it.isDigit() }) return "Улица не может содержать цифры"
    return null
}

private fun validateHouse(value: String): String? {
    if (value.isBlank()) return null
    if (!value.any { it.isDigit() }) return "Дом должен содержать цифры"

    val firstDigitIndex = value.indexOfFirst { it.isDigit() }
    val firstLetterIndex = value.indexOfFirst { it.isLetter() }

    if (firstLetterIndex != -1 && firstLetterIndex < firstDigitIndex) {
        return "Буквы не могут идти перед цифрами"
    }

    val lettersAfterDigits = value.substring(firstDigitIndex).count { it.isLetter() }
    if (lettersAfterDigits > 1) return "После цифр может быть только одна буква"
    if (lettersAfterDigits == 1 && !value.last().isLetter()) {
        return "Буква должна быть в конце номера дома"
    }
    return null
}

private fun validateBuilding(value: String): String? {
    if (value.isBlank()) return null
    if (!value.any { it.isDigit() }) return "Корпус должен содержать цифры"

    val firstDigitIndex = value.indexOfFirst { it.isDigit() }
    val lastLetterIndex = value.indexOfLast { it.isLetter() }

    if (lastLetterIndex != -1 && lastLetterIndex > firstDigitIndex) {
        return "Буквы могут быть только в начале"
    }
    return null
}