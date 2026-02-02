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

enum class HistoryFilter {
    ALL, SENT, BALANCE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    transactions: List<Transaction>,
    totalSpent: Double,
    averageTransaction: Double,
    categoryStats: Map<PaymentCategory, Pair<Int, Double>>
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    
    val filteredTransactions = remember(transactions, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> transactions
            HistoryFilter.SENT -> transactions.filter { it.type == TransactionType.SEND }
            HistoryFilter.BALANCE -> transactions.filter { it.type == TransactionType.BALANCE_CHECK }
        }
    }
    
    val groupedTransactions = remember(filteredTransactions) {
        filteredTransactions.groupBy { it.relativeDate }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Row
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Transactions") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Analytics") }
            )
        }
        
        when (selectedTab) {
            0 -> TransactionsTab(
                groupedTransactions = groupedTransactions,
                selectedFilter = selectedFilter,
                onFilterChange = { selectedFilter = it }
            )
            1 -> AnalyticsTab(
                totalSpent = totalSpent,
                averageTransaction = averageTransaction,
                categoryStats = categoryStats
            )
        }
    }
}

@Composable
private fun TransactionsTab(
    groupedTransactions: Map<String, List<Transaction>>,
    selectedFilter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Filter chips
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == HistoryFilter.ALL,
                    onClick = { onFilterChange(HistoryFilter.ALL) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = selectedFilter == HistoryFilter.SENT,
                    onClick = { onFilterChange(HistoryFilter.SENT) },
                    label = { Text("Sent") }
                )
                FilterChip(
                    selected = selectedFilter == HistoryFilter.BALANCE,
                    onClick = { onFilterChange(HistoryFilter.BALANCE) },
                    label = { Text("Balance Checks") }
                )
            }
        }
        
        if (groupedTransactions.isEmpty()) {
            item {
                EmptyTransactionsCard()
            }
        } else {
            groupedTransactions.forEach { (date, txList) ->
                item {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(txList) { transaction ->
                    TransactionItem(transaction = transaction)
                }
            }
        }
    }
}

@Composable
private fun EmptyTransactionsCard() {
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
                text = "No transactions found",
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

@Composable
private fun AnalyticsTab(
    totalSpent: Double,
    averageTransaction: Double,
    categoryStats: Map<PaymentCategory, Pair<Int, Double>>
) {
    val sortedCategories = remember(categoryStats) {
        categoryStats.entries.sortedByDescending { it.value.second }.take(6)
    }
    
    val totalAmount = remember(categoryStats) {
        categoryStats.values.sumOf { it.second }
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overview Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    title = "Total Spent",
                    value = "₹${String.format("%,.0f", totalSpent)}",
                    icon = Icons.Default.TrendingDown,
                    color = SendRed,
                    modifier = Modifier.weight(1f)
                )
                
                StatCard(
                    title = "Average",
                    value = "₹${String.format("%,.0f", averageTransaction)}",
                    icon = Icons.Default.Analytics,
                    color = BalanceBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Category Breakdown Header
        item {
            Text(
                text = "Spending by Category",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        if (sortedCategories.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PieChart,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No spending data yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(sortedCategories) { (category, stats) ->
                val (count, amount) = stats
                val percentage = if (totalAmount > 0) (amount / totalAmount * 100) else 0.0
                
                CategoryStatItem(
                    category = category,
                    count = count,
                    amount = amount,
                    percentage = percentage
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = color.copy(alpha = 0.1f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@Composable
private fun CategoryStatItem(
    category: PaymentCategory,
    count: Int,
    amount: Double,
    percentage: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color(category.color).copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = category.icon,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "$count transaction${if (count != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "₹${String.format("%,.0f", amount)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${String.format("%.1f", percentage)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(category.color)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { (percentage / 100).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = Color(category.color),
                trackColor = Color(category.color).copy(alpha = 0.1f),
            )
        }
    }
}
