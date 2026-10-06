package com.example.myapplication.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.ui.components.AddMaterialDialogSimple
import com.example.myapplication.ui.components.AddWorkDialogSimple
import com.example.myapplication.ui.components.MaterialDialog
import com.example.myapplication.ui.components.MaterialItem
import com.example.myapplication.ui.components.TotalBudgetCard
import com.example.myapplication.ui.components.WorkDialog
import com.example.myapplication.ui.components.WorkItemCard
import com.example.myapplication.viewmodels.EstimateDraftViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEstimateDraftScreen(
    navController: NavController,
    viewModel: EstimateDraftViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsState()
    val isReadOnly by viewModel.isReadOnly.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showAddMaterial by remember { mutableStateOf(false) }
    var showAddWork by remember { mutableStateOf(false) }
    var showSubmitDialog by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }

    var editingMaterial by remember { mutableStateOf<Material?>(null) }
    var editingWorkItem by remember { mutableStateOf<WorkItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Черновик сметы",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (!isReadOnly) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FloatingActionButton(
                        onClick = { showAddMaterial = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Category, "Добавить материал")
                    }
                    FloatingActionButton(
                        onClick = { showAddWork = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Build, "Добавить работу")
                    }
                }
            }
        },
        bottomBar = {
            if (!isReadOnly) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    tonalElevation = 3.dp
                ) {
                    Button(
                        onClick = { showSubmitDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        enabled = draft != null && !isLoading
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Отправить на согласование")
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val d = draft
        if (d == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Черновик не найден")
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GradientDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = d.projectName,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text("Название сметы*") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isReadOnly,
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = d.projectDescription,
                        onValueChange = { viewModel.updateDescription(it) },
                        label = { Text("Описание") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isReadOnly,
                        minLines = 2
                    )
                }

                item {
                    Text(
                        "Материалы",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (d.materials.isEmpty()) {
                    item { Text("Нет материалов", color = Color.Gray) }
                } else {
                    items(d.materials, key = { it.id }) { material ->
                        MaterialItem(
                            material = material,
                            onEdit = if (isReadOnly) null
                            else { { editingMaterial = material } },
                            onDelete = if (isReadOnly) null
                            else { { viewModel.deleteMaterial(material.id) } }
                        )
                    }
                }

                item {
                    Text(
                        "Работы",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (d.workItems.isEmpty()) {
                    item { Text("Нет работ", color = Color.Gray) }
                } else {
                    items(d.workItems, key = { it.id }) { work ->
                        WorkItemCard(
                            workItem = work,
                            onEdit = if (isReadOnly) null
                            else { { editingWorkItem = work } },
                            onDelete = if (isReadOnly) null
                            else { { viewModel.deleteWorkItem(work.id) } }
                        )
                    }
                }

                item {
                    val total = d.materials.sumOf { it.quantity * it.unitPrice } +
                            d.workItems.sumOf { it.laborHours * it.hourlyRate + it.materialCost }
                    TotalBudgetCard(total = total)
                }

                if (errorMessage != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                errorMessage.orEmpty(),
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddMaterial) {
        AddMaterialDialogSimple(
            onDismiss = { showAddMaterial = false },
            onAdd = { name, qty, unit, price ->
                viewModel.addMaterial(
                    Material(
                        projectId = draft?.projectId.orEmpty(),
                        name = name,
                        quantity = qty,
                        unit = unit,
                        unitPrice = price
                    )
                )
                showAddMaterial = false
            }
        )
    }

    if (showAddWork) {
        AddWorkDialogSimple(
            onDismiss = { showAddWork = false },
            onAdd = { name, hours, rate, cost ->
                viewModel.addWorkItem(
                    WorkItem(
                        projectId = draft?.projectId.orEmpty(),
                        name = name,
                        laborHours = hours,
                        hourlyRate = rate,
                        materialCost = cost
                    )
                )
                showAddWork = false
            }
        )
    }

    editingMaterial?.let { material ->
        MaterialDialog(
            material = material,
            onDismiss = { editingMaterial = null },
            onSave = { name, quantity, unit, price ->
                viewModel.updateMaterial(
                    material.copy(
                        name = name,
                        quantity = quantity,
                        unit = unit,
                        unitPrice = price
                    )
                )
                editingMaterial = null
            }
        )
    }

    editingWorkItem?.let { work ->
        WorkDialog(
            workItem = work,
            onDismiss = { editingWorkItem = null },
            onSave = { name, hours, rate, materialCost ->
                viewModel.updateWorkItem(
                    work.copy(
                        name = name,
                        laborHours = hours,
                        hourlyRate = rate,
                        materialCost = materialCost
                    )
                )
                editingWorkItem = null
            }
        )
    }

    if (showSubmitDialog) {
        AlertDialog(
            onDismissRequest = { showSubmitDialog = false },
            title = { Text("Отправить на согласование?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Заказчик получит уведомление и сможет утвердить или отклонить изменения.")
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Комментарий (необязательно)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateComment(comment)
                        viewModel.submitForApproval()
                        showSubmitDialog = false
                        comment = ""
                        navController.navigateUp()
                    }
                ) {
                    Text("Отправить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}