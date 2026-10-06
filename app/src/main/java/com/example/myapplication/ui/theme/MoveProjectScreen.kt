package com.example.myapplication.ui.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.viewmodels.ObjectsViewModel

@Composable
fun MoveProjectWrapper(
    navController: NavController,
    projectId: String,
    currentObjectId: String?,
    viewModel: ObjectsViewModel = hiltViewModel()
) {
    var navigationStack by rememberSaveable {
        mutableStateOf<List<NavEntry>>(emptyList())
    }
    var selectedTarget by remember { mutableStateOf<MoveTarget?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val currentParentId: String? = navigationStack.lastOrNull()?.id

    val onObjectSelected: (String, String) -> Unit = remember {
        { objectId, objectName ->
            selectedTarget = if (objectId == ROOT_ID) {
                MoveTarget.Root
            } else {
                MoveTarget.ToObject(objectId, objectName)
            }
            showConfirmDialog = true
        }
    }

    val onObjectOpen: (String, String) -> Unit = remember {
        { objectId, objectName ->
            navigationStack = navigationStack + NavEntry(objectId, objectName)
        }
    }

    val onNavigateBack: () -> Unit = remember(navController, navigationStack) {
        {
            if (navigationStack.isNotEmpty()) {
                navigationStack = navigationStack.dropLast(1)
            } else {
                navController.navigateUp()
            }
        }
    }

    val onConfirmMove: () -> Unit = remember(navController, projectId) {
        {
            val target = selectedTarget
            if (target != null) {
                when (target) {
                    is MoveTarget.Root -> viewModel.moveProject(projectId, ROOT_ID)
                    is MoveTarget.ToObject -> viewModel.moveProject(projectId, target.id)
                }
                showConfirmDialog = false
                selectedTarget = null
                navController.navigateUp()
            }
        }
    }

    val navStackForUi: List<Pair<String, String>> = remember(navigationStack) {
        navigationStack.map { it.id to it.name }
    }

    ObjectsScreen(
        navController = navController,
        parentObjectId = currentParentId,
        selectionMode = true,
        onObjectSelected = onObjectSelected,
        onObjectOpen = onObjectOpen,
        onNavigateBack = onNavigateBack,
        navigationStack = navStackForUi,
        viewModel = viewModel
    )

    selectedTarget?.let { target ->
        if (showConfirmDialog) {
            MoveConfirmationDialog(
                target = target,
                onDismiss = {
                    showConfirmDialog = false
                    selectedTarget = null
                },
                onConfirm = onConfirmMove
            )
        }
    }
}

private const val ROOT_ID = "root"

private sealed interface MoveTarget {
    data object Root : MoveTarget
    data class ToObject(val id: String, val name: String) : MoveTarget
}

private data class NavEntry(
    val id: String,
    val name: String
)

@Composable
private fun MoveConfirmationDialog(
    target: MoveTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val (title, message) = remember(target) {
        when (target) {
            is MoveTarget.Root -> "Переместить на главный экран" to
                    "Смета будет перемещена на главный экран (вне объектов). Продолжить?"
            is MoveTarget.ToObject -> "Переместить смету" to
                    "Смета будет перемещена в объект \"${target.name}\". Продолжить?"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Переместить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}