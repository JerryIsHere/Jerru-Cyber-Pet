package com.jerru.pet

import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class JerruNotificationListener : NotificationListenerService() {
    companion object {
        @Volatile var lastNotification = Long.MIN_VALUE
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        if (notification.packageName != packageName && !notification.isOngoing) {
            lastNotification = SystemClock.elapsedRealtime()
        }
    }
}
