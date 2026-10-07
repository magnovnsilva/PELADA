package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MatchReminderReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val title = intent.getStringExtra(EXTRA_TITLE) ?: "Sideral F.C. • Rodada de Quinta-feira!"
    val message = intent.getStringExtra(EXTRA_MESSAGE)
      ?: "Bora pro jogo! Hoje é dia de bola na rede. Confira a escalação e chegue no horário."

    showNotification(context, title, message)
  }

  companion object {
    const val CHANNEL_ID = "sideral_match_reminders"
    const val CHANNEL_NAME = "Lembretes da Rodada Sideral F.C."
    const val NOTIFICATION_ID = 1001
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_MESSAGE = "extra_message"

    fun showNotification(context: Context, title: String, message: String) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permission = ContextCompat.checkSelfPermission(
          context,
          android.Manifest.permission.POST_NOTIFICATIONS
        )
        if (permission != PackageManager.PERMISSION_GRANTED) {
          return
        }
      }

      val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
          ?: return

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          CHANNEL_NAME,
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = "Notificações e lembretes semanais das rodadas de futebol do Sideral F.C."
          enableLights(true)
          enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
      }

      val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val largeIconBitmap = try {
        BitmapFactory.decodeResource(context.resources, R.drawable.splash_logo)
      } catch (e: Exception) {
        null
      }

      val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setLargeIcon(largeIconBitmap)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setColor(0xFFD31D28.toInt())
        .build()

      notificationManager.notify(NOTIFICATION_ID, notification)
    }
  }
}
