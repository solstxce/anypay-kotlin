package com.idk.anypay.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.idk.anypay.data.model.*
import com.idk.anypay.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentTransactions: List<Transaction>,
    lastBalance: Double,
    hasPhonePermission: Boolean,
    isAccessibilityEnabled: Boolean,
    hasOverlayPermission: Boolean = true,
    onSendMoney: () -> Unit,
    onCheckBalance: () -> Unit,
    onViewHistory: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestOverlayPermission: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Service Status
        if (!hasPhonePermission || !isAccessibilityEnabled || !hasOverlayPermission) {
            item {
                ServiceStatusCard(
                    hasPhonePermission = hasPhonePermission,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    hasOverlayPermission = hasOverlayPermission,
                    onRequestPermissions = onRequestPermissions,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onRequestOverlayPermission = onRequestOverlayPermission
                )
            }
        }
        
        // Balance Card
        item {
            BalanceCard(
                balance = lastBalance,
                onRefresh = onCheckBalance
            )
        }
        
        // Quick Actions
        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionCard(
                    title = "Send Money",
                    icon = Icons.Default.Send,
                    color = SendRed,
                    onClick = onSendMoney,
                    modifier = Modifier.weight(1f)
                )
                
                QuickActionCard(
                    title = "Check Balance",
                    icon = Icons.Default.AccountBalance,
                    color = BalanceBlue,
                    onClick = onCheckBalance,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Recent Transactions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium
                )
                
                TextButton(onClick = onViewHistory) {
                    Text("View All")
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        if (recentTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Your transactions will appear here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentTransactions) { transaction ->
                TransactionItem(transaction = transaction)
            }
        }
    }
}

@Composable
private fun ServiceStatusCard(
    hasPhonePermission: Boolean,
    isAccessibilityEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
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
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Setup Required",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (!hasPhonePermission) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Phone Permissions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Required for USSD calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Grant")
                    }
                }
                
                if (!isAccessibilityEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            if (!isAccessibilityEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility Service",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Required for USSD automation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Enable")
                    }
                }
            }
            
            if (!hasOverlayPermission) {
                if (!hasPhonePermission || !isAccessibilityEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Overlay Permission",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "For seamless USSD experience",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                    Button(
                        onClick = onRequestOverlayPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Allow")
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Double,
    onRefresh: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Available Balance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh Balance",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            Text(
                text = "₹${String.format("%,.2f", balance)}",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Tap refresh to check current balance via USSD",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    onClick: (() -> Unit)? = null
) {
    val containerModifier = if (onClick != null) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.fillMaxWidth()
    }
    
    Card(
        modifier = containerModifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transaction type icon
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when (transaction.type) {
                    TransactionType.SEND -> SendRed.copy(alpha = 0.1f)
                    TransactionType.RECEIVE -> ReceiveGreen.copy(alpha = 0.1f)
                    TransactionType.BALANCE_CHECK -> BalanceBlue.copy(alpha = 0.1f)
                },
                modifier = Modifier.size(44.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        when (transaction.type) {
                            TransactionType.SEND -> Icons.Default.ArrowUpward
                            TransactionType.RECEIVE -> Icons.Default.ArrowDownward
                            TransactionType.BALANCE_CHECK -> Icons.Default.AccountBalance
                        },
                        contentDescription = null,
                        tint = when (transaction.type) {
                            TransactionType.SEND -> SendRed
                            TransactionType.RECEIVE -> ReceiveGreen
                            TransactionType.BALANCE_CHECK -> BalanceBlue
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Transaction details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when (transaction.type) {
                        TransactionType.SEND -> transaction.recipientVpa.ifEmpty { "Payment" }
                        TransactionType.RECEIVE -> transaction.recipientName.ifEmpty { "Received" }
                        TransactionType.BALANCE_CHECK -> "Balance Check"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (transaction.message.isNotEmpty() && transaction.type == TransactionType.SEND) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = Color(transaction.category.color).copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = transaction.category.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(transaction.category.color),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            
            // Amount and status
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = transaction.formattedAmount,
                    style = MaterialTheme.typography.titleMedium,
                    color = transaction.amountColor
                )
                
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = when (transaction.status) {
                        TransactionStatus.SUCCESS -> SuccessGreen.copy(alpha = 0.1f)
                        TransactionStatus.FAILED -> ErrorRed.copy(alpha = 0.1f)
                        TransactionStatus.PENDING -> PendingOrange.copy(alpha = 0.1f)
                    }
                ) {
                    Text(
                        text = transaction.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (transaction.status) {
                            TransactionStatus.SUCCESS -> SuccessGreen
                            TransactionStatus.FAILED -> ErrorRed
                            TransactionStatus.PENDING -> PendingOrange
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
