package com.example.myapplication.ui.theme

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.ui.components.AddMaterialDialogSimple
import com.example.myapplication.ui.components.AddWorkDialogSimple
import com.example.myapplication.ui.components.ContactSelectorConfig
import com.example.myapplication.ui.components.ContactSelectorDialog
import com.example.myapplication.ui.theme.components.ContactDetailsDialog
import com.example.myapplication.viewmodels.ContactsViewModel
import com.example.myapplication.viewmodels.ProjectsViewModel
import kotlinx.coroutines.flow.first

enum class ProjectScreenMode {
    CREATE, EDIT, VIEW;

    val isReadOnly: Boolean get() = this == VIEW

    val title: String
        get() = when (this) {
            CREATE -> "Создать смету"
            EDIT -> "Редактировать смету"
            VIEW -> "Просмотр сметы"
        }

    val bottomButtonText: String
        get() = when (this) {
            CREATE -> "Создать смету"
            EDIT -> "Сохранить изменения"
            VIEW -> "Закрыть"
        }
}

enum class ContactRole(val selectorTitle: String) {
    CUSTOMER("Выберите заказчика"),
    FOREMAN("Выберите прораба"),
    MANAGER("Выберите менеджера")
}

data class ProjectFormState(
    val name: String = "",
    val description: String = "",
    val selectedCustomer: Contact? = null,
    val selectedForeman: Contact? = null,
    val selectedManager: Contact? = null,
    val includeForeman: Boolean = false,
    val includeManager: Boolean = false,
    val materials: List<Material> = emptyList(),
    val workItems: List<WorkItem> = emptyList()
) {
    val totalMaterialCost: Double get() = materials.sumOf { it.quantity * it.unitPrice }
    val totalWorkCost: Double get() = workItems.sumOf { it.laborHours * it.hourlyRate + it.materialCost }
    val grandTotal: Double get() = totalMaterialCost + totalWorkCost
    val isValid: Boolean get() = name.isNotBlank()

    fun contactFor(role: ContactRole): Contact? = when (role) {
        ContactRole.CUSTOMER -> selectedCustomer
        ContactRole.FOREMAN -> selectedForeman
        ContactRole.MANAGER -> selectedManager
    }

    fun withContact(role: ContactRole, contact: Contact?): ProjectFormState = when (role) {
        ContactRole.CUSTOMER -> copy(selectedCustomer = contact)
        ContactRole.FOREMAN -> copy(selectedForeman = contact)
        ContactRole.MANAGER -> copy(selectedManager = contact)
    }
}

