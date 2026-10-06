package com.example.myapplication.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myapplication.utils.PhoneUtils
import com.example.myapplication.viewmodels.AuthViewModel
import kotlinx.coroutines.delay

private const val RETRY_BUTTON_DELAY_MS = 1000L
private const val MIN_CODE_LENGTH = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    navController: NavController,
    viewModel: AuthViewModel
) {
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val verificationCode by viewModel.verificationCode.collectAsState()
    val isCodeSent by viewModel.isCodeSent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val showAccountInUseError by viewModel.showAccountInUseError.collectAsState()

    var showRetryButton by remember { mutableStateOf(false) }

    LaunchedEffect(showAccountInUseError) {
        showRetryButton = false
        if (showAccountInUseError) {
            delay(RETRY_BUTTON_DELAY_MS)
            showRetryButton = true
        }
    }

    val resetToPhoneInput = remember(viewModel) {
        {
            viewModel.updatePhoneNumber("")
            viewModel.updateVerificationCode("")
            viewModel.resetToPhoneInput()
            viewModel.clearAccountInUseError()
            showRetryButton = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Вход в приложение",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            AppLogo()

            Spacer(Modifier.height(48.dp))

            if (!isCodeSent) {
                PhoneInputStep(
                    phoneNumber = phoneNumber,
                    errorMessage = errorMessage,
                    showAccountInUseError = showAccountInUseError,
                    isLoading = isLoading,
                    onPhoneChange = viewModel::updatePhoneNumber,
                    onSendCode = viewModel::sendCode
                )
            } else {
                CodeInputStep(
                    phoneNumber = phoneNumber,
                    verificationCode = verificationCode,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    showAccountInUseError = showAccountInUseError,
                    showRetryButton = showRetryButton,
                    onChangePhone = resetToPhoneInput,
                    onCodeChange = {
                        viewModel.updateVerificationCode(it)
                        if (showAccountInUseError) viewModel.clearAccountInUseError()
                    },
                    onVerifyCode = {
                        viewModel.verifyCode()
                        showRetryButton = false
                    },
                    onRetry = {
                        viewModel.retryWithSamePhone()
                        showRetryButton = false
                    }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AppLogo() {
    Icon(
        imageVector = Icons.Default.Phone,
        contentDescription = null,
        modifier = Modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Сметы",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneInputStep(
    phoneNumber: String,
    errorMessage: String?,
    showAccountInUseError: Boolean,
    isLoading: Boolean,
    onPhoneChange: (String) -> Unit,
    onSendCode: () -> Unit
) {
    val showError = errorMessage != null && !showAccountInUseError

    var phoneTextFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = phoneNumber,
                selection = TextRange(phoneNumber.length)
            )
        )
    }

    LaunchedEffect(phoneNumber) {
        if (phoneNumber != phoneTextFieldValue.text) {
            phoneTextFieldValue = TextFieldValue(
                text = phoneNumber,
                selection = TextRange(phoneNumber.length)
            )
        }
    }

    OutlinedTextField(
        value = phoneTextFieldValue,
        onValueChange = { newValue ->
            val newText = newValue.text
            val cursorPos = newValue.selection.start

            val digits = newText.filter { it.isDigit() }.take(11)
            val formatted = PhoneUtils.format(digits)

            val digitsBeforeCursor = newText.take(cursorPos).count { it.isDigit() }

            val totalDigitsBefore = when {
                digitsBeforeCursor == 0 -> 0
                !newText.startsWith("+7") -> digitsBeforeCursor + 1
                else -> digitsBeforeCursor
            }

            val newCursorPos = PhoneUtils.cursorPositionAfterDigit(formatted, totalDigitsBefore)

            val newFieldValue = TextFieldValue(
                text = formatted,
                selection = TextRange(newCursorPos)
            )
            phoneTextFieldValue = newFieldValue

            onPhoneChange(formatted)
        },
        label = { Text("Номер телефона") },
        placeholder = { Text("+7 (XXX) XXX-XX-XX") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = showError
    )

    if (showError) {
        Text(
            text = errorMessage.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onSendCode,
        modifier = Modifier.fillMaxWidth(),
        enabled = phoneNumber.isNotBlank() && !isLoading && !showAccountInUseError
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White
            )
        } else {
            Text("Получить код")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeInputStep(
    phoneNumber: String,
    verificationCode: String,
    isLoading: Boolean,
    errorMessage: String?,
    showAccountInUseError: Boolean,
    showRetryButton: Boolean,
    onChangePhone: () -> Unit,
    onCodeChange: (String) -> Unit,
    onVerifyCode: () -> Unit,
    onRetry: () -> Unit
) {
    PhoneSentCard(
        phoneNumber = phoneNumber,
        onChangePhone = onChangePhone
    )

    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = verificationCode,
        onValueChange = onCodeChange,
        label = { Text("Код подтверждения") },
        placeholder = { Text("123456") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = errorMessage != null && !showAccountInUseError,
        enabled = !showAccountInUseError
    )

    Spacer(Modifier.height(8.dp))

    when {
        showAccountInUseError -> AccountInUseCard(
            message = errorMessage ?: "Аккаунт уже используется на другом устройстве",
            showRetryButton = showRetryButton,
            onRetry = onRetry,
            onChangePhone = onChangePhone
        )
        errorMessage != null -> ErrorCard(errorMessage)
    }

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = onVerifyCode,
        modifier = Modifier.fillMaxWidth(),
        enabled = verificationCode.length >= MIN_CODE_LENGTH && !isLoading && !showAccountInUseError,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (showAccountInUseError)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.primary
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White
            )
        } else {
            Text("Войти")
        }
    }

    if (!showAccountInUseError) {
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onChangePhone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Изменить номер телефона", fontSize = 12.sp)
        }
    }
}

@Composable
private fun PhoneSentCard(
    phoneNumber: String,
    onChangePhone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Код отправлен на номер",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = phoneNumber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(onClick = onChangePhone) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = "Изменить номер",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AccountInUseCard(
    message: String,
    showRetryButton: Boolean,
    onRetry: () -> Unit,
    onChangePhone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Вы не можете войти, так как аккаунт уже активен на другом устройстве. " +
                        "Если это были вы, пожалуйста, выйдите из аккаунта на другом устройстве.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showRetryButton) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Попробовать снова")
                    }
                }
                Button(
                    onClick = onChangePhone,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Ввести другой номер")
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}