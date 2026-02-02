package com.idk.anypay.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.idk.anypay.service.UpiService
import com.idk.anypay.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckBalanceScreen(
    operationState: UpiService.OperationState,
    lastUssdMessage: String?,
    lastBalance: Double,
    onCheckBalance: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onUpdateTransaction: ((com.idk.anypay.data.model.Transaction) -> Unit)? = null
) {
    val isProcessing = operationState is UpiService.OperationState.InProgress
    val isComplete = operationState is UpiService.OperationState.Success || 
                     operationState is UpiService.OperationState.Error
    
    // Track if we've already updated the transaction to prevent re-triggering
    var hasUpdatedTransaction by remember { mutableStateOf(false) }
    
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
                title = { Text("Check Balance") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isProcessing) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        when {
            isComplete -> {
                BalanceResultScreen(
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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Last known balance
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Last Known Balance",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "₹${String.format("%,.2f", lastBalance)}",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Info card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "How it works",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "• This will dial *99# USSD code\n" +
                                       "• Select balance enquiry option\n" +
                                       "• Enter your UPI PIN when prompted\n" +
                                       "• Balance will be displayed and saved",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = onCheckBalance,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check Balance Now")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Balance check is free and does not cost any money",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceResultScreen(
    operationState: UpiService.OperationState,
    onDone: () -> Unit
) {
    val isSuccess = operationState is UpiService.OperationState.Success
    val balance = remember(operationState) {
        (operationState as? UpiService.OperationState.Success)?.transaction?.balance
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
        
        if (isSuccess) {
            if (balance != null) {
                Text(
                    text = "Your Balance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "₹${String.format("%,.2f", balance)}",
                    style = MaterialTheme.typography.displayMedium,
                    color = SuccessGreen
                )
            } else {
                Text(
                    text = "Balance Check Complete",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Please check your SMS for balance details",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = "Failed to Check Balance",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Please try again later",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
