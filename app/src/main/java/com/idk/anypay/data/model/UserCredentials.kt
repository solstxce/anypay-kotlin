package com.idk.anypay.data.model

import com.google.gson.annotations.SerializedName

/**
 * User credentials stored securely for UPI transactions.
 */
data class UserCredentials(
    @SerializedName("upi_pin")
    val upiPin: String = "",
    
    @SerializedName("mobile_number")
    val mobileNumber: String = "",
    
    @SerializedName("bank_name")
    val bankName: String = "",
    
    @SerializedName("bank_ifsc")
    val bankIfsc: String = "",
    
    @SerializedName("card_last_six")
    val cardLastSixDigits: String = "",
    
    @SerializedName("card_expiry_month")
    val cardExpiryMonth: String = "",
    
    @SerializedName("card_expiry_year")
    val cardExpiryYear: String = "",
    
    @SerializedName("is_setup_complete")
    val isSetupComplete: Boolean = false
) {
    /**
     * Formatted card details for USSD submission: last6digits + MMYY
     */
    val formattedCardDetails: String
        get() = "$cardLastSixDigits$cardExpiryMonth$cardExpiryYear"
    
    /**
     * Bank input for USSD: first 4 characters of IFSC or bank name
     */
    val bankInput: String
        get() = if (bankIfsc.length >= 4) bankIfsc.take(4).uppercase() else bankName
    
    /**
     * Validate if all required fields are filled
     */
    fun isValid(): Boolean {
        return upiPin.length in 4..6 &&
               mobileNumber.length == 10 &&
               mobileNumber.first() in '6'..'9' &&
               bankName.isNotBlank() &&
               bankIfsc.length == 11 &&
               cardLastSixDigits.length == 6 &&
               cardExpiryMonth.length == 2 &&
               cardExpiryYear.length == 2
    }
}

/**
 * Supported Indian banks for UPI via *99#
 */
data class Bank(
    val name: String,
    val ifscPrefix: String,
    val shortCode: String
)

val SUPPORTED_BANKS = listOf(
    Bank("State Bank of India", "SBIN", "SBI"),
    Bank("HDFC Bank", "HDFC", "HDFC"),
    Bank("ICICI Bank", "ICIC", "ICICI"),
    Bank("Axis Bank", "UTIB", "AXIS"),
    Bank("Punjab National Bank", "PUNB", "PNB"),
    Bank("Bank of Baroda", "BARB", "BOB"),
    Bank("Kotak Mahindra Bank", "KKBK", "KOTAK"),
    Bank("Yes Bank", "YESB", "YES"),
    Bank("IndusInd Bank", "INDB", "INDUS"),
    Bank("Union Bank of India", "UBIN", "UNION"),
    Bank("Canara Bank", "CNRB", "CANARA"),
    Bank("Bank of India", "BKID", "BOI"),
    Bank("IDBI Bank", "IBKL", "IDBI"),
    Bank("Central Bank of India", "CBIN", "CBI"),
    Bank("Indian Bank", "IDIB", "INDIAN"),
    Bank("Indian Overseas Bank", "IOBA", "IOB"),
    Bank("UCO Bank", "UCBA", "UCO"),
    Bank("Federal Bank", "FDRL", "FEDERAL"),
    Bank("South Indian Bank", "SIBL", "SIB"),
    Bank("Karnataka Bank", "KARB", "KBL")
)
