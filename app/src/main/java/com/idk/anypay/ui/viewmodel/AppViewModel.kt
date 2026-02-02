package com.idk.anypay.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idk.anypay.data.model.*
import com.idk.anypay.data.repository.SecureStorageRepository
import com.idk.anypay.service.UpiService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context: Context = application.applicationContext
    private val storageRepository = SecureStorageRepository.getInstance(context)
    private val upiService = UpiService.getInstance(context)
    
    // ==================== App State ====================
    
    sealed class AppScreen {
        object Loading : AppScreen()
        object Onboarding : AppScreen()
        object Lock : AppScreen()
        object Main : AppScreen()
    }
    
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()
    
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    // ==================== Credentials ====================
    
    val credentials: StateFlow<UserCredentials?> = storageRepository.credentials
    val isSetupComplete: StateFlow<Boolean> = storageRepository.isSetupComplete
    
    // ==================== Transactions ====================
    
    val transactions: StateFlow<List<Transaction>> = storageRepository.transactions
    
    val recentTransactions: StateFlow<List<Transaction>> = transactions.map { list ->
        list.take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // ==================== Operation State ====================
    
    val operationState = upiService.operationState
    val lastUssdMessage = upiService.lastUssdMessage
    
    // ==================== Permission State ====================
    
    private val _hasPhonePermission = MutableStateFlow(false)
    val hasPhonePermission: StateFlow<Boolean> = _hasPhonePermission.asStateFlow()
    
    private val _hasCameraPermission = MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()
    
    val isAccessibilityServiceEnabled: Boolean
        get() = upiService.isAccessibilityServiceEnabled()
    
    // ==================== Analytics ====================
    
    val totalSpent: StateFlow<Double> = transactions.map { list ->
        list.filter { it.type == TransactionType.SEND && it.status == TransactionStatus.SUCCESS }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    
    val averageTransaction: StateFlow<Double> = transactions.map { list ->
        val successfulSends = list.filter { 
            it.type == TransactionType.SEND && it.status == TransactionStatus.SUCCESS 
        }
        if (successfulSends.isEmpty()) 0.0 else successfulSends.sumOf { it.amount } / successfulSends.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    
    val categoryStats: StateFlow<Map<PaymentCategory, Pair<Int, Double>>> = transactions.map { list ->
        list.filter { it.type == TransactionType.SEND && it.status == TransactionStatus.SUCCESS }
            .groupBy { it.category }
            .mapValues { (_, txs) -> Pair(txs.size, txs.sumOf { it.amount }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    
    init {
        determineInitialScreen()
    }
    
    private fun determineInitialScreen() {
        viewModelScope.launch {
            // Check if setup is complete
            if (!storageRepository.isSetupComplete.value) {
                _currentScreen.value = AppScreen.Onboarding
            } else {
                _currentScreen.value = AppScreen.Lock
            }
        }
    }
    
    // ==================== Authentication ====================
    
    fun authenticateWithBiometric(activity: FragmentActivity, onResult: (Boolean, String?) -> Unit) {
        val biometricManager = BiometricManager.from(context)
        
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val executor = ContextCompat.getMainExecutor(activity)
                
                val biometricPrompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            _isAuthenticated.value = true
                            _currentScreen.value = AppScreen.Main
                            onResult(true, null)
                        }
                        
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            onResult(false, errString.toString())
                        }
                        
                        override fun onAuthenticationFailed() {
                            onResult(false, "Authentication failed")
                        }
                    })
                
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock AnyPay")
                    .setSubtitle("Authenticate to access your account")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()
                
                biometricPrompt.authenticate(promptInfo)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                // No biometric hardware, go directly to main
                _isAuthenticated.value = true
                _currentScreen.value = AppScreen.Main
                onResult(true, null)
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                onResult(false, "Please set up biometric authentication in Settings")
            }
            else -> {
                onResult(false, "Biometric authentication unavailable")
            }
        }
    }
    
    fun logout() {
        _isAuthenticated.value = false
        _currentScreen.value = AppScreen.Lock
    }
    
    // ==================== Onboarding ====================
    
    fun saveCredentials(credentials: UserCredentials) {
        viewModelScope.launch {
            storageRepository.saveCredentials(credentials.copy(isSetupComplete = true))
            _currentScreen.value = AppScreen.Lock
        }
    }
    
    fun updateUpiPin(newPin: String) {
        viewModelScope.launch {
            storageRepository.updateUpiPin(newPin)
        }
    }
    
    // ==================== Transactions ====================
    
    fun checkBalance() {
        val creds = credentials.value ?: return
        val transaction = upiService.checkBalance(creds)
        viewModelScope.launch {
            storageRepository.saveTransaction(transaction)
        }
    }
    
    fun sendMoney(recipient: String, amount: Double, remarks: String = "payment") {
        val creds = credentials.value ?: return
        val transaction = upiService.sendMoney(creds, recipient, amount, remarks)
        viewModelScope.launch {
            storageRepository.saveTransaction(transaction)
        }
    }
    
    fun cancelOperation() {
        upiService.cancelOperation()
    }
    
    fun resetOperationState() {
        upiService.resetState()
    }
    
    fun updateTransactionWithResult(transaction: Transaction) {
        viewModelScope.launch {
            storageRepository.updateTransaction(transaction)
            if (transaction.type == TransactionType.BALANCE_CHECK && transaction.balance != null) {
                storageRepository.saveLastBalance(transaction.balance)
            }
        }
    }
    
    // ==================== Permissions ====================
    
    fun checkPermissions() {
        _hasPhonePermission.value = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        
        _hasCameraPermission.value = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun updatePhonePermission(granted: Boolean) {
        _hasPhonePermission.value = granted
    }
    
    fun updateCameraPermission(granted: Boolean) {
        _hasCameraPermission.value = granted
    }
    
    fun openAccessibilitySettings() {
        upiService.openAccessibilitySettings()
    }
    
    fun dialUssd(ussdCode: String) {
        try {
            val encodedCode = Uri.encode(ussdCode)
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$encodedCode"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    // ==================== Clear Data ====================
    
    fun clearAllData() {
        viewModelScope.launch {
            storageRepository.clearAllData()
            _isAuthenticated.value = false
            _currentScreen.value = AppScreen.Onboarding
        }
    }
    
    fun getLastBalance(): Double {
        return storageRepository.getLastBalance()
    }
}
