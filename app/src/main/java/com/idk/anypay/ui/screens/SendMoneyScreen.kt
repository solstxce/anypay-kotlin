package com.idk.anypay.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.idk.anypay.data.model.UpiPaymentInfo
import com.idk.anypay.service.UpiService
import com.idk.anypay.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendMoneyScreen(
    operationState: UpiService.OperationState,
    lastUssdMessage: String?,
    initialRecipient: String = "",
    initialAmount: String = "",
    initialRemarks: String = "",
    onSendMoney: (String, Double, String) -> Unit,
    onCancel: () -> Unit,
    onScanQr: () -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onUpdateTransaction: ((com.idk.anypay.data.model.Transaction) -> Unit)? = null
) {
    var recipient by remember { mutableStateOf(initialRecipient) }
    var amount by remember { mutableStateOf(initialAmount) }
    var remarks by remember { mutableStateOf(initialRemarks) }
    
    // Update when initial values change (from QR scan)
    LaunchedEffect(initialRecipient, initialAmount, initialRemarks) {
        if (initialRecipient.isNotEmpty()) recipient = initialRecipient
        if (initialAmount.isNotEmpty()) amount = initialAmount
        if (initialRemarks.isNotEmpty()) remarks = initialRemarks
    }
    
    // Track if we've already updated the transaction to prevent re-triggering
    var hasUpdatedTransaction by remember { mutableStateOf(false) }
    
    // Validation
    val isRecipientValid = UpiPaymentInfo.isValidRecipient(recipient)
    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val isAmountValid = amountValue > 0 && amountValue <= 100000
    val canSend = isRecipientValid && isAmountValid && operationState is UpiService.OperationState.Idle
    
    val isProcessing = operationState is UpiService.OperationState.InProgress
    val isComplete = operationState is UpiService.OperationState.Success || 
                     operationState is UpiService.OperationState.Error
    
    // Update transaction when operation completes (only once)
    LaunchedEffect(isComplete) {
        if (isComplete && !hasUpdatedTransaction) {
            if (operationState is UpiService.OperationState.Success) {
                operationState.transaction?.let { 
                    onUpdateTransaction?.invoke(it)
                    hasUpdatedTransaction = true
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Money") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isProcessing) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onScanQr, enabled = !isProcessing) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        when {
            isComplete -> {
                CompletionScreen(
                    operationState = operationState,
                    onDone = {
                        onReset()
                        onBack()
                    }
                )
            }
            isProcessing -> {
                ProcessingScreen(
                    message = (operationState as? UpiService.OperationState.InProgress)?.message ?: "Processing...",
                    lastUssdMessage = lastUssdMessage,
                    onCancel = onCancel,
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Recipient Input
                    OutlinedTextField(
                        value = recipient,
                        onValueChange = { recipient = it },
                        label = { Text("UPI ID or Mobile Number") },
                        placeholder = { Text("example@upi or 9876543210") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = onScanQr) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                            }
                        },
                        isError = recipient.isNotEmpty() && !isRecipientValid,
                        supportingText = {
                            if (recipient.isNotEmpty() && !isRecipientValid) {
                                Text("Enter valid UPI ID (user@provider) or 10-digit mobile")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Amount Input
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { 
                            if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                amount = it
                            }
                        },
                        label = { Text("Amount") },
                        placeholder = { Text("0.00") },
                        prefix = { Text("₹ ") },
                        leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = amount.isNotEmpty() && !isAmountValid,
                        supportingText = {
                            when {
                                amount.isNotEmpty() && amountValue <= 0 -> Text("Enter a valid amount")
                                amount.isNotEmpty() && amountValue > 100000 -> Text("Maximum amount is ₹1,00,000")
                                else -> Text("Maximum: ₹1,00,000 per transaction")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Remarks Input
                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { if (it.length <= 50) remarks = it },
                        label = { Text("Remarks (Optional)") },
                        placeholder = { Text("What's this for?") },
                        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                        supportingText = { Text("${remarks.length}/50 characters") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Warning Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = WarningYellow.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Please verify details",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Transactions via USSD cannot be reversed. Double-check the recipient and amount before proceeding.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Send Button
                    Button(
                        onClick = {
                            onSendMoney(recipient, amountValue, remarks.ifEmpty { "payment" })
                        },
                        enabled = canSend,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send ₹${if (amountValue > 0) String.format("%,.2f", amountValue) else "0.00"}")
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessingScreen(
    message: String,
    lastUssdMessage: String?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Processing USSD Request",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (lastUssdMessage != null) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "USSD Response",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = lastUssdMessage.take(200),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cancel")
        }
    }
}

@Composable
fun CompletionScreen(
    operationState: UpiService.OperationState,
    onDone: () -> Unit
) {
    val isSuccess = operationState is UpiService.OperationState.Success
    val message = when (operationState) {
        is UpiService.OperationState.Success -> operationState.message
        is UpiService.OperationState.Error -> operationState.message
        else -> ""
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (isSuccess) SuccessGreen.copy(alpha = 0.1f) else ErrorRed.copy(alpha = 0.1f),
            modifier = Modifier.size(100.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isSuccess) SuccessGreen else ErrorRed,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (isSuccess) "Payment Successful!" else "Payment Failed",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (!isSuccess) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = ErrorRed.copy(alpha = 0.1f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Error Details",
                        style = MaterialTheme.typography.labelMedium,
                        color = ErrorRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message.take(200),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}
