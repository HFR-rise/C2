package com.example.myapplication.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.ui.components.MaterialDialog
import com.example.myapplication.ui.components.MaterialItem
import com.example.myapplication.ui.components.TotalBudgetCard
import com.example.myapplication.ui.components.WorkDialog
import com.example.myapplication.ui.components.WorkItemCard
import com.example.myapplication.viewmodels.ProjectDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    navController: NavController,
    viewModel: ProjectDetailViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsState()
    val filteredMaterials by viewModel.filteredMaterials.collectAsState()
    val filteredWorkItems by viewModel.filteredWorkItems.collectAsState()
    val materialSearchQuery by viewModel.materialSearchQuery.collectAsState()
    val workSearchQuery by viewModel.workSearchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showMaterialDialog by viewModel.showMaterialDialog.collectAsState()
    val showWorkDialog by viewModel.showWorkDialog.collectAsState()
    val editingMaterial by viewModel.editingMaterial.collectAsState()
    val editingWorkItem by viewModel.editingWorkItem.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Детали проекта") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = viewModel::showMaterialDialog,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить материал")
                }
                FloatingActionButton(
                    onClick = viewModel::showWorkDialog,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Build, contentDescription = "Добавить работу")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TotalBudgetCard(
                    total = project?.totalBudget ?: 0.0,
                    title = "Бюджет проекта"
                )
            }

            item {
                Text("Материалы", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            item {
                OutlinedTextField(
                    value = materialSearchQuery,
                    onValueChange = viewModel::updateMaterialSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск материалов...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }

            if (filteredMaterials.isEmpty() && materialSearchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "Материалы не найдены",
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            items(filteredMaterials, key = { it.id }) { material ->
                MaterialItem(
                    material = material,
                    onClick = { viewModel.startEditMaterial(material) },
                    onDelete = { viewModel.deleteMaterial(material) }
                )
            }

            item {
                Text(
                    text = "Работы",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = workSearchQuery,
                    onValueChange = viewModel::updateWorkSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск работ...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }

            if (filteredWorkItems.isEmpty() && workSearchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "Работы не найдены",
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            items(filteredWorkItems, key = { it.id }) { work ->
                WorkItemCard(
                    workItem = work,
                    onClick = { viewModel.startEditWorkItem(work) },
                    onDelete = { viewModel.deleteWorkItem(work) }
                )
            }
        }
    }

    if (showMaterialDialog || editingMaterial != null) {
        val material = editingMaterial
        MaterialDialog(
            material = material,
            onDismiss = {
                viewModel.hideMaterialDialog()
                viewModel.clearEditMaterial()
            },
            onSave = { name, quantity, unit, price ->
                if (material == null) {
                    viewModel.addMaterial(name, quantity, unit, price)
                } else {
                    viewModel.updateMaterial(
                        material.copy(
                            name = name,
                            quantity = quantity,
                            unit = unit,
                            unitPrice = price
                        )
                    )
                }
            }
        )
    }

    if (showWorkDialog || editingWorkItem != null) {
        val work = editingWorkItem
        WorkDialog(
            workItem = work,
            onDismiss = {
                viewModel.hideWorkDialog()
                viewModel.clearEditWorkItem()
            },
            onSave = { name, hours, rate, materialCost ->
                if (work == null) {
                    viewModel.addWorkItem(name, hours, rate, materialCost)
                } else {
                    viewModel.updateWorkItem(
                        work.copy(
                            name = name,
                            laborHours = hours,
                            hourlyRate = rate,
                            materialCost = materialCost
                        )
                    )
                }
            }
        )
    }
}