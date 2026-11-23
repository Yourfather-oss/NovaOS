package com.nova.os

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nova.os.data.MessageIntelligenceStore

/** Retains existing notification-listener capability and captures WhatsApp only after opt-in. */
class NovaNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        MessageIntelligenceStore.capture(this, sbn)
    }
}
