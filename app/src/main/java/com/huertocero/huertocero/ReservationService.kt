package com.huertocero.huertocero

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

object ReservationService {
    private const val HOLD_MINUTES = 60L

    fun reserveProduct(
        db: FirebaseFirestore,
        product: Product,
        buyerId: String,
        quantity: Double
    ): Task<String> {
        val productRef = db.collection("products").document(product.id)
        val reservationRef = db.collection("reservas").document()

        return db.runTransaction { transaction ->
            val snapshot = transaction.get(productRef)
            val latest = snapshot.toObject(Product::class.java)
                ?: throw IllegalStateException("missing_product")

            latest.id = snapshot.id
            val normalizedQuantity = quantity.takeIf { it > 0.0 } ?: throw IllegalStateException("invalid_quantity")
            val available = latest.getAvailableStock()

            if (available < normalizedQuantity) {
                throw IllegalStateException("not_enough_stock")
            }

            transaction.update(productRef, "stockReserved", latest.getStockReservedAsDouble() + normalizedQuantity)
            transaction.set(
                reservationRef,
                mapOf(
                    "productId" to latest.id,
                    "nombre" to latest.name,
                    "precio" to latest.getPriceAsDouble(),
                    "currency" to latest.normalizedCurrency(),
                    "quantity" to normalizedQuantity,
                    "unit" to latest.normalizedUnit(),
                    "buyerId" to buyerId,
                    "sellerId" to latest.sellerId,
                    "status" to "held",
                    "commissionRate" to 0.07,
                    "expiresAt" to Date(System.currentTimeMillis() + HOLD_MINUTES * 60_000L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "holdMinutes" to HOLD_MINUTES
                )
            )

            reservationRef.id
        }
    }

    fun releaseReservation(
        db: FirebaseFirestore,
        reservationId: String
    ): Task<Void?> {
        val reservationRef = db.collection("reservas").document(reservationId)

        return db.runTransaction { transaction ->
            val reservation = transaction.get(reservationRef)
            val productId = reservation.getString("productId").orEmpty()
            val quantity = reservation.getDouble("quantity") ?: 0.0

            if (productId.isNotBlank() && quantity > 0.0) {
                val productRef = db.collection("products").document(productId)
                val productSnapshot = transaction.get(productRef)

                if (productSnapshot.exists()) {
                    val currentReserved = productSnapshot.getDouble("stockReserved") ?: 0.0
                    transaction.update(productRef, "stockReserved", (currentReserved - quantity).coerceAtLeast(0.0))
                }
            }

            transaction.delete(reservationRef)
            null
        }
    }
}
