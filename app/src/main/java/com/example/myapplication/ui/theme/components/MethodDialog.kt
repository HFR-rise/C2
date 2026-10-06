package com.example.myapplication.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.utils.PhoneUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TELEGRAM_VK_REGEX = Regex("^[A-Za-z0-9_-]{3,32}$")

private val EMAIL_REGEX = Regex(
    "^" +
            "(?=.{1,64}@)" +
            "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+" +
            "(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*" +
            "@" +
            "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+" +
            "[A-Za-z]{2,}" +
            "$"
)

private const val INVALID_INPUT_HINT_MS = 3_000L

enum class ContactMethodType(
    val displayName: String,
    val icon: Any,
    val needsAtSymbol: Boolean = false,
    val prefillAtSymbol: Boolean = false,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val validationRegex: String? = null,
    val placeholder: String = ""
) {
    PHONE(
        displayName = "Телефон",
        icon = Icons.Default.Phone,
        keyboardType = KeyboardType.Phone,
        placeholder = "только российские номера"
    ),
    TELEGRAM(
        displayName = "Telegram",
        icon = Icons.Default.Send,
        prefillAtSymbol = true,
        placeholder = "@username"
    ),
    VK(
        displayName = "VK",
        icon = "VK",
        prefillAtSymbol = true,
        placeholder = "@id... или @username"
    ),
    EMAIL(
        displayName = "Email",
        icon = Icons.Default.Email,
        needsAtSymbol = true,
        keyboardType = KeyboardType.Email,
        placeholder = "example@mail.com"
    ),
    CUSTOM(
        displayName = "Иное",
        icon = Icons.Default.Edit,
        placeholder = "Введите произвольный текст"
    )
}

private fun String?.toContactMethodType(): ContactMethodType {
    if (this == null) return ContactMethodType.PHONE
    val lower = lowercase()
    return when {
        lower.contains("телефон") || lower.contains("phone") -> ContactMethodType.PHONE
        lower.contains("telegram") -> ContactMethodType.TELEGRAM
        lower.contains("vk") -> ContactMethodType.VK
        lower.contains("email") || lower.contains("почта") -> ContactMethodType.EMAIL
        else -> ContactMethodType.CUSTOM
    }
}

