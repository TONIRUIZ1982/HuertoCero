package com.huertocero.huertocero

import com.google.firebase.messaging.FirebaseMessagingService

class HuertoMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        NotificationProfile.syncTokenOnly(token)
    }
}
