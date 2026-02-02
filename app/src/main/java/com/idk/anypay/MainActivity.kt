package com.idk.anypay

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idk.anypay.ui.navigation.MainNavigation
import com.idk.anypay.ui.screens.LockScreen
import com.idk.anypay.ui.screens.OnboardingScreen
import com.idk.anypay.ui.theme.AnyPayTheme
import com.idk.anypay.ui.viewmodel.AppViewModel

class MainActivity : FragmentActivity() {
    
    private val viewModel: AppViewModel by viewModels()
    
    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        viewModel.updatePhonePermission(allGranted)
    }
    
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateCameraPermission(granted)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            AnyPayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(
                        viewModel = viewModel,
                        onRequestPhonePermissions = {
                            phonePermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CALL_PHONE,
                                    Manifest.permission.READ_PHONE_STATE
                                )
                            )
                        },
                        onRequestCameraPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onAuthenticate = {
                            viewModel.authenticateWithBiometric(this) { _, _ -> }
                        }
                    )
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions()
    }
}

@Composable
private fun AppContent(
    viewModel: AppViewModel,
    onRequestPhonePermissions: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onAuthenticate: () -> Unit
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val credentials by viewModel.credentials.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val recentTransactions by viewModel.recentTransactions.collectAsStateWithLifecycle()
    val operationState by viewModel.operationState.collectAsStateWithLifecycle()
    val lastUssdMessage by viewModel.lastUssdMessage.collectAsStateWithLifecycle()
    val hasPhonePermission by viewModel.hasPhonePermission.collectAsStateWithLifecycle()
    val hasCameraPermission by viewModel.hasCameraPermission.collectAsStateWithLifecycle()
    val totalSpent by viewModel.totalSpent.collectAsStateWithLifecycle()
    val averageTransaction by viewModel.averageTransaction.collectAsStateWithLifecycle()
    val categoryStats by viewModel.categoryStats.collectAsStateWithLifecycle()
    
    var authError by remember { mutableStateOf<String?>(null) }
    
    when (currentScreen) {
        is AppViewModel.AppScreen.Loading -> {
            // Show loading or splash
        }
        
        is AppViewModel.AppScreen.Onboarding -> {
            OnboardingScreen(
                onComplete = { creds ->
                    viewModel.saveCredentials(creds)
                }
            )
        }
        
        is AppViewModel.AppScreen.Lock -> {
            LockScreen(
                onAuthenticate = onAuthenticate,
                errorMessage = authError
            )
        }
        
        is AppViewModel.AppScreen.Main -> {
            MainNavigation(
                credentials = credentials,
                transactions = transactions,
                recentTransactions = recentTransactions,
                operationState = operationState,
                lastUssdMessage = lastUssdMessage,
                lastBalance = viewModel.getLastBalance(),
                hasPhonePermission = hasPhonePermission,
                hasCameraPermission = hasCameraPermission,
                isAccessibilityEnabled = viewModel.isAccessibilityServiceEnabled,
                totalSpent = totalSpent,
                averageTransaction = averageTransaction,
                categoryStats = categoryStats,
                onSendMoney = { recipient, amount, remarks ->
                    viewModel.sendMoney(recipient, amount, remarks)
                },
                onCheckBalance = {
                    viewModel.checkBalance()
                },
                onCancelOperation = {
                    viewModel.cancelOperation()
                },
                onResetOperation = {
                    viewModel.resetOperationState()
                },
                onUpdatePin = { newPin ->
                    viewModel.updateUpiPin(newPin)
                },
                onClearData = {
                    viewModel.clearAllData()
                },
                onRequestPhonePermissions = onRequestPhonePermissions,
                onRequestCameraPermission = onRequestCameraPermission,
                onOpenAccessibilitySettings = {
                    viewModel.openAccessibilitySettings()
                },
                onDialUssd = { code ->
                    viewModel.dialUssd(code)
                }
            )
        }
    }
}