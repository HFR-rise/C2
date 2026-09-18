package com.example.myapplication.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.ui.components.*
import com.example.myapplication.ui.theme.components.ContactCard
import com.example.myapplication.ui.theme.components.ContactDetailsDialog
import com.example.myapplication.viewmodels.ContactsViewModel
import com.example.myapplication.viewmodels.SearchFilter
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    navController: NavController,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val filteredContacts by viewModel.filteredContacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val editingContact by viewModel.editingContact.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val duplicatesVersion by viewModel.duplicatesVersion.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var selectedContactForDetails by remember { mutableStateOf<Contact?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }

    var contactDetailsMethods by remember { mutableStateOf<List<ContactMethod>>(emptyList()) }
    var editDialogMethods by remember { mutableStateOf<List<ContactMethod>>(emptyList()) }

    LaunchedEffect(selectedContactForDetails) {
        contactDetailsMethods = selectedContactForDetails?.let { contact ->
            viewModel.getContactMethods(contact.id).first()
        } ?: emptyList()
    }

    LaunchedEffect(editingContact) {
        editDialogMethods = editingContact?.let { contact ->
            viewModel.getContactMethods(contact.id).first()
        } ?: emptyList()
    }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showAddDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Add, "Добавить контакт")
            }
        },
        topBar = {
            ContactsTopBar(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                currentFilter = currentFilter,
                onSearchToggle = { isSearchActive = it },
                onSearchQueryChange = viewModel::updateSearchQuery,
                onFilterClick = { showFilterMenu = true },
                onClose = {
                    isSearchActive = false
                    viewModel.clearSearch()
                }
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = viewModel::refreshData,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                GradientDivider()

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredContacts,
                        key = { "${it.id}_$duplicatesVersion" }
                    ) { contact ->
                        ContactCard(
                            contact = contact,
                            onContactClick = { selectedContactForDetails = contact },
                            onEdit = { viewModel.startEditing(contact) },
                            onDelete = { contactToDelete = contact },
                            onDuplicateClick = {
                                viewModel.getDuplicateContacts(contact.id).firstOrNull()
                                    ?.let { dupId ->
                                        viewModel.contacts.value.find { it.id == dupId }?.let {
                                            selectedContactForDetails = it
                                        }
                                    }
                            },
                            hasDuplicate = viewModel.hasDuplicates(contact.id)
                        )
                    }

                    if (filteredContacts.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            EmptyState(
                                icon = Icons.Default.SearchOff,
                                title = "Ничего не найдено",
                                subtitle = "По запросу \"$searchQuery\""
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterMenu) {
        ContactsFilterDialog(
            currentFilter = currentFilter,
            onFilterSelected = viewModel::updateSearchFilter,
            onDismiss = { showFilterMenu = false }
        )
    }

    if (contactToDelete != null) {
        ConfirmationDialog(
            title = "Удалить контакт",
            message = "Вы уверены, что хотите удалить контакт \"${contactToDelete!!.name}\"?",
            onConfirm = {
                viewModel.deleteContact(contactToDelete!!)
                contactToDelete = null
            },
            onDismiss = { contactToDelete = null }
        )
    }

    selectedContactForDetails?.let { contact ->
        ContactDetailsDialog(
            contact = contact,
            methods = contactDetailsMethods,
            onDismiss = {
                selectedContactForDetails = null
                contactDetailsMethods = emptyList()
            }
        )
    }

    if (showAddDialog || editingContact != null) {
        ContactEditDialog(
            contact = editingContact,
            methods = editDialogMethods,
            onDismiss = {
                viewModel.hideAddDialog()
                viewModel.clearEditing()
                editDialogMethods = emptyList()
            },
            onSave = { name, description, added, updated, deleted ->
                if (editingContact == null) {
                    viewModel.addContactWithMethods(name, description, added)
                } else {
                    viewModel.updateContact(editingContact!!.copy(name = name, description = description))
                    added.forEach { viewModel.addContactMethod(editingContact!!.id, it.methodType, it.value) }
                    updated.forEach { viewModel.updateContactMethod(it) }
                    deleted.forEach { viewModel.deleteContactMethod(it) }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    currentFilter: SearchFilter,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            if (isSearchActive) {
                AppSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = getFilterDisplayName(currentFilter),
                    filterIcon = getFilterIcon(currentFilter),
                    onFilterClick = onFilterClick,
                    onClose = onClose
                )
            } else {
                Text("Контакты", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        actions = {
            if (!isSearchActive) {
                IconButton(onClick = { onSearchToggle(true) }) {
                    Icon(Icons.Default.Search, "Поиск")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

@Composable
private fun ContactsFilterDialog(
    currentFilter: SearchFilter,
    onFilterSelected: (SearchFilter) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите тип поиска") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Основные")
                listOf(SearchFilter.BY_NAME, SearchFilter.BY_DESCRIPTION).forEach { filter ->
                    FilterOption(
                        title = getFilterDisplayName(filter),
                        icon = getFilterIcon(filter),
                        isSelected = currentFilter == filter
                    ) {
                        onFilterSelected(filter)
                        onDismiss()
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(title = "Способы связи")
                listOf(
                    SearchFilter.BY_PHONE, SearchFilter.BY_TELEGRAM,
                    SearchFilter.BY_VK, SearchFilter.BY_EMAIL, SearchFilter.BY_OTHER
                ).forEach { filter ->
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

fun getFilterIcon(filter: SearchFilter): androidx.compose.ui.graphics.vector.ImageVector {
    return when (filter) {
        SearchFilter.BY_NAME -> Icons.Default.Person
        SearchFilter.BY_DESCRIPTION -> Icons.Default.Description
        SearchFilter.BY_PHONE -> Icons.Default.Phone
        SearchFilter.BY_TELEGRAM -> Icons.Default.Send
        SearchFilter.BY_VK -> Icons.Default.People
        SearchFilter.BY_EMAIL -> Icons.Default.Email
        SearchFilter.BY_OTHER -> Icons.Default.Link
    }
}

fun getFilterDisplayName(filter: SearchFilter): String {
    return when (filter) {
        SearchFilter.BY_NAME -> "По имени"
        SearchFilter.BY_DESCRIPTION -> "По описанию"
        SearchFilter.BY_PHONE -> "Телефон"
        SearchFilter.BY_TELEGRAM -> "Telegram"
        SearchFilter.BY_VK -> "VK"
        SearchFilter.BY_EMAIL -> "Email"
        SearchFilter.BY_OTHER -> "Другой способ"
    }
}