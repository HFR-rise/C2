package com.example.myapplication.ui.theme

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.myapplication.data.models.ProjectMemberDto
import com.example.myapplication.ui.components.ContactSelectorConfig
import com.example.myapplication.ui.components.ContactSelectorDialog
import com.example.myapplication.ui.components.CustomSnackbarHost
import com.example.myapplication.viewmodels.MemberRole
import com.example.myapplication.viewmodels.ProjectMembersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectMembersScreen(
    navController: NavController,
    viewModel: ProjectMembersViewModel = hiltViewModel()
) {
    val members by viewModel.members.collectAsState()
    val myRole by viewModel.myRole.collectAsState()
    val showContactSelector by viewModel.showContactSelector.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var memberToRemove by remember { mutableStateOf<ProjectMemberDto?>(null) }
    var showBuildersSelector by remember { mutableStateOf(false) }

    LaunchedEffect(operationMessage) {
        operationMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    val isCustomer = myRole == "CUSTOMER"
    val isEstimator = myRole == "ESTIMATOR"

    val canManageBuilders = isCustomer || isEstimator

    Scaffold(
        snackbarHost = { CustomSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Участники", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
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
            if (canManageBuilders) {
                FloatingActionButton(
                    onClick = { showBuildersSelector = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, "Добавить строителей")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GradientDivider()

            if (isLoading && members.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val customer = members.firstOrNull { it.role == "CUSTOMER" }
                item { MemberSectionHeader("Заказчик") }
                if (customer != null) {
                    item {
                        MemberCard(
                            member = customer,
                            canEdit = isCustomer,
                            onChangeRole = { viewModel.openContactSelector(MemberRole.CUSTOMER) },
                            onRemove = null
                        )
                    }
                }

                val estimator = members.firstOrNull { it.role == "ESTIMATOR" }
                item { MemberSectionHeader("Сметчик") }
                if (estimator != null) {
                    item {
                        MemberCard(
                            member = estimator,
                            canEdit = isCustomer,
                            onChangeRole = { viewModel.openContactSelector(MemberRole.ESTIMATOR) },
                            onRemove = { memberToRemove = estimator }
                        )
                    }
                } else {
                    item { EmptyMemberHint("Сметчик не назначен") }
                }

                val builders = members.filter { it.role == "BUILDER" }
                item { MemberSectionHeader("Строители (${builders.size})") }
                if (builders.isEmpty()) {
                    item { EmptyMemberHint("Нет строителей") }
                } else {
                    items(builders, key = { it.userId }) { builder ->
                        MemberCard(
                            member = builder,
                            canEdit = canManageBuilders,
                            onChangeRole = null,
                            onRemove = { memberToRemove = builder }
                        )
                    }
                }
            }
        }
    }

    showContactSelector?.let { role ->
        ContactSelectorDialog(
            config = ContactSelectorConfig(
                title = role.selectorTitle,
                requirePhone = true,
                validateContact = { contact ->
                    viewModel.checkContactExists(contact)
                }
            ),
            onDismiss = { viewModel.closeContactSelector() },
            onSelect = { contact ->
                when (role) {
                    MemberRole.CUSTOMER -> viewModel.setCustomer(contact)
                    MemberRole.ESTIMATOR -> viewModel.setEstimator(contact)
                    MemberRole.BUILDER -> { }
                }
                viewModel.closeContactSelector()
            }
        )
    }

    if (showBuildersSelector) {
        ContactSelectorDialog(
            config = ContactSelectorConfig(
                title = "Выберите строителей",
                requirePhone = true,
                validateContact = { contact ->
                    viewModel.checkContactExists(contact)
                }
            ),
            onDismiss = { showBuildersSelector = false },
            onSelect = { contact ->
                viewModel.addBuilders(listOf(contact))
                showBuildersSelector = false
            }
        )
    }

    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Удалить участника?") },
            text = {
                Text("Убрать ${member.name ?: member.phoneNumber ?: "участника"} из проекта?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeMember(member.userId)
                        memberToRemove = null
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun MemberSectionHeader(title: String) {
    Text(
        title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun EmptyMemberHint(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = Color.Gray,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun MemberCard(
    member: ProjectMemberDto,
    canEdit: Boolean,
    onChangeRole: (() -> Unit)?,
    onRemove: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = roleColor(member.role).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = roleColor(member.role),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name ?: member.phoneNumber ?: member.userId,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
                if (member.name != null && member.phoneNumber != null) {
                    Text(
                        text = member.phoneNumber,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Spacer(Modifier.height(4.dp))
                RoleBadge(member.role)
            }

            if (canEdit) {
                Row {
                    onChangeRole?.let {
                        IconButton(onClick = it) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Сменить",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    onRemove?.let {
                        IconButton(onClick = it) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(role: String) {
    val (text, color) = when (role) {
        "CUSTOMER" -> "Заказчик" to Color(0xFF4CAF50)
        "ESTIMATOR" -> "Сметчик" to Color(0xFF2196F3)
        "BUILDER" -> "Строитель" to Color(0xFFFF9800)
        else -> role to Color.Gray
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private fun roleColor(role: String): Color = when (role) {
    "CUSTOMER" -> Color(0xFF4CAF50)
    "ESTIMATOR" -> Color(0xFF2196F3)
    "BUILDER" -> Color(0xFFFF9800)
    else -> Color.Gray
}