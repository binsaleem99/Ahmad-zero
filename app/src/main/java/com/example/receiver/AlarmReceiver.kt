package com.zero.crm.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zero.crm.MainActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val leadName = intent.getStringExtra("LEAD_NAME") ?: "Lead"
        val leadPhone = intent.getStringExtra("LEAD_PHONE") ?: ""
        val offerName = intent.getStringExtra("OFFER_NAME") ?: "Property Offer"
        val price = intent.getStringExtra("OFFER_PRICE") ?: "0"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "crm_followup_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "CRM Follow-up Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alarms for following up with leads offline"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap notification -> opens MainActivity
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Call button action
        val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$leadPhone")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val callPendingIntent = PendingIntent.getActivity(
            context,
            leadPhone.hashCode(),
            callIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // WhatsApp button action
        val message = "السلام عليكم $leadName، بخصوص عرض $offerName بسعر $price د.ك..."
        val waUrl = "https://wa.me/${leadPhone.replace("+", "").replace(" ", "")}?text=${Uri.encode(message)}"
        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val waPendingIntent = PendingIntent.getActivity(
            context,
            leadPhone.hashCode() + 1,
            waIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("📞 CRM Follow-up: $leadName")
            .setContentText("Schedule alert: Tweak $offerName offer ($price KWD)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Call Now", callPendingIntent)
            .addAction(android.R.drawable.ic_menu_send, "WhatsApp", waPendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
