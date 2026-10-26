package com.conreo.couchytv

import android.service.notification.NotificationListenerService

/**
 * Exists only so MediaSessionManager.getActiveSessions() will answer (fork): Android lists the
 * active media sessions to an app holding notification-listener access. Nothing here reads, keeps or
 * acts on a notification.
 */
class MediaListener : NotificationListenerService()
