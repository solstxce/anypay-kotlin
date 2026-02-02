package com.idk.anypay.data.model

import androidx.compose.ui.graphics.Color
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

/**
 * Transaction types supported by the app
 */
enum class TransactionType {
    @SerializedName("send")
    SEND,
    
    @SerializedName("receive")
    RECEIVE,
    
    @SerializedName("balance_check")
    BALANCE_CHECK
}

/**
 * Transaction status
 */
enum class TransactionStatus {
    @SerializedName("pending")
    PENDING,
    
    @SerializedName("success")
    SUCCESS,
    
    @SerializedName("failed")
    FAILED
}

/**
 * Payment categories with auto-detection based on keywords
 */
enum class PaymentCategory(
    val label: String,
    val icon: String,
    val color: Long
) {
    FOOD_DINING("Food & Dining", "🍴", 0xFFFF6B6B),
    SHOPPING("Shopping", "🛍️", 0xFF4ECDC4),
    GROCERIES("Groceries", "🛒", 0xFF95E1D3),
    TRANSPORT("Transport", "🚌", 0xFFFFE66D),
    ENTERTAINMENT("Entertainment", "🎬", 0xFFAA96DA),
    BILLS_UTILITIES("Bills & Utilities", "🧾", 0xFFFF8B94),
    HEALTH("Health", "🏥", 0xFF6BCF7F),
    EDUCATION("Education", "🎓", 0xFF5DADE2),
    PERSONAL_TRANSFER("Personal Transfer", "👥", 0xFFFFCACA),
    OTHER("Other", "📦", 0xFF95A5A6);
    
    companion object {
        private val FOOD_KEYWORDS = listOf(
            "zomato", "swiggy", "dominos", "pizza", "restaurant", "cafe", 
            "food", "burger", "kfc", "mcdonalds", "starbucks", "chai"
        )
        
        private val SHOPPING_KEYWORDS = listOf(
            "amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho",
            "shopping", "store", "mall", "mart", "shop"
        )
        
        private val GROCERY_KEYWORDS = listOf(
            "bigbasket", "blinkit", "zepto", "dmart", "grofers", "jiomart",
            "grocery", "vegetables", "fruits", "kirana", "supermarket"
        )
        
        private val TRANSPORT_KEYWORDS = listOf(
            "uber", "ola", "rapido", "metro", "petrol", "diesel", "fuel",
            "parking", "toll", "irctc", "redbus", "train", "flight"
        )
        
        private val ENTERTAINMENT_KEYWORDS = listOf(
            "netflix", "hotstar", "prime", "spotify", "gaana", "jio", 
            "movie", "cinema", "pvr", "inox", "gaming", "game"
        )
        
        private val BILLS_KEYWORDS = listOf(
            "electricity", "water", "gas", "internet", "broadband", "recharge",
            "postpaid", "prepaid", "dth", "bill", "insurance", "emi"
        )
        
        private val HEALTH_KEYWORDS = listOf(
            "hospital", "clinic", "pharmacy", "medical", "medicine", "doctor",
            "apollo", "pharmeasy", "netmeds", "1mg", "health", "lab"
        )
        
        private val EDUCATION_KEYWORDS = listOf(
            "school", "college", "university", "course", "fees", "tuition",
            "book", "udemy", "coursera", "byju", "unacademy", "education"
        )
        
        /**
         * Categorize a transaction based on its message/remarks
         */
        fun categorize(message: String?): PaymentCategory {
            if (message.isNullOrBlank()) return OTHER
            
            val lowerMessage = message.lowercase()
            
            return when {
                FOOD_KEYWORDS.any { lowerMessage.contains(it) } -> FOOD_DINING
                SHOPPING_KEYWORDS.any { lowerMessage.contains(it) } -> SHOPPING
                GROCERY_KEYWORDS.any { lowerMessage.contains(it) } -> GROCERIES
                TRANSPORT_KEYWORDS.any { lowerMessage.contains(it) } -> TRANSPORT
                ENTERTAINMENT_KEYWORDS.any { lowerMessage.contains(it) } -> ENTERTAINMENT
                BILLS_KEYWORDS.any { lowerMessage.contains(it) } -> BILLS_UTILITIES
                HEALTH_KEYWORDS.any { lowerMessage.contains(it) } -> HEALTH
                EDUCATION_KEYWORDS.any { lowerMessage.contains(it) } -> EDUCATION
                else -> PERSONAL_TRANSFER
            }
        }
    }
}

