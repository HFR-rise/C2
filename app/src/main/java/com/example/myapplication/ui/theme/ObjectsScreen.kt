package com.example.myapplication.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import com.example.myapplication.ui.components.AppSearchBar
import com.example.myapplication.ui.components.ConfirmationDialog
import com.example.myapplication.ui.components.CreateObjectDialog
import com.example.myapplication.ui.components.CreateTypeDialog
import com.example.myapplication.ui.components.EditObjectDialog
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.FilterOption
import com.example.myapplication.ui.components.MoveProjectCardWithInfo
import com.example.myapplication.ui.components.ObjectCard
import com.example.myapplication.ui.components.ProjectCard
import com.example.myapplication.ui.components.SectionHeader
import com.example.myapplication.ui.components.SelectableObjectCard
import com.example.myapplication.viewmodels.ObjectFilterType
import com.example.myapplication.viewmodels.ObjectsViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectsScreen(
    navController: NavController,
    parentObjectId: String? = null,
    selectionMode: Boolean = false,
    onObjectSelected: ((String, String) -> Unit)? = null,
    onObjectOpen: ((String, String) -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    navigationStack: List<Pair<String, String>> = emptyList(),
    viewModel: ObjectsViewModel = hiltViewModel()
) {
    val filteredObjects by viewModel.filteredObjects.collectAsStateWithLifecycle()
    val projectsInObject by viewModel.projectsInObject.collectAsStateWithLifecycle()
    val rootLevelProjects by viewModel.rootLevelProjects.collectAsStateWithLifecycle()
    val showRootProjects by viewModel.showRootProjects.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentObjectName by viewModel.currentObjectName.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showObjectsSection by remember { mutableStateOf(true) }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    LaunchedEffect(parentObjectId) {
        viewModel.updateParentId(parentObjectId)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearSearch() }
    }

    val callbacks = remember(navController) {
        ObjectsContentCallbacks(
            onToggleObjectsSection = { showObjectsSection = !showObjectsSection },
            onToggleRootProjects = viewModel::toggleRootProjectsSection,
            onObjectSelected = onObjectSelected,
            onObjectOpen = onObjectOpen,
            onShowInfo = viewModel::showInfoDialog,
            onEditObject = viewModel::startEditing,
            onDeleteObject = viewModel::showDeleteConfirmation,
            onDeleteProject = viewModel::showDeleteProjectConfirmation,
            onNavigateToObject = { navController.navigate("objects/${it.id}") },
            onViewProject = { navController.navigate("view_project/${it.id}") },
            onEditProject = { project ->
                when (project.myRole) {
                    "ESTIMATOR" -> navController.navigate("edit_draft/${project.id}")
                    else -> navController.navigate("edit_project/${project.id}")
                }
            },
            onMoveProject = { project, fromObjectId ->
                navController.navigate("move_project/${project.id}/${fromObjectId ?: "none"}")
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = { viewModel.showCreateTypeDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Создать")
                }
            }
        },
        topBar = {
            ObjectsTopBar(
                isSearchActive = isSearchActive,
                selectionMode = selectionMode,
                parentObjectId = parentObjectId,
                searchQuery = searchQuery,
                currentFilter = currentFilter,
                currentObjectName = currentObjectName,
                onSearchToggle = { isSearchActive = it },
                onSearchQueryChange = viewModel::updateSearchQuery,
                onFilterClick = { showFilterMenu = true },
                onBackClick = {
                    when {
                        navigationStack.isNotEmpty() -> onNavigateBack?.invoke()
                        selectionMode -> onNavigateBack?.invoke()
                        else -> navController.popBackStack()
                    }
                },
                onCloseClick = { navController.navigateUp() }
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.refreshAllData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                GradientDivider()

                if (isLoading && !isRefreshing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                } else {
                    ObjectsContent(
                        data = ObjectsContentData(
                            objects = filteredObjects,
                            projectsInObject = projectsInObject,
                            rootLevelProjects = rootLevelProjects,
                            showObjectsSection = showObjectsSection,
                            showRootProjects = showRootProjects,
                            searchQuery = searchQuery,
                            selectionMode = selectionMode,
                            parentObjectId = parentObjectId
                        ),
                        callbacks = callbacks
                    )
                }
            }
        }
    }

    ObjectsDialogs(
        viewModel = viewModel,
        navController = navController,
        selectionMode = selectionMode,
        parentObjectId = parentObjectId,
        showFilterMenu = showFilterMenu,
        onDismissFilterMenu = { showFilterMenu = false }
    )
}

