package com.huertocero.huertocero

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage

object AccountDeletionService {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val storage: FirebaseStorage
        get() = FirebaseStorage.getInstance()

    fun deleteCurrentAccount(): Task<Void> {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return Tasks.forException(IllegalStateException("missing_user"))
        val userId = user.uid

        return deleteUserData(userId).continueWithTask { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException("delete_user_data_failed")
            user.delete()
        }
    }

    private fun deleteUserData(userId: String): Task<Void> {
        val tasks = listOf(
            deleteSellerProducts(userId),
            deleteReservationsFor("buyerId", userId),
            deleteReservationsFor("sellerId", userId),
            deleteUserConversations(userId),
            deleteQuery(db.collection("recommendationEvents").whereEqualTo("userId", userId)),
            deleteQuery(db.collection("reports").whereEqualTo("reporterId", userId)),
            deleteQuery(db.collection("reviews").whereEqualTo("reviewerId", userId)),
            deleteUserDocument(userId),
            db.collection("notificationProfiles").document(userId).delete()
        )

        return Tasks.whenAll(tasks)
    }

    private fun deleteSellerProducts(userId: String): Task<Void> {
        return db.collection("products")
            .whereEqualTo("sellerId", userId)
            .get()
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("products_query_failed")

                val docs = task.result?.documents.orEmpty()
                val imageDeletes = docs.mapNotNull { it.getString("imageUrl") }
                    .filter { it.startsWith("gs://") || it.startsWith("http") }
                    .mapNotNull { url ->
                        val imageRef = try {
                            storage.getReferenceFromUrl(url)
                        } catch (_: IllegalArgumentException) {
                            null
                        }

                        imageRef
                    }
                    .map { imageRef ->
                        imageRef
                            .delete()
                            .continueWithTask { Tasks.forResult<Void?>(null) }
                    }

                Tasks.whenAll(imageDeletes + deleteDocuments(docs))
            }
    }

    private fun deleteReservationsFor(field: String, userId: String): Task<Void> {
        return db.collection("reservas")
            .whereEqualTo(field, userId)
            .get()
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("reservations_query_failed")

                val releaseTasks = task.result?.documents.orEmpty().map { reservation ->
                    ReservationService.releaseReservation(db, reservation.id)
                }

                Tasks.whenAll(releaseTasks)
            }
    }

    private fun deleteUserConversations(userId: String): Task<Void> {
        return db.collection("conversations")
            .whereArrayContains("participants", userId)
            .get()
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("conversations_query_failed")

                val conversationTasks = task.result?.documents.orEmpty().map { conversation ->
                    deleteConversation(conversation)
                }

                Tasks.whenAll(conversationTasks)
            }
    }

    private fun deleteConversation(conversation: DocumentSnapshot): Task<Void> {
        val conversationRef = db.collection("conversations").document(conversation.id)
        return conversationRef.collection("messages")
            .get()
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("messages_query_failed")
                deleteDocuments(task.result?.documents.orEmpty())
            }
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("messages_delete_failed")
                conversationRef.delete()
            }
    }

    private fun deleteUserDocument(userId: String): Task<Void> {
        val userRef = db.collection("users").document(userId)
        val subcollections = listOf(
            "favorites",
            "followedCategories",
            "followedSellers",
            "savedSearches"
        ).map { name -> deleteQuery(userRef.collection(name)) }

        return Tasks.whenAll(subcollections).continueWithTask { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException("user_subcollections_delete_failed")
            userRef.delete()
        }
    }

    private fun deleteQuery(query: Query): Task<Void> {
        return query.get().continueWithTask { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException("delete_query_failed")
            deleteDocuments(task.result?.documents.orEmpty())
        }
    }

    private fun deleteDocuments(docs: List<DocumentSnapshot>): Task<Void> {
        if (docs.isEmpty()) return Tasks.forResult(null)

        val commits = docs.chunked(450).map { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit()
        }

        return Tasks.whenAll(commits)
    }
}
