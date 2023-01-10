package world.verifi.auto_connect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object Notifications {
  const val CHANNEL_ID = "auto_connect_channel"

  fun createNotificationChannel(ctx: Context) {
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Auto Connect",
      NotificationManager.IMPORTANCE_LOW,
    )
    val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as
        NotificationManager
    manager.createNotificationChannel(channel)
  }

  fun buildForegroundNotification(ctx: Context): Notification {
    val mipmap = "${ctx.packageName}.R\$mipmap"
    val icLauncher = "ic_launcher"
    val icon = Class.forName(mipmap).getField(icLauncher).getInt(null)
    return NotificationCompat.Builder(ctx, CHANNEL_ID)
      .setSmallIcon(icon)
      .setContentTitle(
        ctx.applicationInfo.loadLabel(ctx.packageManager).toString()
      )
      .setContentTitle("Connecting to nearby WiFi")
      .build()
  }
}