/**
 * Represents a UPI transaction
 */
data class Transaction(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),
    
    @SerializedName("type")
    val type: TransactionType,
    
    @SerializedName("amount")
    val amount: Double,
    
    @SerializedName("recipient_vpa")
    val recipientVpa: String = "",
    
    @SerializedName("recipient_name")
    val recipientName: String = "",
    
    @SerializedName("status")
    val status: TransactionStatus = TransactionStatus.PENDING,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("message")
    val message: String = "",
    
    @SerializedName("reference_id")
    val referenceId: String = "",
    
    @SerializedName("balance")
    val balance: Double? = null
) {
    /**
     * Category based on message content
     */
    val category: PaymentCategory
        get() = PaymentCategory.categorize(message)
    
    /**
     * Formatted timestamp for display
     */
    val formattedTime: String
        get() = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
    
    /**
     * Formatted date for display
     */
    val formattedDate: String
        get() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    
    /**
     * Human-readable relative date (Today, Yesterday, or date)
     */
    val relativeDate: String
        get() {
            val today = Calendar.getInstance()
            val txDate = Calendar.getInstance().apply { timeInMillis = timestamp }
            
            return when {
                isSameDay(today, txDate) -> "Today"
                isYesterday(today, txDate) -> "Yesterday"
                else -> formattedDate
            }
        }
    
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    
    private fun isYesterday(today: Calendar, other: Calendar): Boolean {
        val yesterday = today.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(yesterday, other)
    }
    
    /**
     * Color for amount display based on transaction type
     */
    val amountColor: Color
        get() = when (type) {
            TransactionType.SEND -> Color(0xFFEF5350)      // Red
            TransactionType.RECEIVE -> Color(0xFF66BB6A)   // Green
            TransactionType.BALANCE_CHECK -> Color(0xFF42A5F5) // Blue
        }
    
    /**
     * Formatted amount with sign
     */
    val formattedAmount: String
        get() = when (type) {
            TransactionType.SEND -> "-₹${String.format("%.2f", amount)}"
            TransactionType.RECEIVE -> "+₹${String.format("%.2f", amount)}"
            TransactionType.BALANCE_CHECK -> "₹${String.format("%.2f", balance ?: 0.0)}"
        }
}

/**
 * Parsed UPI payment info from QR code
 */
data class UpiPaymentInfo(
    val upiId: String,
    val name: String = "",
    val amount: Double? = null,
    val note: String = ""
) {
    companion object {
        /**
         * Parse UPI payment URL from QR code
         * Format: upi://pay?pa=<UPI_ID>&pn=<NAME>&am=<AMOUNT>&tn=<NOTE>
         */
        fun parse(url: String): UpiPaymentInfo? {
            if (!url.startsWith("upi://pay")) return null
            
            try {
                val params = url.substringAfter("?").split("&")
                    .associate {
                        val (key, value) = it.split("=", limit = 2)
                        key to java.net.URLDecoder.decode(value, "UTF-8")
                    }
                
                val upiId = params["pa"] ?: return null
                if (!isValidUpiId(upiId)) return null
                
                return UpiPaymentInfo(
                    upiId = upiId,
                    name = params["pn"] ?: "",
                    amount = params["am"]?.toDoubleOrNull(),
                    note = params["tn"] ?: ""
                )
            } catch (e: Exception) {
                return null
            }
        }
        
        /**
         * Validate UPI ID format (user@provider)
         */
        fun isValidUpiId(upiId: String): Boolean {
            val pattern = Regex("^[a-zA-Z0-9._-]+@[a-zA-Z0-9]+$")
            return pattern.matches(upiId)
        }
        
        /**
         * Validate 10-digit Indian mobile number
         */
        fun isValidMobileNumber(mobile: String): Boolean {
            return mobile.length == 10 && mobile.first() in '6'..'9' && mobile.all { it.isDigit() }
        }
        
        /**
         * Check if input is a valid recipient (UPI ID or mobile)
         */
        fun isValidRecipient(input: String): Boolean {
            return isValidUpiId(input) || isValidMobileNumber(input)
        }
    }
}
