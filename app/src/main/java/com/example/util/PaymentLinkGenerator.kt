package com.zero.crm.util

/**
 * PaymentLinkGenerator
 * Natively generates offline payment request templates and deep-link hooks for local GCC payment networks
 * (K-Net in Kuwait, stc pay in KSA/Bahrain, urpay in KSA) without requiring a server-side backend broker.
 */
object PaymentLinkGenerator {

    enum class PaymentMethod {
        KNET,
        STC_PAY,
        URPAY
    }

    /**
     * Generates a payment request text template to be sent directly to the client via WhatsApp or SMS.
     *
     * @param clientName Name of the client.
     * @param amount The invoice price/amount.
     * @param currency The currency (e.g., KWD, SAR, AED).
     * @param referenceId An optional local offline reference/invoice ID.
     * @param method The local ecosystem payment gateway to target.
     * @return A formatted payment request message with localized payment details.
     */
    fun generatePaymentRequestSnippet(
        clientName: String,
        amount: Int,
        currency: String,
        referenceId: String,
        method: PaymentMethod,
        isArabic: Boolean = false
    ): String {
        val formattedAmount = String.format("%,d", amount)
        
        return if (isArabic) {
            when (method) {
                PaymentMethod.KNET -> """
                    عزيزي $clientName،
                    يرجى تسوية دفعتك البالغة $formattedAmount $currency عبر بوابة K-Net الآمنة.
                    رقم المرجع: $referenceId
                    رابط السداد السريع (K-Net):
                    https://payment.knet.com.kw/pay?ref=$referenceId&amt=$amount
                    
                    شكرًا لك على تعاملك معنا!
                """.trimIndent()
                
                PaymentMethod.STC_PAY -> """
                    عزيزي $clientName،
                    يرجى إرسال مبلغ $formattedAmount $currency عبر stc pay إلى الرقم المعتمد للمؤسسة.
                    رقم المرجع: $referenceId
                    رابط التحويل السريع (stc pay):
                    https://stcpay.com.sa/transfer?ref=$referenceId&amt=$amount
                    
                    شكرًا لثقتكم بنا!
                """.trimIndent()
                
                PaymentMethod.URPAY -> """
                    عزيزي $clientName،
                    يرجى إرسال مبلغ $formattedAmount $currency عبر تطبيق urpay للرقم المعتمد.
                    رقم المرجع: $referenceId
                    رابط الدفع السريع (urpay):
                    https://urpay.com.sa/pay?invoice=$referenceId&val=$amount
                    
                    طاب يومك!
                """.trimIndent()
            }
        } else {
            when (method) {
                PaymentMethod.KNET -> """
                    Dear $clientName,
                    Please settle your payment of $formattedAmount $currency via K-Net secure portal.
                    Invoice Ref: $referenceId
                    Direct Payment Link (K-Net):
                    https://payment.knet.com.kw/pay?ref=$referenceId&amt=$amount
                    
                    Thank you for your business!
                """.trimIndent()
                
                PaymentMethod.STC_PAY -> """
                    Dear $clientName,
                    Please transfer $formattedAmount $currency via stc pay to our registered business wallet.
                    Invoice Ref: $referenceId
                    Direct Wallet Link (stc pay):
                    https://stcpay.com.sa/transfer?ref=$referenceId&amt=$amount
                    
                    We appreciate your business!
                """.trimIndent()
                
                PaymentMethod.URPAY -> """
                    Dear $clientName,
                    Please complete your transfer of $formattedAmount $currency via urpay.
                    Invoice Ref: $referenceId
                    Direct Payment Link (urpay):
                    https://urpay.com.sa/pay?invoice=$referenceId&val=$amount
                    
                    Have a wonderful day!
                """.trimIndent()
            }
        }
    }
}