private class CreateProjectUiState {
    var form by mutableStateOf(ProjectFormState())
    var currentStep by mutableStateOf(1)
    var isInitialLoading by mutableStateOf(false)
    var showContactSelectorFor by mutableStateOf<ContactRole?>(null)
    var showAddMaterialDialog by mutableStateOf(false)
    var showAddWorkDialog by mutableStateOf(false)
    var showContactInfoFor by mutableStateOf<ContactRole?>(null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    navController: NavController,
    mode: ProjectScreenMode,
    objectId: String? = null,
    projectId: String? = null,
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val contactsViewModel: ContactsViewModel = hiltViewModel()

    val uiState = remember { CreateProjectUiState() }
    val isReadOnly = mode.isReadOnly

    val methodsByRole = remember { mutableStateOf(ContactMethodsHolder()) }

    val allContacts by viewModel.contacts.collectAsState()

    LaunchedEffect(projectId, mode, allContacts) {
        if (mode == ProjectScreenMode.CREATE || projectId == null) {
            uiState.isInitialLoading = false
            return@LaunchedEffect
        }

        val needsInitialLoad = uiState.form.name.isBlank() && uiState.form.materials.isEmpty()

        if (needsInitialLoad) {
            uiState.isInitialLoading = true
        }

        val loaded = loadProjectData(
            projectId = projectId,
            viewModel = viewModel,
            allContacts = allContacts
        )

        if (loaded != null) {
            uiState.form = mergeLoadedProject(
                current = uiState.form,
                loaded = loaded,
                allContacts = allContacts
            )
        }

        uiState.isInitialLoading = false
    }

    LaunchedEffect(uiState.form.selectedCustomer?.id) {
        methodsByRole.value = methodsByRole.value.copy(
            customer = loadContactMethods(contactsViewModel, uiState.form.selectedCustomer?.id)
        )
    }
    LaunchedEffect(uiState.form.selectedForeman?.id) {
        methodsByRole.value = methodsByRole.value.copy(
            foreman = loadContactMethods(contactsViewModel, uiState.form.selectedForeman?.id)
        )
    }
    LaunchedEffect(uiState.form.selectedManager?.id) {
        methodsByRole.value = methodsByRole.value.copy(
            manager = loadContactMethods(contactsViewModel, uiState.form.selectedManager?.id)
        )
    }

    Scaffold(
        topBar = {
            ProjectTopBar(
                mode = mode,
                currentStep = uiState.currentStep,
                onBackClick = {
                    if (uiState.currentStep == 2) uiState.currentStep = 1
                    else navController.navigateUp()
                }
            )
        },
        floatingActionButton = {
            if (!isReadOnly && uiState.currentStep == 2) {
                AddItemFab(
                    onAddMaterial = { uiState.showAddMaterialDialog = true },
                    onAddWork = { uiState.showAddWorkDialog = true }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            GradientDivider()

            if (uiState.isInitialLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        when (uiState.currentStep) {
                            1 -> ProjectStep1(
                                form = uiState.form,
                                isReadOnly = isReadOnly,
                                customerMethods = methodsByRole.value.customer,
                                foremanMethods = methodsByRole.value.foreman,
                                managerMethods = methodsByRole.value.manager,
                                onFormUpdate = { uiState.form = it },
                                onShowContactSelector = { uiState.showContactSelectorFor = it },
                                onShowContactInfo = { uiState.showContactInfoFor = it }
                            )
                            2 -> ProjectStep2(
                                form = uiState.form,
                                isReadOnly = isReadOnly,
                                onDeleteMaterial = { material ->
                                    uiState.form = uiState.form.copy(
                                        materials = uiState.form.materials.filter { it.id != material.id }
                                    )
                                },
                                onDeleteWorkItem = { work ->
                                    uiState.form = uiState.form.copy(
                                        workItems = uiState.form.workItems.filter { it.id != work.id }
                                    )
                                }
                            )
                        }
                    }

                    ProjectBottomButton(
                        mode = mode,
                        currentStep = uiState.currentStep,
                        form = uiState.form,
                        objectId = objectId,
                        projectId = projectId,
                        viewModel = viewModel,
                        onNext = { uiState.currentStep = 2 },
                        onComplete = { navController.navigateUp() }
                    )
                }
            }
        }
    }

    uiState.showContactSelectorFor?.let { role ->
        ContactSelectorDialog(
            config = ContactSelectorConfig(
                title = role.selectorTitle,
                showFilterMenu = true,
                showEdit = true,
                showInfo = true,
                showDuplicates = true
            ),
            onDismiss = { uiState.showContactSelectorFor = null },
            onSelect = { contact ->
                uiState.form = uiState.form.withContact(role, contact)
                uiState.showContactSelectorFor = null
            }
        )
    }

    if (uiState.showAddMaterialDialog) {
        AddMaterialDialogSimple(
            onDismiss = { uiState.showAddMaterialDialog = false },
            onAdd = { name, quantity, unit, price ->
                uiState.form = uiState.form.copy(
                    materials = uiState.form.materials + Material(
                        projectId = projectId.orEmpty(),
                        name = name,
                        quantity = quantity,
                        unit = unit,
                        unitPrice = price
                    )
                )
                uiState.showAddMaterialDialog = false
            }
        )
    }

    if (uiState.showAddWorkDialog) {
        AddWorkDialogSimple(
            onDismiss = { uiState.showAddWorkDialog = false },
            onAdd = { name, hours, rate, materialCost ->
                uiState.form = uiState.form.copy(
                    workItems = uiState.form.workItems + WorkItem(
                        projectId = projectId.orEmpty(),
                        name = name,
                        laborHours = hours,
                        hourlyRate = rate,
                        materialCost = materialCost
                    )
                )
                uiState.showAddWorkDialog = false
            }
        )
    }

    uiState.showContactInfoFor?.let { role ->
        val contact = uiState.form.contactFor(role)
        val methods = methodsByRole.value.forRole(role)
        contact?.let {
            ContactDetailsDialog(
                contact = it,
                methods = methods,
                onDismiss = { uiState.showContactInfoFor = null }
            )
        }
    }
}

private data class ContactMethodsHolder(
    val customer: List<ContactMethod> = emptyList(),
    val foreman: List<ContactMethod> = emptyList(),
    val manager: List<ContactMethod> = emptyList()
) {
    fun forRole(role: ContactRole): List<ContactMethod> = when (role) {
        ContactRole.CUSTOMER -> customer
        ContactRole.FOREMAN -> foreman
        ContactRole.MANAGER -> manager
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectTopBar(
    mode: ProjectScreenMode,
    currentStep: Int,
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "${mode.title} ($currentStep/2)",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White
        )
    )
}

@Composable
private fun AddItemFab(
    onAddMaterial: () -> Unit,
    onAddWork: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        FloatingActionButton(
            onClick = onAddMaterial,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Default.Category, contentDescription = "Добавить материал")
        }
        FloatingActionButton(
            onClick = onAddWork,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Default.Build, contentDescription = "Добавить работу")
        }
    }
}

