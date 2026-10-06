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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.ProjectMemberDto
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.ui.components.AddMaterialDialogSimple
import com.example.myapplication.ui.components.AddWorkDialogSimple
import com.example.myapplication.ui.components.ContactSelectorConfig
import com.example.myapplication.ui.components.ContactSelectorDialog
import com.example.myapplication.ui.components.CustomSnackbarHost
import com.example.myapplication.ui.components.MaterialDialog
import com.example.myapplication.ui.components.WorkDialog
import com.example.myapplication.viewmodels.ProjectsViewModel
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

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

enum class MemberRole(val selectorTitle: String) {
    CUSTOMER("Выберите заказчика"),
    ESTIMATOR("Выберите сметчика"),
    BUILDERS("Выберите строителя")
}

data class ProjectFormState(
    val name: String = "",
    val description: String = "",
    val materials: List<Material> = emptyList(),
    val workItems: List<WorkItem> = emptyList(),

    val memberCustomer: Contact? = null,
    val memberEstimator: Contact? = null,
    val memberBuilders: List<Contact> = emptyList()
) {
    val totalMaterialCost: Double get() = materials.sumOf { it.quantity * it.unitPrice }
    val totalWorkCost: Double get() = workItems.sumOf { it.laborHours * it.hourlyRate + it.materialCost }
    val grandTotal: Double get() = totalMaterialCost + totalWorkCost
    val isValid: Boolean get() = name.isNotBlank()
}

private class CreateProjectUiState {
    var form by mutableStateOf(ProjectFormState())
    var currentStep by mutableStateOf(1)
    var isInitialLoading by mutableStateOf(false)
    var isSubmitting by mutableStateOf(false)
    var showMemberSelectorFor by mutableStateOf<MemberRole?>(null)
    var showAddMaterialDialog by mutableStateOf(false)
    var showAddWorkDialog by mutableStateOf(false)

    var myRole by mutableStateOf<String?>(null)

    var editingMaterial by mutableStateOf<Material?>(null)
    var editingWorkItem by mutableStateOf<WorkItem?>(null)
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
    val uiState = remember { CreateProjectUiState() }
    val isReadOnly = mode.isReadOnly

    val allContacts by viewModel.contacts.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var pendingWarning by remember { mutableStateOf<String?>(null) }
    var pendingNavigate by remember { mutableStateOf(false) }

    LaunchedEffect(pendingNavigate) {
        if (!pendingNavigate) return@LaunchedEffect

        val msg = pendingWarning
        if (msg != null) {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
        }
        delay(200)
        pendingWarning = null
        pendingNavigate = false
        navController.navigateUp()
    }

    LaunchedEffect(projectId, mode) {
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
            viewModel = viewModel
        )

        if (loaded != null) {
            uiState.form = mergeLoadedProject(uiState.form, loaded.first)
            uiState.myRole = loaded.second
        }

