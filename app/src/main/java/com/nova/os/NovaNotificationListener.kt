package com.nova.os

import android.service.notification.NotificationListenerService

/**
 * Enabling notification access for NOVA lets it read which media is playing, so the home
 * music card can show the track and control playback. NOVA does not store notifications.
 */
class NovaNotificationListener : NotificationListenerService()
