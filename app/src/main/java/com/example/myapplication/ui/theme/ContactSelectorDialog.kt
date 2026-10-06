package com.example.myapplication.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.ui.theme.components.ContactDetailsDialog
import com.example.myapplication.ui.theme.getFilterDisplayName
import com.example.myapplication.ui.theme.getFilterIcon
import com.example.myapplication.viewmodels.ContactCheckResult
import com.example.myapplication.viewmodels.ContactsViewModel
import com.example.myapplication.viewmodels.SearchFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val SNACKBAR_VISIBLE_MS = 3_000L

data class ContactSelectorConfig(
    val title: String = "Выберите контакт",
    val showSearch: Boolean = true,
    val showFilterMenu: Boolean = false,
    val showEdit: Boolean = false,
    val showInfo: Boolean = false,
    val showDuplicates: Boolean = false,
    val requirePhone: Boolean = false,
    val showCancelButton: Boolean = true,

    val validateContact: (suspend (Contact) -> ContactCheckResult)? = null,

    val validationMessage: (Contact, ContactCheckResult) -> String = { contact, result ->
        when (result) {
            is ContactCheckResult.NotRegistered ->
                "«${contact.name}» не зарегистрирован в приложении. " +
                        "Попросите его установить приложение и войти."
            is ContactCheckResult.Error -> result.message
            is ContactCheckResult.Exists -> ""
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSelectorDialog(
    config: ContactSelectorConfig,
    onDismiss: () -> Unit,
    onSelect: (Contact) -> Unit,
    onNoPhoneError: ((Contact) -> Unit)? = null,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val filteredContacts by viewModel.filteredContacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val duplicatesVersion by viewModel.duplicatesVersion.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var selectedContactForInfo by remember { mutableStateOf<Contact?>(null) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }

    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    val snackbarTimerJob = remember { mutableStateOf<Job?>(null) }

    var contactDetailsMethods by remember { mutableStateOf<List<ContactMethod>>(emptyList()) }
    var editMethods by remember { mutableStateOf<List<ContactMethod>>(emptyList()) }

    val showTimedSnackbar: (String) -> Unit = remember(snackbarHostState) {
        { message ->
            snackbarTimerJob.value?.cancel()

            snackbarTimerJob.value = scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()

                val showJob = launch {
                    snackbarHostState.showSnackbar(
                        message = message,
                        withDismissAction = false
                    )
                }

                delay(SNACKBAR_VISIBLE_MS)
                snackbarHostState.currentSnackbarData?.dismiss()
                showJob.cancel()
            }
        }
    }

    LaunchedEffect(selectedContactForInfo?.id) {
        val contactId = selectedContactForInfo?.id
        if (contactId == null) {
            contactDetailsMethods = emptyList()
            return@LaunchedEffect
        }
        viewModel.getContactMethods(contactId).collect { methods ->
            contactDetailsMethods = methods
        }
    }

    LaunchedEffect(editingContact?.id) {
        val contactId = editingContact?.id
        if (contactId == null) {
            editMethods = emptyList()
            return@LaunchedEffect
        }
        viewModel.getContactMethods(contactId).collect { methods ->
            editMethods = methods
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(16.dp)
                    .align(Alignment.Center),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SelectorTopBar(
                        config = config,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        currentFilter = currentFilter,
                        onSearchToggle = { isSearchActive = it },
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onClearSearch = viewModel::clearSearch,
                        onFilterClick = { showFilterMenu = true },
                        onDismiss = onDismiss
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredContacts, key = { "${it.id}_$duplicatesVersion" }) { contact ->
                            SelectableContactCard(
                                contact = contact,
                                config = config,
                                onClick = {
                                    scope.launch {
                                        if (config.requirePhone) {
                                            val methods = viewModel.getContactMethods(contact.id).first()
                                            val hasPhone = methods.any { it.isPhoneMethod() }
                                            if (!hasPhone) {
                                                showTimedSnackbar("У контакта нет номера телефона")
                                                onNoPhoneError?.invoke(contact)
                                                return@launch
                                            }
                                        }

                                        if (config.validateContact != null) {
                                            val result = try {
                                                config.validateContact.invoke(contact)
                                            } catch (e: Exception) {
                                                ContactCheckResult.Error(
                                                    "Ошибка проверки: ${e.message}"
                                                )
                                            }

                                            if (result !is ContactCheckResult.Exists) {
                                                showTimedSnackbar(
                                                    config.validationMessage(contact, result)
                                                )
                                                return@launch
                                            }
                                        }

                                        onSelect(contact)
                                    }
                                },
                                onEdit = if (config.showEdit) {
                                    { editingContact = contact }
                                } else null,
                                onInfo = if (config.showInfo) {
                                    { selectedContactForInfo = contact }
                                } else null,
                                onDuplicateClick = if (config.showDuplicates) {
                                    {
                                        viewModel.getDuplicateContacts(contact.id).firstOrNull()?.let { dupId ->
                                            viewModel.contacts.value.find { it.id == dupId }?.let {
                                                selectedContactForInfo = it
                                            }
                                        }
                                    }
                                } else null,
                                hasDuplicate = config.showDuplicates && viewModel.hasDuplicates(contact.id)
                            )
                        }

                        if (filteredContacts.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = if (searchQuery.isNotBlank()) Icons.Default.SearchOff
                                    else Icons.Default.Person,
                                    title = if (searchQuery.isNotBlank()) "Ничего не найдено"
                                    else "Нет контактов",
                                    subtitle = if (searchQuery.isNotBlank()) "По запросу \"$searchQuery\""
                                    else "Добавьте контакты в разделе Контакты"
                                )
                            }
                        }
                    }

                    if (config.showCancelButton) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("Отмена")
                        }
                    }
                }
            }

            CustomSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
            )
        }
    }

    if (showFilterMenu) {
        SelectorFilterDialog(
            currentFilter = currentFilter,
            onFilterSelected = viewModel::updateSearchFilter,
            onDismiss = { showFilterMenu = false }
        )
    }

    selectedContactForInfo?.let { contact ->
        ContactDetailsDialog(
            contact = contact,
            methods = contactDetailsMethods,
            onDismiss = { selectedContactForInfo = null }
        )
    }

    editingContact?.let { contact ->
        ContactEditDialog(
            contact = contact,
            methods = editMethods,
            onDismiss = { editingContact = null },
            onSave = { name, description, added, updated, deleted ->
                viewModel.updateContact(contact.copy(name = name, description = description))
                added.forEach { viewModel.addContactMethod(contact.id, it.methodType, it.value) }
                updated.forEach { viewModel.updateContactMethod(it) }
                deleted.forEach { viewModel.deleteContactMethod(it) }
                editingContact = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorTopBar(
    config: ContactSelectorConfig,
    isSearchActive: Boolean,
    searchQuery: String,
    currentFilter: SearchFilter,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onFilterClick: () -> Unit,
    onDismiss: () -> Unit
) {
    TopAppBar(
        title = {
            if (isSearchActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        onSearchToggle(false)
                        onClearSearch()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(if (config.showFilterMenu) getFilterDisplayName(currentFilter) else "Поиск...")
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    if (config.showFilterMenu) {
                        IconButton(onClick = onFilterClick) {
                            Icon(getFilterIcon(currentFilter), contentDescription = "Фильтр")
                        }
                    }
                }
            } else {
                Text(config.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        navigationIcon = {
            if (!isSearchActive) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            }
        },
        actions = {
            if (!isSearchActive && config.showSearch) {
                IconButton(onClick = { onSearchToggle(true) }) {
                    Icon(Icons.Default.Search, contentDescription = "Поиск")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

@Composable
private fun SelectorFilterDialog(
    currentFilter: SearchFilter,
    onFilterSelected: (SearchFilter) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите тип поиска") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchFilter.values().forEach { filter ->
                    FilterOption(
                        title = getFilterDisplayName(filter),
                        icon = getFilterIcon(filter),
                        isSelected = currentFilter == filter
                    ) {
                        onFilterSelected(filter)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun SelectableContactCard(
    contact: Contact,
    config: ContactSelectorConfig,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
    onInfo: (() -> Unit)?,
    onDuplicateClick: (() -> Unit)?,
    hasDuplicate: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                Text(
                    text = contact.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (contact.description.isNotBlank()) {
                    Text(
                        text = contact.description,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
            Row {
                if (hasDuplicate && onDuplicateClick != null) {
                    IconButton(onClick = onDuplicateClick) {
                        Icon(Icons.Default.Warning, "Дубликат", tint = Color.Red)
                    }
                }
                if (onInfo != null) {
                    IconButton(onClick = onInfo) {
                        Icon(Icons.Default.Info, "Информация", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Редактировать", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private fun ContactMethod.isPhoneMethod(): Boolean {
    val type = methodType.lowercase()
    return type.contains("телефон") || type.contains("phone")
}