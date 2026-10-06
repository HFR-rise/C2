package com.example.myapplication.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.ui.components.AppSearchBar
import com.example.myapplication.ui.components.ConfirmationDialog
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.FilterOption
import com.example.myapplication.ui.components.ProjectCard
import com.example.myapplication.ui.components.SectionHeader
import com.example.myapplication.viewmodels.ProjectFilterType
import com.example.myapplication.viewmodels.ProjectsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    navController: NavController,
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val filteredProjects by viewModel.filteredProjects.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()

    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsState()
    val projectToDelete by viewModel.projectToDelete.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (viewModel.syncManager.hasInternetConnection()) {
            viewModel.loadProjectsFromServer()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("create_project/none") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Создать смету")
            }
        },
        topBar = {
            ProjectsTopBar(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            GradientDivider()

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    filteredProjects.isEmpty() && searchQuery.isNotBlank() -> {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "Ничего не найдено",
                            subtitle = "По запросу \"$searchQuery\""
                        )
                    }

                    filteredProjects.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Default.Receipt,
                            title = "У вас пока нет смет",
                            subtitle = "Нажмите + чтобы создать смету"
                        )
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredProjects, key = { it.id }) { project ->
                                ProjectCard(
                                    project = project,
                                    onClick = { navController.navigate("view_project/${project.id}") },
                                    onEdit = {
                                        when (project.myRole) {
                                            "ESTIMATOR" -> navController.navigate("edit_draft/${project.id}")
                                            else -> navController.navigate("edit_project/${project.id}")
                                        }
                                    },
                                    onMove = { navController.navigate("move_project/${project.id}/none") },
                                    onDelete = { viewModel.showDeleteProjectConfirmation(project) }
                                    // onManageMembers убрано — роли теперь меняются внутри сметы
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterMenu) {
        ProjectsFilterDialog(
            currentFilter = currentFilter,
            onFilterSelected = viewModel::updateSearchFilter,
            onDismiss = { showFilterMenu = false }
        )
    }

    if (showDeleteConfirmation && projectToDelete != null) {
        ConfirmationDialog(
            title = "Удалить смету",
            message = "Вы уверены, что хотите удалить смету \"${projectToDelete!!.name}\"? Все материалы и работы будут удалены безвозвратно.",
            onConfirm = { viewModel.confirmDeleteProject() },
            onDismiss = { viewModel.hideDeleteProjectConfirmation() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectsTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    currentFilter: ProjectFilterType,
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
                    placeholder = getProjectSearchPlaceholder(currentFilter),
                    filterIcon = getProjectFilterIcon(currentFilter),
                    onFilterClick = onFilterClick,
                    onClose = onClose
                )
            } else {
                Text("Сметы", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        actions = {
            if (!isSearchActive) {
                IconButton(onClick = { onSearchToggle(true) }) {
                    Icon(Icons.Default.Search, contentDescription = "Поиск", tint = Color.White)
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
private fun ProjectsFilterDialog(
    currentFilter: ProjectFilterType,
    onFilterSelected: (ProjectFilterType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите тип поиска") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Основные")
                listOf(
                    ProjectFilterType.BY_NAME,
                    ProjectFilterType.BY_DESCRIPTION
                ).forEach { filter ->
                    FilterOption(
                        title = getProjectFilterName(filter),
                        icon = getProjectFilterIcon(filter),
                        isSelected = currentFilter == filter
                    ) {
                        onFilterSelected(filter)
                        onDismiss()
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(title = "Участники")
                listOf(
                    ProjectFilterType.BY_PARTICIPANT
                ).forEach { filter ->
                    FilterOption(
                        title = getProjectFilterName(filter),
                        icon = getProjectFilterIcon(filter),
                        isSelected = currentFilter == filter
                    ) {
                        onFilterSelected(filter)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

private fun getProjectFilterIcon(filter: ProjectFilterType): ImageVector = when (filter) {
    ProjectFilterType.BY_NAME -> Icons.Default.Title
    ProjectFilterType.BY_DESCRIPTION -> Icons.Default.Description
    ProjectFilterType.BY_PARTICIPANT -> Icons.Default.Person
}

private fun getProjectFilterName(filter: ProjectFilterType): String = when (filter) {
    ProjectFilterType.BY_NAME -> "По названию"
    ProjectFilterType.BY_DESCRIPTION -> "По описанию"
    ProjectFilterType.BY_PARTICIPANT -> "По участнику"
}

private fun getProjectSearchPlaceholder(filter: ProjectFilterType): String = when (filter) {
    ProjectFilterType.BY_NAME -> "Поиск по названию..."
    ProjectFilterType.BY_DESCRIPTION -> "Поиск по описанию..."
    ProjectFilterType.BY_PARTICIPANT -> "Поиск по имени участника..."
}