package com.idk.anypay.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service for intercepting and responding to USSD dialogs.
 * 
 * USSD sessions are stateful and timing-sensitive. This service implements
 * human-like delays and guards to prevent double-submission and race conditions.
 * 
 * Key timing principles:
 * - Wait after dialog change before reading (DIALOG_STABILIZE_MS)
 * - Wait after text injection before clicking send (TEXT_INJECTION_DELAY_MS)
 * - Wait after clicking send before processing next dialog (POST_SEND_COOLDOWN_MS)
 * - Never respond twice to the same dialog hash
 */
class UssdAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AnyPay-UssdService"
        
        // === TIMING CONSTANTS (tuned for USSD reliability) ===
        private const val DIALOG_STABILIZE_MS = 200L
        private const val EVENT_DEBOUNCE_MS = 100L
        private const val TEXT_INJECTION_DELAY_MS = 300L
        private const val POST_SEND_COOLDOWN_MS = 300L
        private const val MIN_SEND_INTERVAL_MS = 300L
        
        @Volatile
        var instance: UssdAccessibilityService? = null
            private set
        
        @Volatile
        var currentOperation: UssdOperation? = null
            private set
        
        @Volatile
        var lastUssdMessage: String? = null
            private set
        
        // Callbacks
        var onUssdResponse: ((String) -> Unit)? = null
        var onOperationComplete: ((Boolean, String) -> Unit)? = null
        
        // === STATE TRACKING ===
        @Volatile
        private var lastRespondedDialogHash: Int = 0
        
        @Volatile
        private var currentDialogHash: Int = 0
        
        @Volatile
        private var lastEventTime: Long = 0
        
        @Volatile
        private var lastSendTime: Long = 0
        
        @Volatile
        private var isProcessingResponse: Boolean = false
        
        @Volatile
        private var dialogStabilized: Boolean = false
        
        @Volatile
        private var pendingStabilizationJob: Runnable? = null
        
        @Volatile
        private var pendingClickJob: Runnable? = null
        
        fun startCheckBalance(bankIfsc: String, bankName: String, cardDetails: String, upiPin: String) {
            Log.d(TAG, "Starting CHECK_BALANCE operation")
            currentOperation = UssdOperation(
                type = OperationType.CHECK_BALANCE,
                bankIfsc = bankIfsc,
                bankName = bankName,
                cardDetails = cardDetails,
                upiPin = upiPin
            )
            resetState()
        }
        
        fun startSendMoney(
            bankIfsc: String, 
            bankName: String, 
            cardDetails: String, 
            upiPin: String,
            recipient: String,
            amount: String,
            remarks: String
        ) {
            Log.d(TAG, "Starting SEND_MONEY operation to $recipient amount $amount")
            currentOperation = UssdOperation(
                type = OperationType.SEND_MONEY,
                bankIfsc = bankIfsc,
                bankName = bankName,
                cardDetails = cardDetails,
                upiPin = upiPin,
                recipient = recipient,
                amount = amount,
                remarks = remarks
            )
            resetState()
        }
        
        fun startLinkBank(bankIfsc: String, bankName: String, cardDetails: String) {
            Log.d(TAG, "Starting LINK_BANK operation for $bankName")
            currentOperation = UssdOperation(
                type = OperationType.LINK_BANK,
                bankIfsc = bankIfsc,
                bankName = bankName,
                cardDetails = cardDetails
            )
            resetState()
        }
        
        fun cancelOperation() {
            Log.d(TAG, "Cancelling current operation")
            currentOperation = null
            resetState()
        }
        
        fun isServiceRunning(): Boolean = instance != null
        
        private fun resetState() {
            lastRespondedDialogHash = 0
            currentDialogHash = 0
            lastSendTime = 0
            isProcessingResponse = false
            dialogStabilized = false
            pendingStabilizationJob = null
            pendingClickJob = null
        }
    }
    
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service connected")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        currentOperation = null
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Accessibility Service destroyed")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString() ?: return
        if (!isUssdPackage(packageName)) return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                onDialogEvent()
            }
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }
    
    private fun isUssdPackage(packageName: String): Boolean {
        return packageName in listOf(
            "com.android.phone",
            "com.samsung.android.phone",
            "com.google.android.dialer",
            "com.android.server.telecom"
        )
    }
    
    private fun onDialogEvent() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastEventTime < EVENT_DEBOUNCE_MS) {
            return
        }
        lastEventTime = currentTime
        
        pendingStabilizationJob?.let { handler.removeCallbacks(it) }
        dialogStabilized = false
        
        val rootNode = rootInActiveWindow ?: return
        val ussdMessage: String?
        try {
            ussdMessage = findUssdMessage(rootNode)
        } finally {
            rootNode.recycle()
        }
        
        if (ussdMessage == null) return
        
        if (!isLikelyUssdContent(ussdMessage)) {
            Log.d(TAG, "Skipping non-USSD content: ${ussdMessage.take(50)}...")
            return
        }
        
        val newHash = ussdMessage.hashCode()
        
        if (newHash == currentDialogHash && dialogStabilized) {
            return
        }
        
        if (newHash != currentDialogHash) {
            Log.d(TAG, "New dialog detected: ${ussdMessage.take(80)}...")
            currentDialogHash = newHash
            lastUssdMessage = ussdMessage
            
            onUssdResponse?.invoke(ussdMessage)
            
            val lowerMessage = ussdMessage.lowercase()
            val isError = isErrorMessage(lowerMessage)
            Log.d(TAG, "Error check: isError=$isError for message: ${ussdMessage.take(60)}")
            if (isError) {
                Log.e(TAG, "ERROR detected - aborting operation: ${ussdMessage.take(100)}")
                onOperationComplete?.invoke(false, ussdMessage)
                currentOperation = null
                resetState()
                return
            }
        }
        
        val stabilizationJob = Runnable {
            dialogStabilized = true
            processStabilizedDialog(ussdMessage, newHash)
        }
        pendingStabilizationJob = stabilizationJob
        handler.postDelayed(stabilizationJob, DIALOG_STABILIZE_MS)
    }
    
    private fun processStabilizedDialog(ussdMessage: String, dialogHash: Int) {
        if (isProcessingResponse) {
            Log.d(TAG, "Already processing a response, skipping")
            return
        }
        
        if (dialogHash == lastRespondedDialogHash) {
            Log.d(TAG, "Already responded to this dialog (hash=$dialogHash), skipping")
            return
        }
        
        val timeSinceLastSend = System.currentTimeMillis() - lastSendTime
        if (lastSendTime > 0 && timeSinceLastSend < MIN_SEND_INTERVAL_MS) {
            Log.d(TAG, "Too soon since last send (${timeSinceLastSend}ms), waiting...")
            handler.postDelayed({
                processStabilizedDialog(ussdMessage, dialogHash)
            }, MIN_SEND_INTERVAL_MS - timeSinceLastSend + 100)
            return
        }
        
        val lowerMessage = ussdMessage.lowercase()
        
        val isComplete = isOperationComplete(lowerMessage)
        val isError = isErrorMessage(lowerMessage)
        val isSuccess = isSuccessMessage(lowerMessage)
        Log.d(TAG, "Completion check: complete=$isComplete, error=$isError, success=$isSuccess")
        
        if (isComplete) {
            Log.d(TAG, "Operation complete detected")
            Log.d(TAG, "Success = $isSuccess, message: ${ussdMessage.take(100)}")
            onOperationComplete?.invoke(isSuccess, ussdMessage)
            currentOperation = null
            
            handler.postDelayed({
                dismissUssdDialog()
            }, 500L)
            return
        }
        
        val response = determineResponse(ussdMessage)
        
        if (response != null) {
            Log.d(TAG, ">>> WILL SEND: $response (after stabilization)")
            isProcessingResponse = true
            sendResponseWithTiming(response, dialogHash)
        }
    }
    
    private fun sendResponseWithTiming(response: String, dialogHash: Int) {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            isProcessingResponse = false
            return
        }
        
        try {
            val inputField = findInputField(rootNode)
            
            if (inputField != null) {
                if (!inputField.isFocused) {
                    Log.d(TAG, "Input field not focused, requesting focus...")
                    inputField.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    
                    handler.postDelayed({
                        injectTextAndSend(response, dialogHash)
                    }, 200L)
                    inputField.recycle()
                    return
                }
                
                inputField.recycle()
                injectTextAndSend(response, dialogHash)
            } else {
                val okButton = findButtonByText(rootNode, listOf("OK", "Dismiss", "Close"))
                if (okButton != null) {
                    Log.d(TAG, "Clicking OK button")
                    okButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    okButton.recycle()
                    lastRespondedDialogHash = dialogHash
                    lastSendTime = System.currentTimeMillis()
                }
                
                handler.postDelayed({
                    isProcessingResponse = false
                }, POST_SEND_COOLDOWN_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending response", e)
            isProcessingResponse = false
        } finally {
            rootNode.recycle()
        }
    }
    
    private fun injectTextAndSend(response: String, dialogHash: Int) {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            isProcessingResponse = false
            return
        }
        
        try {
            val inputField = findInputField(rootNode)
            if (inputField == null) {
                Log.e(TAG, "Input field not found for text injection")
                isProcessingResponse = false
                return
            }
            
            Log.d(TAG, "Injecting text: $response")
            
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                response
            )
            inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            inputField.recycle()
            
            lastRespondedDialogHash = dialogHash
            
            val clickJob = Runnable {
                pendingClickJob = null
                clickSendButtonSafely()
            }
            pendingClickJob = clickJob
            handler.postDelayed(clickJob, TEXT_INJECTION_DELAY_MS)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting text", e)
            isProcessingResponse = false
        } finally {
            rootNode.recycle()
        }
    }
    
    private fun clickSendButtonSafely() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            isProcessingResponse = false
            return
        }
        
        try {
            val sendButton = findButtonByText(rootNode, listOf("Send", "Reply", "OK", "Submit", "Confirm"))
            if (sendButton != null) {
                Log.d(TAG, "Clicking send button")
                sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                sendButton.recycle()
                lastSendTime = System.currentTimeMillis()
            } else {
                Log.w(TAG, "Send button not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking send", e)
        } finally {
            rootNode.recycle()
            
            handler.postDelayed({
                isProcessingResponse = false
                Log.d(TAG, "Processing lock released, ready for next dialog")
            }, POST_SEND_COOLDOWN_MS)
        }
    }
    
    private fun dismissUssdDialog() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.d(TAG, "No dialog to dismiss")
            return
        }
        
        try {
            val dismissButton = findButtonByText(rootNode, listOf("Cancel", "OK", "Close", "Dismiss", "Done"))
            if (dismissButton != null) {
                Log.d(TAG, "Dismissing USSD dialog")
                dismissButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                dismissButton.recycle()
            } else {
                Log.d(TAG, "No dismiss button found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing dialog", e)
        } finally {
            rootNode.recycle()
        }
    }
    
    private fun determineResponse(message: String): String? {
        val operation = currentOperation ?: return null
        val lowerMessage = message.lowercase()
        
        Log.d(TAG, "Analyzing message for ${operation.type}, step ${operation.step}")
        
        val hasNumberedOptions = message.contains("1.") && 
            (message.contains("2.") || message.contains("3."))
        
        return when (operation.type) {
            OperationType.CHECK_BALANCE -> handleCheckBalance(operation, message, lowerMessage, hasNumberedOptions)
            OperationType.SEND_MONEY -> handleSendMoney(operation, message, lowerMessage, hasNumberedOptions)
            OperationType.LINK_BANK -> handleLinkBank(operation, message, lowerMessage, hasNumberedOptions)
        }
    }
    
    private fun isErrorMessage(lowerMessage: String): Boolean {
        return lowerMessage.contains("incorrect") ||
               lowerMessage.contains("invalid") ||
               lowerMessage.contains("failed") ||
               lowerMessage.contains("declined") ||
               lowerMessage.contains("not registered") ||
               lowerMessage.contains("connection problem") ||
               lowerMessage.contains("try again") ||
               lowerMessage.contains("unable to") ||
               lowerMessage.contains("could not") ||
               lowerMessage.contains("cannot") ||
               lowerMessage.contains("blocked") ||
               lowerMessage.contains("expired") ||
               lowerMessage.contains("insufficient") ||
               lowerMessage.contains("invalid mmi") ||
               lowerMessage.contains("payment address incorrect") ||
               (lowerMessage.contains("beneficiary") && lowerMessage.contains("incorrect"))
    }
    
    private fun isSuccessMessage(lowerMessage: String): Boolean {
        if (isErrorMessage(lowerMessage)) return false
        
        return lowerMessage.contains("success") ||
               lowerMessage.contains("completed") ||
               lowerMessage.contains("balance is") ||
               lowerMessage.contains("available balance") ||
               (lowerMessage.contains("rs") && lowerMessage.contains("balance") && !lowerMessage.contains("insufficient"))
    }
    
    private fun isOperationComplete(lowerMessage: String): Boolean {
        return isSuccessMessage(lowerMessage) || isErrorMessage(lowerMessage)
    }
    
    private fun handleCheckBalance(
        operation: UssdOperation,
        message: String,
        lowerMessage: String,
        hasNumberedOptions: Boolean
    ): String? {
        if (hasNumberedOptions && !operation.selectedMenuOption) {
            val balanceOption = findMenuOption(message, listOf("check balance", "bal enq", "balance enquiry", "know balance"))
            if (balanceOption != null) {
                Log.d(TAG, "Selecting balance option: $balanceOption")
                operation.selectedMenuOption = true
                operation.step++
                return balanceOption
            }
        }
        
        if (!hasNumberedOptions) {
            if (isAskingForPin(lowerMessage) && !operation.sentPin) {
                Log.d(TAG, "PIN prompt detected")
                operation.sentPin = true
                operation.step++
                return operation.upiPin
            }
            
            if (isAskingForBank(lowerMessage) && !operation.sentBank) {
                Log.d(TAG, "Bank prompt detected")
                operation.sentBank = true
                operation.step++
                return operation.bankInput
            }
            
            if (isAskingForCard(lowerMessage) && !operation.sentCard) {
                Log.d(TAG, "Card prompt detected")
                operation.sentCard = true
                operation.step++
                return operation.cardDetails
            }
        }
        
        return null
    }
    
    private fun handleSendMoney(
        operation: UssdOperation,
        message: String,
        lowerMessage: String,
        hasNumberedOptions: Boolean
    ): String? {
        if (hasNumberedOptions) {
            if (!operation.selectedMenuOption) {
                val sendOption = findMenuOption(message, listOf("send money", "transfer", "pay"))
                if (sendOption != null) {
                    Log.d(TAG, "Selecting send option: $sendOption")
                    operation.selectedMenuOption = true
                    operation.step++
                    return sendOption
                }
            }
            
            if (operation.selectedMenuOption && !operation.selectedPaymentMethod) {
                if (lowerMessage.contains("send money to") || 
                    lowerMessage.contains("mobile no") ||
                    lowerMessage.contains("upi id")) {
                    
                    val isUpiId = operation.recipient.contains("@")
                    val isMobileNumber = operation.recipient.matches(Regex("^\\d{10}$"))
                    Log.d(TAG, "Recipient type: isUpiId=$isUpiId, isMobile=$isMobileNumber")
                    
                    val option = when {
                        isUpiId -> findMenuOption(message, listOf("upi id", "vpa")) ?: "3"
                        isMobileNumber -> findMenuOption(message, listOf("mobile no", "mobile number")) ?: "1"
                        else -> "1"
                    }
                    
                    Log.d(TAG, "Selecting payment method: $option")
                    operation.selectedPaymentMethod = true
                    operation.step++
                    return option
                }
            }
        }
        
        if (!hasNumberedOptions) {
            if (isAskingForPin(lowerMessage) && !operation.sentPin) {
                Log.d(TAG, "PIN prompt detected")
                operation.sentPin = true
                operation.step++
                return operation.upiPin
            }
            
            if (isAskingForBank(lowerMessage) && !operation.sentBank) {
                Log.d(TAG, "Bank prompt detected")
                operation.sentBank = true
                operation.step++
                return operation.bankInput
            }
            
            if (isAskingForCard(lowerMessage) && !operation.sentCard) {
                Log.d(TAG, "Card prompt detected")
                operation.sentCard = true
                operation.step++
                return operation.cardDetails
            }
            
            if (isAskingForRecipient(lowerMessage) && !operation.sentRecipient) {
                if (message.contains(operation.recipient)) {
                    Log.d(TAG, "Recipient already echoed in dialog, skipping")
                    return null
                }
                Log.d(TAG, "Recipient prompt detected")
                operation.sentRecipient = true
                operation.step++
                return operation.recipient
            }
            
            if (isAskingForAmount(lowerMessage) && !operation.sentAmount) {
                if (message.contains(operation.amount)) {
                    Log.d(TAG, "Amount already echoed in dialog, skipping")
                    return null
                }
                Log.d(TAG, "Amount prompt detected")
                operation.sentAmount = true
                operation.step++
                return operation.amount
            }
            
            if (isAskingForRemarks(lowerMessage) && !operation.sentRemarks) {
                Log.d(TAG, "Remarks prompt detected")
                operation.sentRemarks = true
                operation.step++
                return operation.remarks
            }
        }
        
        return null
    }
    
    private fun handleLinkBank(
        operation: UssdOperation,
        message: String,
        lowerMessage: String,
        hasNumberedOptions: Boolean
    ): String? {
        if (isAskingForBank(lowerMessage) && !operation.sentBank) {
            Log.d(TAG, "Bank prompt detected")
            operation.sentBank = true
            operation.step++
            return operation.bankInput
        }
        
        if (isAskingForCard(lowerMessage) && !operation.sentCard) {
            Log.d(TAG, "Card prompt detected")
            operation.sentCard = true
            operation.step++
            return operation.cardDetails
        }
        
        if (hasNumberedOptions && !operation.selectedMenuOption) {
            val profileOption = findMenuOption(message, listOf("my profile", "profile", "settings", "my account"))
            if (profileOption != null) {
                Log.d(TAG, "Selecting profile option: $profileOption")
                operation.selectedMenuOption = true
                operation.step++
                return profileOption
            }
        }
        
        if (hasNumberedOptions && operation.selectedMenuOption && !operation.selectedPaymentMethod) {
            val changeBankOption = findMenuOption(message, listOf("change bank", "link bank", "bank account"))
            if (changeBankOption != null) {
                Log.d(TAG, "Selecting change bank option: $changeBankOption")
                operation.selectedPaymentMethod = true
                operation.step++
                return changeBankOption
            }
        }
        
        return null
    }
    
    private fun isAskingForPin(lowerMessage: String): Boolean {
        return lowerMessage.contains("upi pin") ||
               lowerMessage.contains("enter pin") ||
               lowerMessage.contains("enter your pin") ||
               lowerMessage.contains("m-pin") ||
               lowerMessage.contains("mpin") ||
               lowerMessage.contains("4 digit") ||
               lowerMessage.contains("6 digit") ||
               (lowerMessage.contains("enter") && lowerMessage.contains("pin") && !lowerMessage.contains("upi id"))
    }
    
    private fun isAskingForBank(lowerMessage: String): Boolean {
        return lowerMessage.contains("enter your bank") ||
               lowerMessage.contains("bank's name") ||
               lowerMessage.contains("bank ifsc") ||
               lowerMessage.contains("first 4 letters")
    }
    
    private fun isAskingForCard(lowerMessage: String): Boolean {
        return lowerMessage.contains("last 6") ||
               lowerMessage.contains("debit card") ||
               lowerMessage.contains("card number") ||
               lowerMessage.contains("card details")
    }
    
    private fun isAskingForRecipient(lowerMessage: String): Boolean {
        return lowerMessage.contains("mobile") ||
               lowerMessage.contains("vpa") ||
               lowerMessage.contains("upi id") ||
               lowerMessage.contains("beneficiary") ||
               lowerMessage.contains("payee") ||
               lowerMessage.contains("enter number") ||
               lowerMessage.contains("recipient")
    }
    
    private fun isAskingForAmount(lowerMessage: String): Boolean {
        return lowerMessage.contains("enter amount") ||
               lowerMessage.contains("amount to") ||
               lowerMessage.contains("how much")
    }
    
    private fun isAskingForRemarks(lowerMessage: String): Boolean {
        return lowerMessage.contains("remark") ||
               lowerMessage.contains("comment") ||
               lowerMessage.contains("note")
    }
    
    private fun findMenuOption(message: String, keywords: List<String>): String? {
        val lines = message.split("\n")
        for (line in lines) {
            val lowerLine = line.lowercase()
            for (keyword in keywords) {
                if (lowerLine.contains(keyword)) {
                    val match = Regex("^(\\d+)[.)\\s]").find(line.trim())
                    if (match != null) {
                        return match.groupValues[1]
                    }
                }
            }
        }
        return null
    }
    
    private fun findUssdMessage(node: AccessibilityNodeInfo): String? {
        val messages = mutableListOf<String>()
        findTextNodes(node, messages)
        
        val filteredMessages = messages.filter { msg ->
            msg.isNotBlank() && 
            !msg.equals("OK", ignoreCase = true) &&
            !msg.equals("Cancel", ignoreCase = true) &&
            !msg.equals("Send", ignoreCase = true) &&
            !msg.equals("Reply", ignoreCase = true) &&
            msg.length > 2 &&
            !isNonUssdContent(msg)
        }
        
        return if (filteredMessages.isNotEmpty()) {
            filteredMessages.joinToString("\n")
        } else null
    }
    
    private fun isNonUssdContent(msg: String): Boolean {
        val lowerMsg = msg.lowercase()
        
        if (lowerMsg == "search contacts" || 
            lowerMsg == "contacts" ||
            lowerMsg.startsWith("search ") ||
            lowerMsg == "india" ||
            Regex("^[a-z]{3} \\d{1,2}$").matches(lowerMsg)) {
            return true
        }
        
        if (Regex("^\\+?\\d{2}\\s?\\d{4,5}\\s?\\d{4,5}$").matches(msg.trim())) {
            return true
        }
        
        if (msg.length < 5 && !Regex("^\\d+\\.").matches(msg)) {
            val genericWords = setOf("call", "chat", "video", "info", "back", "next", "done")
            if (lowerMsg in genericWords) return true
        }
        
        return false
    }
    
    private fun isLikelyUssdContent(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        val ussdIndicators = listOf(
            "1.", "2.", "3.",
            "select option",
            "bank", "upi", "pin",
            "account", "balance",
            "send money", "transfer",
            "request money",
            "enter amount", "amount",
            "mobile", "vpa",
            "success", "fail", "completed",
            "carrier info",
            "enter your", "enter the",
            "debit card", "last 6",
            "ifsc",
            "incorrect", "invalid", "declined",
            "beneficiary", "payment address"
        )
        
        for (indicator in ussdIndicators) {
            if (lowerMessage.contains(indicator)) {
                return true
            }
        }
        
        return false
    }
    
    private fun findTextNodes(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            texts.add(text)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findTextNodes(child, texts)
                child.recycle()
            }
        }
    }
    
    private fun findInputField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.toString()?.contains("EditText") == true) {
            return AccessibilityNodeInfo.obtain(node)
        }
        
        if (node.isEditable) {
            return AccessibilityNodeInfo.obtain(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findInputField(child)
                child.recycle()
                if (result != null) return result
            }
        }
        
        return null
    }
    
    private fun findButtonByText(node: AccessibilityNodeInfo, buttonTexts: List<String>): AccessibilityNodeInfo? {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        
        if (node.isClickable) {
            for (buttonText in buttonTexts) {
                if (text.equals(buttonText, ignoreCase = true) ||
                    contentDesc.equals(buttonText, ignoreCase = true)) {
                    return AccessibilityNodeInfo.obtain(node)
                }
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findButtonByText(child, buttonTexts)
                child.recycle()
                if (result != null) return result
            }
        }
        
        return null
    }
}

enum class OperationType {
    CHECK_BALANCE,
    SEND_MONEY,
    LINK_BANK
}

data class UssdOperation(
    val type: OperationType,
    val bankIfsc: String = "",
    val bankName: String = "",
    val cardDetails: String = "",
    val upiPin: String = "",
    val recipient: String = "",
    val amount: String = "",
    val remarks: String = "payment",
    var step: Int = 0,
    var selectedMenuOption: Boolean = false,
    var selectedPaymentMethod: Boolean = false,
    var sentRecipient: Boolean = false,
    var sentAmount: Boolean = false,
    var sentPin: Boolean = false,
    var sentBank: Boolean = false,
    var sentCard: Boolean = false,
    var sentRemarks: Boolean = false
) {
    val bankInput: String
        get() = if (bankIfsc.length >= 4) bankIfsc.take(4).uppercase() else bankName
}