@Composable
private fun ProjectBottomButton(
    mode: ProjectScreenMode,
    currentStep: Int,
    form: ProjectFormState,
    objectId: String?,
    projectId: String?,
    viewModel: ProjectsViewModel,
    onNext: () -> Unit,
    onComplete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        tonalElevation = 3.dp
    ) {
        if (currentStep == 1) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = form.name.isNotBlank() || mode.isReadOnly
            ) {
                Text("Далее")
            }
        } else {
            Button(
                onClick = {
                    when (mode) {
                        ProjectScreenMode.CREATE -> {
                            if (form.isValid) {
                                viewModel.createProjectWithMaterialsAndWorks(
                                    name = form.name,
                                    description = form.description,
                                    objectId = normalizeObjectId(objectId),
                                    materials = form.materials,
                                    workItems = form.workItems,
                                    customerContactId = form.selectedCustomer?.id,
                                    foremanContactId = form.selectedForeman?.id,
                                    managerContactId = form.selectedManager?.id,
                                    includeForeman = form.includeForeman,
                                    includeManager = form.includeManager
                                )
                                onComplete()
                            }
                        }
                        ProjectScreenMode.EDIT -> {
                            if (projectId != null && form.isValid) {
                                viewModel.updateProject(
                                    projectId = projectId,
                                    name = form.name,
                                    description = form.description,
                                    materials = form.materials,
                                    workItems = form.workItems,
                                    customerContactId = form.selectedCustomer?.id,
                                    foremanContactId = form.selectedForeman?.id,
                                    managerContactId = form.selectedManager?.id,
                                    includeForeman = form.includeForeman,
                                    includeManager = form.includeManager
                                )
                                onComplete()
                            }
                        }
                        ProjectScreenMode.VIEW -> onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = mode.isReadOnly || form.isValid
            ) {
                Text(mode.bottomButtonText)
            }
        }
    }
}

private suspend fun loadProjectData(
    projectId: String,
    viewModel: ProjectsViewModel,
    allContacts: List<Contact>
): ProjectFormState? {
    return try {
        val project = viewModel.getProjectByIdAsync(projectId) ?: return null
        val materials = viewModel.getMaterialsForProject(projectId)
        val workItems = viewModel.getWorkItemsForProject(projectId)

        val customer = project.customerContactId?.let { id -> allContacts.find { it.id == id } }
        val foreman = project.foremanContactId?.let { id -> allContacts.find { it.id == id } }
        val manager = project.managerContactId?.let { id -> allContacts.find { it.id == id } }

        ProjectFormState(
            name = project.name,
            description = project.description,
            selectedCustomer = customer,
            selectedForeman = foreman,
            selectedManager = manager,
            includeForeman = project.includeForeman,
            includeManager = project.includeManager,
            materials = materials,
            workItems = workItems
        )
    } catch (e: Exception) {
        Log.e(TAG, "loadProjectData: ${e.message}")
        null
    }
}

private fun mergeLoadedProject(
    current: ProjectFormState,
    loaded: ProjectFormState,
    allContacts: List<Contact>
): ProjectFormState {
    val isFirstLoad = current.name.isBlank() && current.materials.isEmpty()

    return if (isFirstLoad) {
        loaded.copy(
            selectedCustomer = refreshContact(loaded.selectedCustomer, allContacts),
            selectedForeman = refreshContact(loaded.selectedForeman, allContacts),
            selectedManager = refreshContact(loaded.selectedManager, allContacts)
        )
    } else {
        current.copy(
            selectedCustomer = refreshContact(current.selectedCustomer, allContacts),
            selectedForeman = refreshContact(current.selectedForeman, allContacts),
            selectedManager = refreshContact(current.selectedManager, allContacts)
        )
    }
}

private fun refreshContact(current: Contact?, all: List<Contact>): Contact? {
    if (current == null) return null
    return all.find { it.id == current.id } ?: current
}

private suspend fun loadContactMethods(
    viewModel: ContactsViewModel,
    contactId: String?
): List<ContactMethod> {
    if (contactId == null) return emptyList()
    return try {
        viewModel.getContactMethods(contactId).first()
    } catch (e: Exception) {
        emptyList()
    }
}

private fun normalizeObjectId(objectId: String?): String? = when (objectId) {
    null, "none", "root", "" -> null
    else -> objectId
}

private const val TAG = "CreateProjectScreen"