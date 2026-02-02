package com.idk.anypay.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.idk.anypay.data.model.PaymentCategory
import com.idk.anypay.data.model.Transaction
import com.idk.anypay.data.model.UpiPaymentInfo
import com.idk.anypay.data.model.UserCredentials
import com.idk.anypay.service.UpiService
import com.idk.anypay.ui.screens.*
import kotlinx.coroutines.flow.StateFlow

sealed class Screen(val route: String, val title: String, val icon: ImageVector?) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object History : Screen("history", "History", Icons.Default.History)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object SendMoney : Screen("send_money", "Send Money", null)
    object CheckBalance : Screen("check_balance", "Check Balance", null)
    object QrScanner : Screen("qr_scanner", "Scan QR", null)
}

val bottomNavItems = listOf(Screen.Home, Screen.History, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    credentials: UserCredentials?,
    transactions: List<Transaction>,
    recentTransactions: List<Transaction>,
    operationState: UpiService.OperationState,
    lastUssdMessage: String?,
    lastBalance: Double,
    hasPhonePermission: Boolean,
    hasCameraPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    hasOverlayPermission: Boolean = true,
    totalSpent: Double,
    averageTransaction: Double,
    categoryStats: Map<PaymentCategory, Pair<Int, Double>>,
    onSendMoney: (String, Double, String) -> Unit,
    onCheckBalance: () -> Unit,
    onCancelOperation: () -> Unit,
    onResetOperation: () -> Unit,
    onUpdatePin: (String) -> Unit,
    onClearData: () -> Unit,
    onRequestPhonePermissions: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestOverlayPermission: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }
    
    // State for send money form (to persist when going to QR scanner)
    var pendingRecipient by remember { mutableStateOf("") }
    var pendingAmount by remember { mutableStateOf("") }
    var pendingRemarks by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            if (showBottomBar) {
                TopAppBar(
                    title = { 
                        Text(
                            text = bottomNavItems.find { it.route == currentRoute }?.title ?: "AnyPay"
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { 
                                screen.icon?.let { Icon(it, contentDescription = screen.title) }
                            },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Home.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    recentTransactions = recentTransactions,
                    lastBalance = lastBalance,
                    hasPhonePermission = hasPhonePermission,
                    hasOverlayPermission = hasOverlayPermission,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    onSendMoney = { 
                        pendingRecipient = ""
                        pendingAmount = ""
                        pendingRemarks = ""
                        navController.navigate(Screen.SendMoney.route) 
                    },
                    onCheckBalance = { 
                        if (hasPhonePermission && isAccessibilityEnabled) {
                            navController.navigate(Screen.CheckBalance.route)
                        } else if (!hasPhonePermission) {
                            onRequestPhonePermissions()
                        } else {
                            onOpenAccessibilitySettings()
                        }
                    },
                    onViewHistory = { 
                        navController.navigate(Screen.History.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onRequestPermissions = onRequestPhonePermissions,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onRequestOverlayPermission = onRequestOverlayPermission
                )
            }
            
            composable(Screen.History.route) {
                HistoryScreen(
                    transactions = transactions,
                    totalSpent = totalSpent,
                    averageTransaction = averageTransaction,
                    categoryStats = categoryStats
                )
            }
            
            composable(Screen.Settings.route) {
                SettingsScreen(
                    credentials = credentials,
                    onUpdatePin = onUpdatePin,
                    onClearData = onClearData,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
            }
            
            composable(Screen.SendMoney.route) {
                SendMoneyScreen(
                    operationState = operationState,
                    lastUssdMessage = lastUssdMessage,
                    initialRecipient = pendingRecipient,
                    initialAmount = pendingAmount,
                    initialRemarks = pendingRemarks,
                    onSendMoney = { recipient, amount, remarks ->
                        if (hasPhonePermission && isAccessibilityEnabled) {
                            onSendMoney(recipient, amount, remarks)
                        } else if (!hasPhonePermission) {
                            onRequestPhonePermissions()
                        } else {
                            onOpenAccessibilitySettings()
                        }
                    },
                    onCancel = onCancelOperation,
                    onScanQr = {
                        if (hasCameraPermission) {
                            navController.navigate(Screen.QrScanner.route)
                        } else {
                            onRequestCameraPermission()
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onReset = onResetOperation
                )
            }
            
            composable(Screen.CheckBalance.route) {
                CheckBalanceScreen(
                    operationState = operationState,
                    lastUssdMessage = lastUssdMessage,
                    lastBalance = lastBalance,
                    onCheckBalance = {
                        if (hasPhonePermission && isAccessibilityEnabled) {
                            onCheckBalance()
                        }
                    },
                    onCancel = onCancelOperation,
                    onBack = { navController.popBackStack() },
                    onReset = onResetOperation
                )
            }
            
            composable(Screen.QrScanner.route) {
                QrScannerScreen(
                    onScanResult = { paymentInfo ->
                        pendingRecipient = paymentInfo.upiId
                        pendingAmount = paymentInfo.amount?.toString() ?: ""
                        pendingRemarks = paymentInfo.note.ifEmpty { paymentInfo.name }
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
