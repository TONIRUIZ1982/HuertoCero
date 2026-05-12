package com.huertocero.huertocero

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object EngagementTracker {
    fun productEvent(event: String, product: Product) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("recommendationEvents").add(
            mapOf(
                "event" to event,
                "userId" to userId,
                "productId" to product.id,
                "sellerId" to product.sellerId,
                "category" to ProductCategories.normalize(product.category),
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
    }

    fun followSignals(product: Product) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val category = ProductCategories.normalize(product.category)

        db.collection("users").document(userId)
            .collection("followedCategories").document(category)
            .set(mapOf("category" to category, "updatedAt" to FieldValue.serverTimestamp()))

        if (product.sellerId.isNotBlank()) {
            db.collection("users").document(userId)
                .collection("followedSellers").document(product.sellerId)
                .set(
                    mapOf(
                        "sellerId" to product.sellerId,
                        "sellerName" to product.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
        }

        db.collection("notificationProfiles").document(userId)
            .set(
                buildMap {
                    put("userId", userId)
                    put("categoryAlerts", FieldValue.arrayUnion(category))
                    if (product.sellerId.isNotBlank()) {
                        put("sellerAlerts", FieldValue.arrayUnion(product.sellerId))
                    }
                    put("updatedAt", FieldValue.serverTimestamp())
                },
                SetOptions.merge()
            )
    }

    fun saveSearch(query: String, category: String, sortMode: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val cleanQuery = query.trim().lowercase(Locale.getDefault())
        val normalizedCategory = if (category == ProductCategories.FILTER_ALL) {
            ProductCategories.FILTER_ALL
        } else {
            ProductCategories.normalize(category)
        }
        val searchId = "${cleanQuery}_${normalizedCategory}_${sortMode}".hashCode().toString()

        db.collection("users").document(userId)
            .collection("savedSearches").document(searchId)
            .set(
                mapOf(
                    "query" to cleanQuery,
                    "category" to normalizedCategory,
                    "sortMode" to sortMode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

        val notificationData = mutableMapOf<String, Any>(
            "userId" to userId,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (cleanQuery.isNotBlank()) {
            notificationData["savedSearchTerms"] = FieldValue.arrayUnion(cleanQuery)
        }
        if (normalizedCategory != ProductCategories.FILTER_ALL) {
            notificationData["categoryAlerts"] = FieldValue.arrayUnion(normalizedCategory)
        }

        db.collection("notificationProfiles").document(userId)
            .set(notificationData, SetOptions.merge())
    }

    fun trackDailyOpen() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userId)
        val today = dateKey(Date())
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DATE, -1)
        }.time.let(::dateKey)

        userRef.get().addOnSuccessListener { doc ->
            val lastActiveDate = doc.getString("lastActiveDate")
            val currentStreak = doc.getLong("localStreakDays") ?: 0L
            val nextStreak = when (lastActiveDate) {
                today -> currentStreak
                yesterday -> currentStreak + 1
                else -> 1
            }

            userRef.set(
                mapOf(
                    "lastActiveDate" to today,
                    "localStreakDays" to nextStreak,
                    "appOpenCount" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }
    }

    fun addImpactAfterReservation(product: Product, quantity: Double) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(userId)
            .set(
                mapOf(
                    "impactReservations" to FieldValue.increment(1),
                    "impactLocalKg" to FieldValue.increment(quantity),
                    "impactKmSavedEstimate" to FieldValue.increment(12)
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
        productEvent("reserve", product)
    }

    private fun dateKey(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
    }
}