        uiState.isInitialLoading = false
    }

    val canManageMembers = when (mode) {
        ProjectScreenMode.CREATE -> true
        ProjectScreenMode.EDIT, ProjectScreenMode.VIEW -> uiState.myRole == "CUSTOMER"
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
        snackbarHost = { CustomSnackbarHost(hostState = snackbarHostState) },
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
                                canManageMembers = canManageMembers,
                                showCustomerHint = (mode == ProjectScreenMode.CREATE),
                                onFormUpdate = { updatedForm -> uiState.form = updatedForm },
                                onShowMemberSelector = { role -> uiState.showMemberSelectorFor = role }
                            )
                            2 -> ProjectStep2(
                                form = uiState.form,
                                isReadOnly = isReadOnly,
                                onEditMaterial = { material ->
                                    uiState.editingMaterial = material
                                },
                                onEditWorkItem = { work ->
                                    uiState.editingWorkItem = work
                                },
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
                        isSubmitting = uiState.isSubmitting,
                        onNext = { uiState.currentStep = 2 },
                        onCancel = { navController.navigateUp() },
                        onCreate = {
                            if (!formIsValid(uiState.form)) return@ProjectBottomButton
                            uiState.isSubmitting = true

                            viewModel.createProjectWithMaterialsAndWorks(
                                name = uiState.form.name,
                                description = uiState.form.description,
                                objectId = normalizeObjectId(objectId),
                                materials = uiState.form.materials,
                                workItems = uiState.form.workItems,
                                memberCustomer = uiState.form.memberCustomer,
                                memberEstimator = uiState.form.memberEstimator,
                                memberBuilders = uiState.form.memberBuilders,
                                onResult = { result ->
                                    uiState.isSubmitting = false
                                    if (result.isSuccess) {
                                        pendingWarning = result.warning
                                        pendingNavigate = true
                                    }
                                }
                            )
                        },
                        onUpdate = {
                            if (projectId == null || !formIsValid(uiState.form)) return@ProjectBottomButton
                            uiState.isSubmitting = true

                            viewModel.updateProjectWithMembers(
                                projectId = projectId,
                                name = uiState.form.name,
                                description = uiState.form.description,
                                materials = uiState.form.materials,
                                workItems = uiState.form.workItems,
                                memberCustomer = uiState.form.memberCustomer,
                                memberEstimator = uiState.form.memberEstimator,
                                memberBuilders = uiState.form.memberBuilders,
                                onResult = { warning ->
                                    uiState.isSubmitting = false
                                    if (warning != null) {
                                        pendingWarning = warning
                                    }
                                    pendingNavigate = true
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    uiState.showMemberSelectorFor?.let { role ->
        ContactSelectorDialog(
            config = ContactSelectorConfig(
                title = role.selectorTitle,
                requirePhone = true,
                validateContact = { contact ->
                    viewModel.checkContactExists(contact)
                }
            ),
            onDismiss = { uiState.showMemberSelectorFor = null },
            onSelect = { contact ->
                when (role) {
                    MemberRole.CUSTOMER -> {
                        uiState.form = uiState.form.copy(memberCustomer = contact)
                    }
                    MemberRole.ESTIMATOR -> {
                        uiState.form = uiState.form.copy(memberEstimator = contact)
                    }
                    MemberRole.BUILDERS -> {
                        if (uiState.form.memberBuilders.none { it.id == contact.id }) {
                            uiState.form = uiState.form.copy(
                                memberBuilders = uiState.form.memberBuilders + contact
                            )
                        }
                    }
                }
                uiState.showMemberSelectorFor = null
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

    uiState.editingMaterial?.let { material ->
        MaterialDialog(
            material = material,
            onDismiss = { uiState.editingMaterial = null },
            onSave = { name, quantity, unit, price ->
                uiState.form = uiState.form.copy(
                    materials = uiState.form.materials.map { m ->
                        if (m.id == material.id) {
                            m.copy(
                                name = name,
                                quantity = quantity,
                                unit = unit,
                                unitPrice = price
                            )
                        } else m
                    }
                )
                uiState.editingMaterial = null
            }
        )
    }

    uiState.editingWorkItem?.let { work ->
        WorkDialog(
            workItem = work,
            onDismiss = { uiState.editingWorkItem = null },
            onSave = { name, hours, rate, materialCost ->
                uiState.form = uiState.form.copy(
                    workItems = uiState.form.workItems.map { w ->
                        if (w.id == work.id) {
                            w.copy(
                                name = name,
                                laborHours = hours,
                                hourlyRate = rate,
                                materialCost = materialCost
                            )
                        } else w
                    }
                )
                uiState.editingWorkItem = null
            }
        )
    }
}

private fun formIsValid(form: ProjectFormState): Boolean = form.name.isNotBlank()

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
    isSubmitting: Boolean,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
    onUpdate: () -> Unit
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
                        ProjectScreenMode.CREATE -> onCreate()
                        ProjectScreenMode.EDIT -> onUpdate()
                        ProjectScreenMode.VIEW -> onCancel()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = (mode.isReadOnly || form.isValid) && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(mode.bottomButtonText)
                }
            }
        }
    }
}

private suspend fun loadProjectData(
    projectId: String,
    viewModel: ProjectsViewModel
): Pair<ProjectFormState, String?>? {
    return try {
        val result = viewModel.getProjectWithMembers(projectId)
        if (result == null) {
            Log.w(TAG, "loadProjectData: project $projectId not found")
            return null
        }
        val (project, members) = result

        val materials = viewModel.getMaterialsForProject(projectId)
        val workItems = viewModel.getWorkItemsForProject(projectId)

        val customerDto = members.firstOrNull { it.role == "CUSTOMER" }
        val estimatorDto = members.firstOrNull { it.role == "ESTIMATOR" }
        val builderDtos = members.filter { it.role == "BUILDER" }

        Log.d(TAG, "loadProjectData: members — customer=$customerDto, " +
                "estimator=$estimatorDto, builders=${builderDtos.size}")

        val customer = customerDto?.toContactForDisplay(viewModel)
        val estimator = estimatorDto?.toContactForDisplay(viewModel)
        val builders = builderDtos.mapNotNull { it.toContactForDisplay(viewModel) }

        val form = ProjectFormState(
            name = project.name,
            description = project.description,
            materials = materials,
            workItems = workItems,
            memberCustomer = customer,
            memberEstimator = estimator,
            memberBuilders = builders
        )

        form to project.myRole
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "loadProjectData: ${e.message}", e)
        null
    }
}

private suspend fun ProjectMemberDto.toContactForDisplay(
    viewModel: ProjectsViewModel
): Contact? {
    val phone = phoneNumber
    val userId = userId

    if (phone.isNullOrBlank() && name.isNullOrBlank()) {
        Log.w(TAG, "toContactForDisplay: userId=$userId has no phone and no name — skip")
        return null
    }

    val nameFromProfile = name?.takeIf { it.isNotBlank() }

    val nameFromContact = if (nameFromProfile == null) {
        viewModel.findLocalContactNameByPhone(phone)
    } else null

    val displayName = nameFromProfile
        ?: nameFromContact
        ?: phone
        ?: userId

    Log.d(TAG, "toContactForDisplay: userId=$userId, phone=$phone, " +
            "nameFromProfile=$nameFromProfile, nameFromContact=$nameFromContact, " +
            "→ displayName='$displayName'")

    return Contact(
        id = userId,
        name = displayName,
        description = phone.orEmpty(),
        userId = userId
    )
}

private fun mergeLoadedProject(
    current: ProjectFormState,
    loaded: ProjectFormState
): ProjectFormState {
    val isFirstLoad = current.name.isBlank() && current.materials.isEmpty()
    return if (isFirstLoad) loaded else current
}

private fun normalizeObjectId(objectId: String?): String? = when (objectId) {
    null, "none", "root", "" -> null
    else -> objectId
}

private const val TAG = "CreateProjectScreen"