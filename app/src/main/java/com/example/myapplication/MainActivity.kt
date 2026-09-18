package com.example.myapplication

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.services.SyncManager
import com.example.myapplication.services.WebSocketService
import com.example.myapplication.ui.theme.AuthScreen
import com.example.myapplication.ui.theme.ContactsScreen
import com.example.myapplication.ui.theme.CreateProjectScreen
import com.example.myapplication.ui.theme.FinanceAppTheme
import com.example.myapplication.ui.theme.MoveProjectWrapper
import com.example.myapplication.ui.theme.ObjectsScreen
import com.example.myapplication.ui.theme.PendingSharesScreen
import com.example.myapplication.ui.theme.ProfileScreen
import com.example.myapplication.ui.theme.ProjectScreenMode
import com.example.myapplication.utils.UserPreferences
import com.example.myapplication.viewmodels.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

object Routes {
    const val AUTH = "auth"
    const val OBJECTS_ROOT = "objects_root"
    const val OBJECTS_WITH_PARENT = "objects/{parentId}"
    const val MOVE_PROJECT = "move_project/{projectId}/{currentObjectId}"
    const val CONTACTS = "contacts"
    const val MATERIALS_STORAGE = "materials_storage"
    const val PROFILE = "profile"
    const val VIEW_PROJECT = "view_project/{projectId}"
    const val CREATE_PROJECT = "create_project/{objectId}"
    const val EDIT_PROJECT = "edit_project/{projectId}"
}

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var webSocketService: WebSocketService
    @Inject lateinit var syncManager: SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FinanceAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        userPreferences = userPreferences,
                        syncManager = syncManager
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    userPreferences: UserPreferences,
    syncManager: SyncManager,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    val lastSelectedRoute = remember(currentDestination?.route) {
        resolveLastSelectedRoute(currentDestination?.route)
    }

    LaunchedEffect(isLoggedIn) {
        val target = if (isLoggedIn) Routes.OBJECTS_ROOT else Routes.AUTH
        navController.navigate(target) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    MonitorNetworkConnection(syncManager)

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                currentRoute = currentDestination?.route,
                lastSelectedRoute = lastSelectedRoute,
                isLoggedIn = isLoggedIn,
                onItemClick = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                        popUpTo(Routes.OBJECTS_ROOT) { inclusive = false }
                    }
                }
            )
        }
    ) { paddingValues ->
        AppNavHost(
            navController = navController,
            startDestination = Routes.AUTH,
            userPreferences = userPreferences,
            authViewModel = authViewModel,
            onLogout = authViewModel::logout,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

private fun resolveLastSelectedRoute(route: String?): String = when {
    route == null -> Routes.OBJECTS_ROOT
    route == Routes.OBJECTS_ROOT || route.startsWith("objects/") -> Routes.OBJECTS_ROOT
    route == Routes.CONTACTS -> Routes.CONTACTS
    route == Routes.MATERIALS_STORAGE -> Routes.MATERIALS_STORAGE
    route == Routes.PROFILE -> Routes.PROFILE
    else -> Routes.OBJECTS_ROOT
}

@Composable
private fun MonitorNetworkConnection(syncManager: SyncManager) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
}

@Composable
private fun BottomNavigationBar(
    currentRoute: String?,
    lastSelectedRoute: String,
    isLoggedIn: Boolean,
    onItemClick: (String) -> Unit
) {
    if (!shouldShowBottomBar(currentRoute, isLoggedIn)) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF4CAF50),
                        Color(0xFF2196F3),
                        Color(0xFF9C27B0)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItems.items.forEach { item ->
                BottomNavItemView(
                    item = item,
                    isSelected = lastSelectedRoute == item.route,
                    onClick = { onItemClick(item.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun shouldShowBottomBar(currentRoute: String?, isLoggedIn: Boolean): Boolean {
    if (!isLoggedIn || currentRoute == null) return false
    if (currentRoute == Routes.AUTH) return false

    val hiddenRoutes = listOf(
        "move_project/",
        "create_project",
        "edit_project/",
        "view_project/"
    )
    return hiddenRoutes.none { currentRoute.startsWith(it) }
}

@Composable
private fun BottomNavItemView(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.icon,
            contentDescription = item.title,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.title,
                fontSize = 11.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    userPreferences: UserPreferences,
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.AUTH) {
            AuthScreen(
                navController = navController,
                viewModel = authViewModel
            )
        }

        composable(Routes.OBJECTS_ROOT) {
            ObjectsScreen(navController = navController, parentObjectId = null)
        }

        composable(
            route = Routes.OBJECTS_WITH_PARENT,
            arguments = listOf(navArgument("parentId") { type = NavType.StringType })
        ) { backStackEntry ->
            ObjectsScreen(
                navController = navController,
                parentObjectId = backStackEntry.arguments?.getString("parentId")
            )
        }

        composable(
            route = Routes.MOVE_PROJECT,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("currentObjectId") {
                    type = NavType.StringType
                    defaultValue = "none"
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val currentObjectId = backStackEntry.arguments?.getString("currentObjectId")
                ?.takeIf { it != "none" }

            MoveProjectWrapper(
                navController = navController,
                projectId = projectId,
                currentObjectId = currentObjectId
            )
        }

        composable(Routes.CONTACTS) {
            ContactsScreen(navController)
        }

        composable(Routes.MATERIALS_STORAGE) {
            PendingSharesScreen(navController)
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                navController = navController,
                userPreferences = userPreferences,
                onLogout = onLogout
            )
        }

        composable(
            route = Routes.VIEW_PROJECT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            CreateProjectScreen(
                navController = navController,
                mode = ProjectScreenMode.VIEW,
                objectId = null,
                projectId = backStackEntry.arguments?.getString("projectId")
            )
        }

        composable(
            route = Routes.CREATE_PROJECT,
            arguments = listOf(navArgument("objectId") { type = NavType.StringType })
        ) { backStackEntry ->
            CreateProjectScreen(
                navController = navController,
                mode = ProjectScreenMode.CREATE,
                objectId = backStackEntry.arguments?.getString("objectId"),
                projectId = null
            )
        }

        composable(
            route = Routes.EDIT_PROJECT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            CreateProjectScreen(
                navController = navController,
                mode = ProjectScreenMode.EDIT,
                objectId = null,
                projectId = backStackEntry.arguments?.getString("projectId")
            )
        }
    }
}

data class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val selectedIcon: ImageVector
)

object BottomNavItems {
    val items = listOf(
        BottomNavItem("Объекты", Icons.Default.Folder, Routes.OBJECTS_ROOT, Icons.Default.Folder),
        BottomNavItem("Контакты", Icons.Default.Contacts, Routes.CONTACTS, Icons.Default.Contacts),
        BottomNavItem("Общение", Icons.Default.Chat, Routes.MATERIALS_STORAGE, Icons.Default.Chat),
        BottomNavItem("Профиль", Icons.Default.Person, Routes.PROFILE, Icons.Default.Person)
    )
}