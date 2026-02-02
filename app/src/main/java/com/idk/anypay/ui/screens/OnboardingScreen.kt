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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.idk.anypay.data.model.SUPPORTED_BANKS
import com.idk.anypay.data.model.UserCredentials
import com.idk.anypay.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: (UserCredentials) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    
    // Form state
    var mobileNumber by remember { mutableStateOf("") }
    var upiPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var selectedBankIndex by remember { mutableIntStateOf(-1) }
    var bankIfsc by remember { mutableStateOf("") }
    var cardLastSix by remember { mutableStateOf("") }
    var cardExpiryMonth by remember { mutableStateOf("") }
    var cardExpiryYear by remember { mutableStateOf("") }
    
    // Visibility states
    var showPin by remember { mutableStateOf(false) }
    var showConfirmPin by remember { mutableStateOf(false) }
    var bankDropdownExpanded by remember { mutableStateOf(false) }
    
    // Validation
    val isMobileValid = mobileNumber.length == 10 && mobileNumber.firstOrNull()?.let { it in '6'..'9' } == true
    val isPinValid = upiPin.length in 4..6 && upiPin == confirmPin
    val isBankValid = selectedBankIndex >= 0 && bankIfsc.length == 11
    val isCardValid = cardLastSix.length == 6 && cardExpiryMonth.length == 2 && cardExpiryYear.length == 2
    
    val canProceed = when (currentStep) {
        0 -> isMobileValid
        1 -> isPinValid
        2 -> isBankValid
        3 -> isCardValid
        else -> false
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Up AnyPay") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = { (currentStep + 1) / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            
            Text(
                text = "Step ${currentStep + 1} of 4",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            when (currentStep) {
                0 -> MobileNumberStep(
                    mobileNumber = mobileNumber,
                    onMobileChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) mobileNumber = it },
                    isValid = isMobileValid
                )
                1 -> PinSetupStep(
                    upiPin = upiPin,
                    confirmPin = confirmPin,
                    showPin = showPin,
                    showConfirmPin = showConfirmPin,
                    onPinChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) upiPin = it },
                    onConfirmPinChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it },
                    onToggleShowPin = { showPin = !showPin },
                    onToggleShowConfirmPin = { showConfirmPin = !showConfirmPin },
                    isPinValid = upiPin.length in 4..6,
                    pinsMatch = upiPin == confirmPin && confirmPin.isNotEmpty()
                )
                2 -> BankSelectionStep(
                    selectedBankIndex = selectedBankIndex,
                    bankIfsc = bankIfsc,
                    expanded = bankDropdownExpanded,
                    onExpandedChange = { bankDropdownExpanded = it },
                    onBankSelect = { index ->
                        selectedBankIndex = index
                        if (index >= 0) {
                            bankIfsc = SUPPORTED_BANKS[index].ifscPrefix + "0000000"
                        }
                        bankDropdownExpanded = false
                    },
                    onIfscChange = { if (it.length <= 11) bankIfsc = it.uppercase() },
                    isBankValid = selectedBankIndex >= 0,
                    isIfscValid = bankIfsc.length == 11
                )
                3 -> CardDetailsStep(
                    cardLastSix = cardLastSix,
                    cardExpiryMonth = cardExpiryMonth,
                    cardExpiryYear = cardExpiryYear,
                    onCardChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) cardLastSix = it },
                    onMonthChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) cardExpiryMonth = it },
                    onYearChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) cardExpiryYear = it },
                    isCardValid = cardLastSix.length == 6,
                    isExpiryValid = cardExpiryMonth.length == 2 && cardExpiryYear.length == 2
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { currentStep-- }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                
                Button(
                    onClick = {
                        if (currentStep < 3) {
                            currentStep++
                        } else {
                            // Complete setup
                            onComplete(
                                UserCredentials(
                                    mobileNumber = mobileNumber,
                                    upiPin = upiPin,
                                    bankName = if (selectedBankIndex >= 0) SUPPORTED_BANKS[selectedBankIndex].name else "",
                                    bankIfsc = bankIfsc,
                                    cardLastSixDigits = cardLastSix,
                                    cardExpiryMonth = cardExpiryMonth,
                                    cardExpiryYear = cardExpiryYear,
                                    isSetupComplete = true
                                )
                            )
                        }
                    },
                    enabled = canProceed
                ) {
                    Text(if (currentStep < 3) "Next" else "Complete Setup")
                    if (currentStep < 3) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileNumberStep(
    mobileNumber: String,
    onMobileChange: (String) -> Unit,
    isValid: Boolean
) {
    Column {
        Text(
            text = "Enter Your Mobile Number",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "This should be the mobile number linked to your bank account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = mobileNumber,
            onValueChange = onMobileChange,
            label = { Text("Mobile Number") },
            placeholder = { Text("9876543210") },
            prefix = { Text("+91 ") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            isError = mobileNumber.isNotEmpty() && !isValid,
            supportingText = {
                if (mobileNumber.isNotEmpty() && !isValid) {
                    Text("Enter a valid 10-digit mobile number starting with 6-9")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PinSetupStep(
    upiPin: String,
    confirmPin: String,
    showPin: Boolean,
    showConfirmPin: Boolean,
    onPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit,
    onToggleShowPin: () -> Unit,
    onToggleShowConfirmPin: () -> Unit,
    isPinValid: Boolean,
    pinsMatch: Boolean
) {
    Column {
        Text(
            text = "Set Your UPI PIN",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Create a 4-6 digit UPI PIN for transactions. Keep it secret!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = upiPin,
            onValueChange = onPinChange,
            label = { Text("UPI PIN") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onToggleShowPin) {
                    Icon(
                        if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPin) "Hide PIN" else "Show PIN"
                    )
                }
            },
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            isError = upiPin.isNotEmpty() && !isPinValid,
            supportingText = {
                if (upiPin.isNotEmpty() && !isPinValid) {
                    Text("PIN must be 4-6 digits")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = confirmPin,
            onValueChange = onConfirmPinChange,
            label = { Text("Confirm UPI PIN") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onToggleShowConfirmPin) {
                    Icon(
                        if (showConfirmPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showConfirmPin) "Hide PIN" else "Show PIN"
                    )
                }
            },
            visualTransformation = if (showConfirmPin) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            isError = confirmPin.isNotEmpty() && !pinsMatch,
            supportingText = {
                if (confirmPin.isNotEmpty() && !pinsMatch) {
                    Text("PINs do not match")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankSelectionStep(
    selectedBankIndex: Int,
    bankIfsc: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onBankSelect: (Int) -> Unit,
    onIfscChange: (String) -> Unit,
    isBankValid: Boolean,
    isIfscValid: Boolean
) {
    Column {
        Text(
            text = "Select Your Bank",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Choose the bank account you want to link for UPI payments.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            OutlinedTextField(
                value = if (selectedBankIndex >= 0) SUPPORTED_BANKS[selectedBankIndex].name else "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Bank") },
                leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                SUPPORTED_BANKS.forEachIndexed { index, bank ->
                    DropdownMenuItem(
                        text = { Text(bank.name) },
                        onClick = { onBankSelect(index) }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = bankIfsc,
            onValueChange = onIfscChange,
            label = { Text("IFSC Code") },
            placeholder = { Text("SBIN0001234") },
            leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
            singleLine = true,
            isError = bankIfsc.isNotEmpty() && !isIfscValid,
            supportingText = {
                if (bankIfsc.isNotEmpty() && !isIfscValid) {
                    Text("IFSC code must be 11 characters")
                } else {
                    Text("You can find this on your cheque book or bank statement")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CardDetailsStep(
    cardLastSix: String,
    cardExpiryMonth: String,
    cardExpiryYear: String,
    onCardChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    isCardValid: Boolean,
    isExpiryValid: Boolean
) {
    Column {
        Text(
            text = "Enter Debit Card Details",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Enter your debit card's last 6 digits and expiry date for verification.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = cardLastSix,
            onValueChange = onCardChange,
            label = { Text("Last 6 Digits of Debit Card") },
            placeholder = { Text("123456") },
            leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = cardLastSix.isNotEmpty() && !isCardValid,
            supportingText = {
                if (cardLastSix.isNotEmpty() && !isCardValid) {
                    Text("Enter exactly 6 digits")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Card Expiry Date",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = cardExpiryMonth,
                onValueChange = onMonthChange,
                label = { Text("MM") },
                placeholder = { Text("01") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            
            OutlinedTextField(
                value = cardExpiryYear,
                onValueChange = onYearChange,
                label = { Text("YY") },
                placeholder = { Text("28") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        
        if (!isExpiryValid && (cardExpiryMonth.isNotEmpty() || cardExpiryYear.isNotEmpty())) {
            Text(
                text = "Enter valid month (MM) and year (YY)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Your data is encrypted and stored securely on your device. We never send it to any server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
