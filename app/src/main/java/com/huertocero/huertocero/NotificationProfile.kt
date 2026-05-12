package com.huertocero.huertocero

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Locale

object NotificationProfile {
    private val db = FirebaseFirestore.getInstance()

    fun syncLocation(latLng: LatLng) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            db.collection("notificationProfiles").document(userId)
                .set(
                    mapOf(
                        "userId" to userId,
                        "fcmToken" to token,
                        "lat" to latLng.latitude,
                        "lng" to latLng.longitude,
                        "geoCells" to GeoEngagement.nearbyCells(latLng),
                        "alertRadiusKm" to 8,
                        "locale" to Locale.getDefault().toLanguageTag(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
        }
    }

    fun syncTokenOnly(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("notificationProfiles").document(userId)
            .set(
                mapOf(
                    "userId" to userId,
                    "fcmToken" to token,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
    }
}