private fun filterTelegramVkInput(input: String): String {
    return input.filter { c ->
        c == '_' || c == '-' ||
                (c.code < 128 && (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'))
    }
}

private fun filterEmailInput(input: String): String {
    return input.filter { c -> c.code in 33..126 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodDialog(
    method: ContactMethod?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val defaultType = method?.methodType.toContactMethodType()

    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(defaultType) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showInvalidInput by remember { mutableStateOf(false) }
    var invalidInputJob by remember { mutableStateOf<Job?>(null) }

    val invalidateInput: () -> Unit = remember {
        {
            invalidInputJob?.cancel()
            showInvalidInput = true
            invalidInputJob = scope.launch {
                delay(INVALID_INPUT_HINT_MS)
                showInvalidInput = false
            }
        }
    }
    val clearInvalidInput: () -> Unit = remember {
        {
            invalidInputJob?.cancel()
            invalidInputJob = null
            showInvalidInput = false
        }
    }

    var phoneValue by remember {
        mutableStateOf(if (defaultType == ContactMethodType.PHONE) method?.value ?: "" else "")
    }
    var telegramValue by remember {
        mutableStateOf(if (defaultType == ContactMethodType.TELEGRAM) method?.value ?: "" else "")
    }
    var vkValue by remember {
        mutableStateOf(if (defaultType == ContactMethodType.VK) method?.value ?: "" else "")
    }
    var emailValue by remember {
        mutableStateOf(if (defaultType == ContactMethodType.EMAIL) method?.value ?: "" else "")
    }
    var customValue by remember {
        mutableStateOf(if (defaultType == ContactMethodType.CUSTOM) method?.value ?: "" else "")
    }
    var customTypeName by remember {
        mutableStateOf(
            if (defaultType == ContactMethodType.CUSTOM && method?.methodType != "Иное") {
                method?.methodType ?: ""
            } else ""
        )
    }

    var phoneTextFieldValue by remember {
        mutableStateOf(
            if (defaultType == ContactMethodType.PHONE) {
                val formatted = PhoneUtils.format(method?.value ?: "")
                TextFieldValue(formatted, selection = TextRange(formatted.length))
            } else {
                TextFieldValue("")
            }
        )
    }
    var telegramTextFieldValue by remember { mutableStateOf(TextFieldValue(telegramValue)) }
    var vkTextFieldValue by remember { mutableStateOf(TextFieldValue(vkValue)) }
    var emailTextFieldValue by remember { mutableStateOf(TextFieldValue(emailValue)) }
    var customTextFieldValue by remember { mutableStateOf(TextFieldValue(customValue)) }

    val currentTextFieldValue = when (selectedType) {
        ContactMethodType.PHONE -> phoneTextFieldValue
        ContactMethodType.TELEGRAM -> telegramTextFieldValue
        ContactMethodType.VK -> vkTextFieldValue
        ContactMethodType.EMAIL -> emailTextFieldValue
        ContactMethodType.CUSTOM -> customTextFieldValue
    }

    fun updateCurrentValue(newValue: String, newTextFieldValue: TextFieldValue) {
        when (selectedType) {
            ContactMethodType.PHONE -> {
                phoneValue = newValue
                phoneTextFieldValue = newTextFieldValue
            }
            ContactMethodType.TELEGRAM -> {
                telegramValue = newValue
                telegramTextFieldValue = newTextFieldValue
            }
            ContactMethodType.VK -> {
                vkValue = newValue
                vkTextFieldValue = newTextFieldValue
            }
            ContactMethodType.EMAIL -> {
                emailValue = newValue
                emailTextFieldValue = newTextFieldValue
            }
            ContactMethodType.CUSTOM -> {
                customValue = newValue
                customTextFieldValue = newTextFieldValue
            }
        }
    }

    LaunchedEffect(selectedType) {
        clearInvalidInput()
        errorMessage = null

        when (selectedType) {
            ContactMethodType.TELEGRAM -> {
                if (telegramValue.isEmpty()) {
                    updateCurrentValue("@", TextFieldValue("@", selection = TextRange(1)))
                } else if (!telegramValue.startsWith("@")) {
                    val newVal = "@$telegramValue"
                    updateCurrentValue(newVal, TextFieldValue(newVal, selection = TextRange(newVal.length)))
                }
            }
            ContactMethodType.VK -> {
                if (vkValue.isEmpty()) {
                    updateCurrentValue("@", TextFieldValue("@", selection = TextRange(1)))
                } else if (!vkValue.startsWith("@")) {
                    val newVal = "@$vkValue"
                    updateCurrentValue(newVal, TextFieldValue(newVal, selection = TextRange(newVal.length)))
                }
            }
            else -> {}
        }
    }

    fun validate(): Boolean {
        errorMessage = null

        val value = when (selectedType) {
            ContactMethodType.PHONE -> phoneValue
            ContactMethodType.TELEGRAM -> telegramValue
            ContactMethodType.VK -> vkValue
            ContactMethodType.EMAIL -> emailValue
            ContactMethodType.CUSTOM -> customValue
        }

        if (value.isBlank()) {
            errorMessage = "Поле не может быть пустым"
            return false
        }

        when (selectedType) {
            ContactMethodType.PHONE -> {
                if (!PhoneUtils.isValid(value)) {
                    errorMessage = "Введите корректный российский номер телефона"
                    return false
                }
            }
            ContactMethodType.EMAIL -> {
                if (!EMAIL_REGEX.matches(value)) {
                    errorMessage = "Введите корректный email (латиница, цифры, . - _ +)"
                    return false
                }
            }
            ContactMethodType.TELEGRAM, ContactMethodType.VK -> {
                if (!value.startsWith("@")) {
                    errorMessage = "Никнейм должен начинаться с @"
                    return false
                }
                val nickname = value.substring(1)
                if (!TELEGRAM_VK_REGEX.matches(nickname)) {
                    errorMessage = "Никнейм: только латиница, цифры, _ и -, от 3 до 32 символов"
                    return false
                }
            }
            ContactMethodType.CUSTOM -> {
                if (customTypeName.isBlank()) {
                    errorMessage = "Введите название способа связи"
                    return false
                }
            }
        }
        return true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Способ связи") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Тип связи", fontWeight = FontWeight.Medium)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        ContactMethodType.PHONE,
                        ContactMethodType.TELEGRAM,
                        ContactMethodType.VK,
                        ContactMethodType.EMAIL
                    ).forEach { type ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedType = type
                                    errorMessage = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            when (val icon = type.icon) {
                                is String -> Text(
                                    text = icon,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                else -> Icon(
                                    icon as ImageVector,
                                    contentDescription = type.displayName,
                                    modifier = Modifier.size(36.dp),
                                    tint = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedType = ContactMethodType.CUSTOM
                            errorMessage = null
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedType == ContactMethodType.CUSTOM)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (selectedType == ContactMethodType.CUSTOM)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Иное (вольный ввод)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedType == ContactMethodType.CUSTOM)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (selectedType == ContactMethodType.CUSTOM) {
                    OutlinedTextField(
                        value = customTypeName,
                        onValueChange = { customTypeName = it },
                        label = { Text("Название способа связи") },
                        placeholder = { Text("Например: Discord, Skype...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = currentTextFieldValue,
                    onValueChange = { newValue ->
                        val newText = newValue.text
                        val cursorPos = newValue.selection.start

                        when (selectedType) {
                            ContactMethodType.PHONE -> {
                                val digits = newText.filter { it.isDigit() }.take(11)
                                val formatted = PhoneUtils.format(digits)

                                val digitsBeforeCursor = newText.take(cursorPos).count { it.isDigit() }

                                val totalDigitsBefore = when {
                                    digitsBeforeCursor == 0 -> 0
                                    !newText.startsWith("+7") -> digitsBeforeCursor + 1
                                    else -> digitsBeforeCursor
                                }

                                val newCursorPos = PhoneUtils.cursorPositionAfterDigit(formatted, totalDigitsBefore)

                                updateCurrentValue(
                                    formatted,
                                    TextFieldValue(formatted, selection = TextRange(newCursorPos))
                                )
                            }
                            ContactMethodType.TELEGRAM, ContactMethodType.VK -> {
                                val withoutAt = newText.replace("@", "")
                                val filtered = filterTelegramVkInput(withoutAt)
                                val result = "@$filtered"

                                val hadInvalid = withoutAt.length != filtered.length

                                if (hadInvalid) {
                                    invalidateInput()
                                } else {
                                    clearInvalidInput()
                                }

                                val newCursorPos = when {
                                    cursorPos <= 1 -> 1
                                    else -> cursorPos.coerceAtMost(result.length)
                                }

                                updateCurrentValue(
                                    result,
                                    TextFieldValue(result, selection = TextRange(newCursorPos))
                                )
                            }
                            ContactMethodType.EMAIL -> {
                                val filtered = filterEmailInput(newText)

                                val hadInvalid = newText.length != filtered.length

                                if (hadInvalid) {
                                    invalidateInput()
                                } else {
                                    clearInvalidInput()
                                }

                                val newCursorPos = cursorPos.coerceAtMost(filtered.length)
                                updateCurrentValue(
                                    filtered,
                                    TextFieldValue(filtered, selection = TextRange(newCursorPos))
                                )
                            }
                            else -> {
                                updateCurrentValue(
                                    newText,
                                    TextFieldValue(
                                        newText,
                                        selection = TextRange(cursorPos.coerceAtMost(newText.length))
                                    )
                                )
                            }
                        }
                    },
                    label = {
                        Text(
                            when (selectedType) {
                                ContactMethodType.PHONE -> "Телефон"
                                ContactMethodType.TELEGRAM -> "Telegram"
                                ContactMethodType.VK -> "VK"
                                ContactMethodType.EMAIL -> "Email"
                                ContactMethodType.CUSTOM -> customTypeName.ifBlank { "Значение" }
                            }
                        )
                    },
                    placeholder = { Text(selectedType.placeholder) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null || showInvalidInput,
                    supportingText = {
                        when {
                            errorMessage != null -> Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                            showInvalidInput -> Text("Неправильный ввод", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = selectedType.keyboardType),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        errorBorderColor = Color.Red,
                        errorLabelColor = MaterialTheme.colorScheme.error,
                        errorSupportingTextColor = MaterialTheme.colorScheme.error,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        errorContainerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (validate()) {
                        val typeName = if (selectedType == ContactMethodType.CUSTOM) {
                            customTypeName.ifBlank { "Иное" }
                        } else selectedType.displayName

                        val value = when (selectedType) {
                            ContactMethodType.PHONE -> phoneValue
                            ContactMethodType.TELEGRAM -> telegramValue
                            ContactMethodType.VK -> vkValue
                            ContactMethodType.EMAIL -> emailValue
                            ContactMethodType.CUSTOM -> customValue
                        }
                        onSave(typeName, value)
                        onDismiss()
                    }
                }
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