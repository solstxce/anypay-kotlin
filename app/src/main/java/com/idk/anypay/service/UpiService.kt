package com.idk.anypay.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.idk.anypay.data.model.Transaction
import com.idk.anypay.data.model.TransactionStatus
import com.idk.anypay.data.model.TransactionType
import com.idk.anypay.data.model.UserCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-level UPI service that wraps USSD accessibility service operations.
 */
class UpiService(private val context: Context) {
    
    companion object {
        private const val TAG = "AnyPay-UpiService"
        
        @Volatile
        private var instance: UpiService? = null
        
        fun getInstance(context: Context): UpiService {
            return instance ?: synchronized(this) {
                instance ?: UpiService(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    // Current operation state
    sealed class OperationState {
        object Idle : OperationState()
        data class InProgress(val message: String = "Processing...") : OperationState()
        data class Success(val message: String, val transaction: Transaction?) : OperationState()
        data class Error(val message: String) : OperationState()
    }
    
    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()
    
    private val _lastUssdMessage = MutableStateFlow<String?>(null)
    val lastUssdMessage: StateFlow<String?> = _lastUssdMessage.asStateFlow()
    
    // Current pending transaction (for updating status later)
    private var pendingTransaction: Transaction? = null
    
    init {
        // Set up callbacks from accessibility service
        UssdAccessibilityService.onUssdResponse = { message ->
            Log.d(TAG, "USSD Response: ${message.take(100)}...")
            _lastUssdMessage.value = message
            _operationState.value = OperationState.InProgress(message)
        }
        
        UssdAccessibilityService.onOperationComplete = { success, message ->
            Log.d(TAG, "Operation complete: success=$success")
            handleOperationComplete(success, message)
        }
    }
    
    /**
     * Check if accessibility service is enabled
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return UssdAccessibilityService.isServiceRunning()
    }
    
    /**
     * Open accessibility settings for user to enable the service
     */
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    /**
     * Check balance using USSD *99#
     */
    fun checkBalance(credentials: UserCredentials): Transaction {
        Log.d(TAG, "Starting check balance operation")
        
        val transaction = Transaction(
            type = TransactionType.BALANCE_CHECK,
            amount = 0.0,
            status = TransactionStatus.PENDING,
            message = "Checking balance..."
        )
        
        pendingTransaction = transaction
        _operationState.value = OperationState.InProgress("Initiating balance check...")
        
        UssdAccessibilityService.startCheckBalance(
            bankIfsc = credentials.bankIfsc,
            bankName = credentials.bankName,
            cardDetails = credentials.formattedCardDetails,
            upiPin = credentials.upiPin
        )
        
        return transaction
    }
    
    /**
     * Send money using USSD *99#
     */
    fun sendMoney(
        credentials: UserCredentials,
        recipient: String,
        amount: Double,
        remarks: String = "payment"
    ): Transaction {
        Log.d(TAG, "Starting send money operation: $recipient, amount=$amount")
        
        val transaction = Transaction(
            type = TransactionType.SEND,
            amount = amount,
            recipientVpa = recipient,
            status = TransactionStatus.PENDING,
            message = remarks
        )
        
        pendingTransaction = transaction
        _operationState.value = OperationState.InProgress("Initiating payment...")
        
        UssdAccessibilityService.startSendMoney(
            bankIfsc = credentials.bankIfsc,
            bankName = credentials.bankName,
            cardDetails = credentials.formattedCardDetails,
            upiPin = credentials.upiPin,
            recipient = recipient,
            amount = amount.toLong().toString(), // USSD expects integer amounts
            remarks = remarks
        )
        
        return transaction
    }
    
    /**
     * Link bank account using USSD *99#
     */
    fun linkBank(credentials: UserCredentials) {
        Log.d(TAG, "Starting link bank operation")
        
        _operationState.value = OperationState.InProgress("Linking bank account...")
        
        UssdAccessibilityService.startLinkBank(
            bankIfsc = credentials.bankIfsc,
            bankName = credentials.bankName,
            cardDetails = credentials.formattedCardDetails
        )
    }
    
    /**
     * Cancel current operation
     */
    fun cancelOperation() {
        Log.d(TAG, "Cancelling current operation")
        UssdAccessibilityService.cancelOperation()
        _operationState.value = OperationState.Idle
        pendingTransaction = null
    }
    
    /**
     * Reset state to idle
     */
    fun resetState() {
        _operationState.value = OperationState.Idle
        _lastUssdMessage.value = null
        pendingTransaction = null
    }
    
    /**
     * Handle operation completion from accessibility service
     */
    private fun handleOperationComplete(success: Boolean, message: String) {
        val transaction = pendingTransaction?.copy(
            status = if (success) TransactionStatus.SUCCESS else TransactionStatus.FAILED,
            message = message,
            referenceId = extractReferenceId(message),
            balance = extractBalance(message)
        )
        
        _operationState.value = if (success) {
            OperationState.Success(message, transaction)
        } else {
            OperationState.Error(message)
        }
        
        pendingTransaction = null
    }
    
    /**
     * Extract reference ID from USSD response
     */
    private fun extractReferenceId(message: String): String {
        // Common patterns for reference IDs in USSD responses
        val patterns = listOf(
            Regex("(?:ref|reference|txn|transaction)\\s*(?:no|id|number)?[:\\s]*([A-Z0-9]+)", RegexOption.IGNORE_CASE),
            Regex("([0-9]{12,})")  // Long numeric ID
        )
        
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return ""
    }
    
    /**
     * Extract balance amount from USSD response
     */
    private fun extractBalance(message: String): Double? {
        // Common patterns for balance in USSD responses
        val patterns = listOf(
            Regex("(?:balance|bal)[:\\s]*(?:rs\\.?|inr)?\\s*([0-9,]+\\.?[0-9]*)", RegexOption.IGNORE_CASE),
            Regex("(?:rs\\.?|inr)\\s*([0-9,]+\\.?[0-9]*)\\s*(?:available|balance)?", RegexOption.IGNORE_CASE),
            Regex("available[:\\s]*(?:rs\\.?|inr)?\\s*([0-9,]+\\.?[0-9]*)", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null) {
                val balanceStr = match.groupValues[1].replace(",", "")
                return balanceStr.toDoubleOrNull()
            }
        }
        
        return null
    }
}
