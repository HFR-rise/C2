package com.example.myapplication.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.example.myapplication.data.database.ChangeRequestEntity
import com.example.myapplication.data.models.MaterialSnapshotDto
import com.example.myapplication.data.models.ProjectSnapshotDto
import com.example.myapplication.data.models.WorkItemSnapshotDto
import com.example.myapplication.viewmodels.CommunicationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationScreen(
    navController: NavController,
    viewModel: CommunicationViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val processingIds by viewModel.processingIds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val currentUserId by viewModel.currentUserId.collectAsState()

    var rejectingId by remember { mutableStateOf<String?>(null) }
    var rejectComment by remember { mutableStateOf("") }
    var viewingDraft by remember { mutableStateOf<ChangeRequestEntity?>(null) }
    var viewingComment by remember { mutableStateOf<ChangeRequestEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Событие", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            GradientDivider()

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Пока нет событий", color = Color.Gray)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        ChangeCard(
                            item = item,
                            currentUserId = currentUserId,
                            isProcessing = processingIds.contains(item.id),
                            onApprove = { viewModel.approve(item.id) },
                            onReject = {
                                rejectingId = item.id
                                rejectComment = ""
                            },
                            onViewDraft = { viewingDraft = item },
                            onViewComment = { viewingComment = item }
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    if (rejectingId != null) {
        AlertDialog(
            onDismissRequest = { rejectingId = null },
            title = { Text("Отклонить изменения?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Объясните сметчику, что нужно исправить.")
                    OutlinedTextField(
                        value = rejectComment,
                        onValueChange = { rejectComment = it },
                        label = { Text("Причина отклонения*") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = rejectComment.isNotBlank(),
                    onClick = {
                        rejectingId?.let { viewModel.reject(it, rejectComment) }
                        rejectingId = null
                        rejectComment = ""
                    }
                ) {
                    Text("Отклонить", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectingId = null }) { Text("Отмена") }
            }
        )
    }

    viewingDraft?.let { entity ->
        val snapshot = remember(entity.id) { viewModel.parseSnapshot(entity) }
        DraftSnapshotDialog(
            entity = entity,
            snapshot = snapshot,
            onDismiss = { viewingDraft = null }
        )
    }

    viewingComment?.let { entity ->
        CommentDialog(
            entity = entity,
            onDismiss = { viewingComment = null }
        )
    }
}

@Composable
private fun ChangeCard(
    item: ChangeRequestEntity,
    currentUserId: String?,
    isProcessing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onViewDraft: () -> Unit,
    onViewComment: () -> Unit
) {
    val isPendingEdit = item.kind == "ESTIMATE_EDIT" && item.status == "PENDING"
    val isApproved = item.kind == "ESTIMATE_EDIT" && item.status == "APPROVED"
    val isRejected = item.kind == "ESTIMATE_EDIT" && item.status == "REJECTED"
    val isComment = item.kind == "COMMENT"

    val canReview = isPendingEdit && item.authorId != currentUserId

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.projectName ?: "Смета",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(item.status)
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "${item.authorName ?: item.authorPhone ?: "Автор"} • ${formatDate(item.createdAt)}",
                fontSize = 12.sp,
                color = Color.Gray
            )

            if (isComment) {
                Spacer(Modifier.height(8.dp))
                Text(item.comment.orEmpty(), fontSize = 14.sp)
            } else if (isPendingEdit) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Предложены изменения в смету",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (isApproved) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Смета утверждена",
                    fontSize = 13.sp,
                    color = Color(0xFF4CAF50)
                )
            } else if (isRejected) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Изменения отклонены",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.kind == "ESTIMATE_EDIT") {
                    IconButton(onClick = onViewDraft) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = "Просмотр черновика",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (!item.comment.isNullOrBlank() || !item.reviewComment.isNullOrBlank()) {
                    IconButton(onClick = onViewComment) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = "Комментарий",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (canReview) {
                    IconButton(
                        onClick = onApprove,
                        enabled = !isProcessing
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Утвердить",
                            tint = Color(0xFF4CAF50)
                        )
                    }
                    IconButton(
                        onClick = onReject,
                        enabled = !isProcessing
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Отклонить",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (text, color) = when (status) {
        "PENDING" -> "На согласовании" to Color(0xFFFF9800)
        "APPROVED" -> "Утверждено" to Color(0xFF4CAF50)
        "REJECTED" -> "Отклонено" to Color(0xFFF44336)
        else -> status to Color.Gray
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DraftSnapshotDialog(
    entity: ChangeRequestEntity,
    snapshot: ProjectSnapshotDto?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Черновик сметы", fontWeight = FontWeight.Bold)
                Text(
                    entity.projectName ?: "",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        },
        text = {
            if (snapshot == null) {
                Text("Не удалось загрузить черновик")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    snapshot.name?.let {
                        item {
                            Text("Название: $it", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    snapshot.description?.takeIf { it.isNotBlank() }?.let {
                        item {
                            Text("Описание: $it", fontSize = 13.sp)
                        }
                    }

                    item {
                        Text(
                            "Материалы (${snapshot.materials?.size ?: 0})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    val materials = snapshot.materials.orEmpty()
                    if (materials.isEmpty()) {
                        item { Text("Нет материалов", fontSize = 12.sp, color = Color.Gray) }
                    } else {
                        materials.forEach { m ->
                            item { DraftMaterialRow(m) }
                        }
                    }

                    item {
                        Text(
                            "Работы (${snapshot.workItems?.size ?: 0})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    val workItems = snapshot.workItems.orEmpty()
                    if (workItems.isEmpty()) {
                        item { Text("Нет работ", fontSize = 12.sp, color = Color.Gray) }
                    } else {
                        workItems.forEach { w ->
                            item { DraftWorkRow(w) }
                        }
                    }

                    snapshot.totalBudget?.let { total ->
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Итого:", fontWeight = FontWeight.Bold)
                                    Text(
                                        String.format(Locale.US, "%.2f ₽", total),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun DraftMaterialRow(m: MaterialSnapshotDto) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(m.name ?: "—", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(
                "${m.quantity ?: 0.0} ${m.unit ?: "шт"} × ${
                    String.format(Locale.US, "%.2f", m.unitPrice ?: 0.0)
                } ₽",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun DraftWorkRow(w: WorkItemSnapshotDto) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(w.name ?: "—", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            val labor = (w.laborHours ?: 0.0) * (w.hourlyRate ?: 0.0)
            Text(
                "Труд: ${w.laborHours ?: 0.0} ч × ${
                    String.format(Locale.US, "%.2f", w.hourlyRate ?: 0.0)
                } ₽ = ${String.format(Locale.US, "%.2f", labor)} ₽",
                fontSize = 11.sp,
                color = Color.Gray
            )
            Text(
                "Материалы: ${
                    String.format(Locale.US, "%.2f", w.materialCost ?: 0.0)
                } ₽",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun CommentDialog(
    entity: ChangeRequestEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Комментарии") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!entity.comment.isNullOrBlank()) {
                    Column {
                        Text(
                            "Сметчик",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(entity.comment, fontSize = 14.sp)
                    }
                }

                if (!entity.reviewComment.isNullOrBlank()) {
                    HorizontalDivider()
                    Column {
                        Text(
                            "Заказчик:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(entity.reviewComment, fontSize = 14.sp)
                    }
                }

                if (entity.comment.isNullOrBlank() && entity.reviewComment.isNullOrBlank()) {
                    Text("Комментариев нет", color = Color.Gray)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

private fun formatDate(epochMs: Long): String {
    return SimpleDateFormat(
        "dd.MM.yyyy HH:mm",
        Locale.getDefault()
    ).format(Date(epochMs))
}