private data class ObjectsContentData(
    val objects: List<ObjectModel>,
    val projectsInObject: List<Project>,
    val rootLevelProjects: List<Project>,
    val showObjectsSection: Boolean,
    val showRootProjects: Boolean,
    val searchQuery: String,
    val selectionMode: Boolean,
    val parentObjectId: String?
)

private data class ObjectsContentCallbacks(
    val onToggleObjectsSection: () -> Unit,
    val onToggleRootProjects: () -> Unit,
    val onObjectSelected: ((String, String) -> Unit)?,
    val onObjectOpen: ((String, String) -> Unit)?,
    val onShowInfo: (ObjectModel) -> Unit,
    val onEditObject: (ObjectModel) -> Unit,
    val onDeleteObject: (ObjectModel) -> Unit,
    val onDeleteProject: (Project) -> Unit,
    val onNavigateToObject: (ObjectModel) -> Unit,
    val onViewProject: (Project) -> Unit,
    val onEditProject: (Project) -> Unit,
    val onMoveProject: (Project, String?) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObjectsTopBar(
    isSearchActive: Boolean,
    selectionMode: Boolean,
    parentObjectId: String?,
    searchQuery: String,
    currentFilter: ObjectFilterType,
    currentObjectName: String?,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onBackClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val titleText = remember(selectionMode, parentObjectId, currentObjectName, isSearchActive) {
        when {
            isSearchActive -> null
            selectionMode -> if (parentObjectId == null) {
                "Выберите объект для перемещения"
            } else {
                currentObjectName ?: "Выберите объект"
            }
            else -> currentObjectName ?: "Объекты"
        }
    }

    TopAppBar(
        title = {
            if (isSearchActive) {
                AppSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = getSearchPlaceholder(currentFilter),
                    filterIcon = getObjectFilterIcon(currentFilter),
                    onFilterClick = onFilterClick,
                    onClose = { onSearchToggle(false) }
                )
            } else {
                Text(
                    text = titleText.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        navigationIcon = {
            when {
                selectionMode && parentObjectId != null -> IconButton(onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
                }
                selectionMode -> IconButton(onCloseClick) {
                    Icon(Icons.Default.Close, "Закрыть", tint = Color.White)
                }
                parentObjectId != null && !isSearchActive -> IconButton(onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
                }
            }
        },
        actions = {
            if (!isSearchActive && !selectionMode) {
                IconButton(onClick = { onSearchToggle(true) }) {
                    Icon(Icons.Default.Search, "Поиск", tint = Color.White)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

@Composable
private fun ObjectsContent(
    data: ObjectsContentData,
    callbacks: ObjectsContentCallbacks
) {
    val isEmptyRoot = !data.selectionMode &&
            data.objects.isEmpty() &&
            data.projectsInObject.isEmpty() &&
            data.rootLevelProjects.isEmpty() &&
            data.searchQuery.isBlank() &&
            data.parentObjectId == null

    val isNoResults = data.searchQuery.isNotBlank() &&
            data.objects.isEmpty() &&
            data.projectsInObject.isEmpty() &&
            data.rootLevelProjects.isEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (data.objects.isNotEmpty()) {
            item(key = "objects_header") {
                SectionHeader(
                    title = "🏢 Объекты",
                    count = data.objects.size,
                    isExpanded = data.showObjectsSection,
                    onToggle = callbacks.onToggleObjectsSection
                )
            }

            if (data.showObjectsSection) {
                items(data.objects, key = { it.id }) { obj ->
                    if (data.selectionMode) {
                        SelectableObjectCard(
                            obj = obj,
                            onSelect = { callbacks.onObjectSelected?.invoke(obj.id, obj.name) },
                            onOpen = { callbacks.onObjectOpen?.invoke(obj.id, obj.name) },
                            onInfo = { callbacks.onShowInfo(obj) }
                        )
                    } else {
                        ObjectCard(
                            obj = obj,
                            onClick = { callbacks.onNavigateToObject(obj) },
                            onEdit = { callbacks.onEditObject(obj) },
                            onDelete = { callbacks.onDeleteObject(obj) },
                            onInfo = { callbacks.onShowInfo(obj) }
                        )
                    }
                }
            }
        }

        if (data.objects.isNotEmpty() &&
            (data.projectsInObject.isNotEmpty() || data.rootLevelProjects.isNotEmpty())
        ) {
            item(key = "divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.Gray.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
            }
        }

        if (data.parentObjectId != null && data.projectsInObject.isNotEmpty()) {
            item(key = "projects_in_object_header") {
                SectionHeader(
                    title = "📋 Сметы",
                    count = data.projectsInObject.size,
                    color = Color(0xFFFF9800)
                )
            }
            items(data.projectsInObject, key = { it.id }) { project ->
                if (data.selectionMode) {
                    MoveProjectCardWithInfo(
                        project = project,
                        onInfoClick = { callbacks.onViewProject(project) }
                    )
                } else {
                    ProjectCard(
                        project = project,
                        onClick = { callbacks.onViewProject(project) },
                        onEdit = { callbacks.onEditProject(project) },
                        onMove = { callbacks.onMoveProject(project, data.parentObjectId) },
                        onDelete = { callbacks.onDeleteProject(project) }
                    )
                }
            }
        }

        if (data.parentObjectId != null &&
            data.projectsInObject.isEmpty() &&
            !isNoResults
        ) {
            item(key = "empty_projects_in_object") {
                EmptyState(
                    icon = Icons.Default.Folder,
                    title = "Нет объектов и смет",
                    subtitle = if (data.selectionMode) {
                        "В этом объекте нет смет"
                    } else {
                        "Для создания нажмите +"
                    }
                )
            }
        }

        if (!data.selectionMode && data.parentObjectId == null && data.rootLevelProjects.isNotEmpty()) {
            item(key = "root_projects_header") {
                SectionHeader(
                    title = "📋 Сметы",
                    count = data.rootLevelProjects.size,
                    isExpanded = data.showRootProjects,
                    onToggle = callbacks.onToggleRootProjects,
                    color = Color(0xFFFF9800)
                )
            }
            if (data.showRootProjects) {
                items(data.rootLevelProjects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { callbacks.onViewProject(project) },
                        onEdit = { callbacks.onEditProject(project) },
                        onMove = { callbacks.onMoveProject(project, null) },
                        onDelete = { callbacks.onDeleteProject(project) }
                    )
                }
            }
        }

        if (isEmptyRoot) {
            item(key = "empty_state") {
                EmptyState(
                    icon = Icons.Default.Folder,
                    title = "Нет объектов и смет",
                    subtitle = "Для создания нажмите +"
                )
            }
        }

        if (isNoResults) {
            item(key = "no_results") {
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "Ничего не найдено",
                    subtitle = "По запросу \"${data.searchQuery}\""
                )
            }
        }

        if (data.selectionMode && data.parentObjectId == null) {
            item(key = "root_selection") {
                RootSelectionButton(
                    onSelectRoot = { callbacks.onObjectSelected?.invoke("root", "Главный экран") }
                )
            }
        }
    }
}

@Composable
private fun ObjectsDialogs(
    viewModel: ObjectsViewModel,
    navController: NavController,
    selectionMode: Boolean,
    parentObjectId: String?,
    showFilterMenu: Boolean,
    onDismissFilterMenu: () -> Unit
) {
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val showCreateDialog by viewModel.showCreateDialog.collectAsStateWithLifecycle()
    val showCreateTypeDialog by viewModel.showCreateTypeDialog.collectAsStateWithLifecycle()
    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsStateWithLifecycle()
    val objectToDelete by viewModel.objectToDelete.collectAsStateWithLifecycle()
    val showInfoDialog by viewModel.showInfoDialog.collectAsStateWithLifecycle()
    val infoObject by viewModel.infoObject.collectAsStateWithLifecycle()
    val showEditDialog by viewModel.showEditDialog.collectAsStateWithLifecycle()
    val editingObject by viewModel.editingObject.collectAsStateWithLifecycle()
    val showDeleteProjectConfirmation by viewModel.showDeleteProjectConfirmation.collectAsStateWithLifecycle()
    val projectToDelete by viewModel.projectToDelete.collectAsStateWithLifecycle()

    if (showFilterMenu) {
        ObjectFilterDialog(
            currentFilter = currentFilter,
            onFilterSelected = viewModel::updateSearchFilter,
            onDismiss = onDismissFilterMenu
        )
    }

    if (showCreateTypeDialog && !selectionMode) {
        CreateTypeDialog(
            onDismiss = viewModel::hideCreateTypeDialog,
            onCreateObject = {
                viewModel.hideCreateTypeDialog()
                viewModel.showCreateDialog()
            },
            onCreateProject = {
                viewModel.hideCreateTypeDialog()
                navController.navigate("create_project/${parentObjectId ?: "none"}")
            }
        )
    }

    if (showCreateDialog && !selectionMode) {
        CreateObjectDialog(
            onDismiss = viewModel::hideCreateDialog,
            onCreate = viewModel::createObject
        )
    }

    if (showDeleteConfirmation && objectToDelete != null) {
        ConfirmationDialog(
            title = "Удалить объект",
            message = "Вы уверены, что хотите удалить объект \"${objectToDelete?.name ?: ""}\"? Все дочерние объекты и сметы также будут удалены.",
            onConfirm = {
                objectToDelete?.let { viewModel.deleteObject(it) }
                viewModel.hideDeleteConfirmation()
            },
            onDismiss = viewModel::hideDeleteConfirmation
        )
    }

    if (showEditDialog && editingObject != null && !selectionMode) {
        val currentEditing = editingObject!!
        EditObjectDialog(
            obj = currentEditing,
            onDismiss = viewModel::hideEditDialog,
            onSave = { name, street, house, building, description ->
                viewModel.updateObject(
                    currentEditing.copy(
                        name = name,
                        street = street,
                        house = house,
                        building = building,
                        description = description
                    )
                )
            }
        )
    }

    if (showInfoDialog && infoObject != null) {
        ObjectInfoDialog(
            obj = infoObject!!,
            onDismiss = viewModel::hideInfoDialog
        )
    }

    if (showDeleteProjectConfirmation && projectToDelete != null) {
        ConfirmationDialog(
            title = "Удалить смету",
            message = "Вы уверены, что хотите удалить смету \"${projectToDelete!!.name}\"? Все материалы и работы будут удалены безвозвратно.",
            onConfirm = viewModel::confirmDeleteProject,
            onDismiss = viewModel::hideDeleteProjectConfirmation
        )
    }
}

@Composable
private fun RootSelectionButton(onSelectRoot: () -> Unit) {
    Column {
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSelectRoot,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF9800),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("🏠 Не добавлять в объект", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Смета будет отображаться на главном экране",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun ObjectFilterDialog(
    currentFilter: ObjectFilterType,
    onFilterSelected: (ObjectFilterType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите тип поиска") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterOption(
                    title = "По названию",
                    icon = Icons.Default.Folder,
                    isSelected = currentFilter == ObjectFilterType.BY_NAME
                ) {
                    onFilterSelected(ObjectFilterType.BY_NAME); onDismiss()
                }
                FilterOption(
                    title = "По описанию",
                    icon = Icons.Default.Description,
                    isSelected = currentFilter == ObjectFilterType.BY_DESCRIPTION
                ) {
                    onFilterSelected(ObjectFilterType.BY_DESCRIPTION); onDismiss()
                }
                FilterOption(
                    title = "По адресу",
                    icon = Icons.Default.LocationOn,
                    isSelected = currentFilter == ObjectFilterType.BY_ADDRESS
                ) {
                    onFilterSelected(ObjectFilterType.BY_ADDRESS); onDismiss()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun ObjectInfoDialog(obj: ObjectModel, onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val formattedAddress = obj.getFormattedAddress()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(obj.name) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                if (formattedAddress.isNotBlank()) {
                    InfoRow(
                        icon = Icons.Default.LocationOn,
                        text = formattedAddress,
                        onClick = { clipboardManager.setText(AnnotatedString(formattedAddress)) }
                    )
                }
                if (obj.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    InfoRow(icon = Icons.Default.Description, text = obj.description)
                }
                if (formattedAddress.isBlank() && obj.description.isBlank()) {
                    Text("Нет дополнительной информации")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    text: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray)
        Spacer(Modifier.width(8.dp))
        Text(text, modifier = Modifier.weight(1f))
        if (onClick != null) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Копировать",
                modifier = Modifier.size(16.dp),
                tint = Color.Gray
            )
        }
    }
}

private fun getSearchPlaceholder(filter: ObjectFilterType): String = when (filter) {
    ObjectFilterType.BY_NAME -> "Поиск по названию..."
    ObjectFilterType.BY_DESCRIPTION -> "Поиск по описанию..."
    ObjectFilterType.BY_ADDRESS -> "Поиск по адресу..."
}

private fun getObjectFilterIcon(filter: ObjectFilterType): ImageVector = when (filter) {
    ObjectFilterType.BY_NAME -> Icons.Default.Folder
    ObjectFilterType.BY_DESCRIPTION -> Icons.Default.Description
    ObjectFilterType.BY_ADDRESS -> Icons.Default.LocationOn
}