package com.idk.anypay.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.idk.anypay.data.model.Transaction
import com.idk.anypay.data.model.UserCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Secure storage repository using EncryptedSharedPreferences.
 * All user credentials and transactions are encrypted at rest.
 */
class SecureStorageRepository(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "anypay_secure_prefs"
        private const val KEY_CREDENTIALS = "user_credentials"
        private const val KEY_TRANSACTIONS = "transactions"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_LAST_BALANCE = "last_balance"
        
        @Volatile
        private var instance: SecureStorageRepository? = null
        
        fun getInstance(context: Context): SecureStorageRepository {
            return instance ?: synchronized(this) {
                instance ?: SecureStorageRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val gson = Gson()
    
    // StateFlows for reactive updates
    private val _credentials = MutableStateFlow<UserCredentials?>(null)
    val credentials: StateFlow<UserCredentials?> = _credentials.asStateFlow()
    
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()
    
    private val _isSetupComplete = MutableStateFlow(false)
    val isSetupComplete: StateFlow<Boolean> = _isSetupComplete.asStateFlow()
    
    init {
        // Load initial data
        loadCredentials()
        loadTransactions()
        _isSetupComplete.value = sharedPrefs.getBoolean(KEY_SETUP_COMPLETE, false)
    }
    
    // ==================== Credentials ====================
    
    private fun loadCredentials() {
        val json = sharedPrefs.getString(KEY_CREDENTIALS, null)
        _credentials.value = json?.let { gson.fromJson(it, UserCredentials::class.java) }
    }
    
    suspend fun saveCredentials(credentials: UserCredentials) = withContext(Dispatchers.IO) {
        val json = gson.toJson(credentials)
        sharedPrefs.edit()
            .putString(KEY_CREDENTIALS, json)
            .putBoolean(KEY_SETUP_COMPLETE, credentials.isSetupComplete)
            .apply()
        _credentials.value = credentials
        _isSetupComplete.value = credentials.isSetupComplete
    }
    
    fun getCredentialsSync(): UserCredentials? {
        return _credentials.value
    }
    
    suspend fun updateUpiPin(newPin: String) = withContext(Dispatchers.IO) {
        _credentials.value?.let { current ->
            saveCredentials(current.copy(upiPin = newPin))
        }
    }
    
    // ==================== Transactions ====================
    
    private fun loadTransactions() {
        val json = sharedPrefs.getString(KEY_TRANSACTIONS, null)
        val type = object : TypeToken<List<Transaction>>() {}.type
        _transactions.value = json?.let { gson.fromJson(it, type) } ?: emptyList()
    }
    
    suspend fun saveTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        val current = _transactions.value.toMutableList()
        current.add(0, transaction) // Add to beginning (newest first)
        val json = gson.toJson(current)
        sharedPrefs.edit().putString(KEY_TRANSACTIONS, json).apply()
        _transactions.value = current
    }
    
    suspend fun updateTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        val current = _transactions.value.toMutableList()
        val index = current.indexOfFirst { it.id == transaction.id }
        if (index >= 0) {
            current[index] = transaction
            val json = gson.toJson(current)
            sharedPrefs.edit().putString(KEY_TRANSACTIONS, json).apply()
            _transactions.value = current
        }
    }
    
    fun getTransactionsSync(): List<Transaction> {
        return _transactions.value
    }
    
    suspend fun clearTransactions() = withContext(Dispatchers.IO) {
        sharedPrefs.edit().remove(KEY_TRANSACTIONS).apply()
        _transactions.value = emptyList()
    }
    
    // ==================== Balance ====================
    
    suspend fun saveLastBalance(balance: Double) = withContext(Dispatchers.IO) {
        sharedPrefs.edit().putFloat(KEY_LAST_BALANCE, balance.toFloat()).apply()
    }
    
    fun getLastBalance(): Double {
        return sharedPrefs.getFloat(KEY_LAST_BALANCE, 0f).toDouble()
    }
    
    // ==================== Clear All Data ====================
    
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        sharedPrefs.edit().clear().apply()
        _credentials.value = null
        _transactions.value = emptyList()
        _isSetupComplete.value = false
    }
}
