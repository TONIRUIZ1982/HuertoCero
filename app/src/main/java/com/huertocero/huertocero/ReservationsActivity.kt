package com.huertocero.huertocero

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ReservationsActivity : HuertoActivity() {

    private lateinit var listView: ListView
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: Button

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reservations)

        listView = findViewById(R.id.listReservations)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnBack = findViewById(R.id.btnBack)
        val reservationsHeader = findViewById<View>(R.id.reservationsHeader)

        UiMotion.makePressable(btnBack)
        UiMotion.reveal(reservationsHeader, listView)
        btnBack.setOnClickListener { finish() }
        loadReservations()
    }

    private fun loadReservations() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("reservas")
            .whereEqualTo("buyerId", userId)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    tvEmpty.visibility = View.VISIBLE
                    listView.adapter = null
                    return@addOnSuccessListener
                } else {
                    tvEmpty.visibility = View.GONE
                }

                val list = result.documents.sortedBy { doc ->
                    doc.getTimestamp("expiresAt")?.toDate()?.time ?: Long.MAX_VALUE
                }
                val adapter = object : BaseAdapter() {
                    override fun getCount(): Int = list.size
                    override fun getItem(position: Int) = list[position]
                    override fun getItemId(position: Int) = position.toLong()

                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val isNewView = convertView == null
                        val view = convertView ?: layoutInflater.inflate(R.layout.item_reservation, parent, false)

                        val nameText = view.findViewById<TextView>(R.id.tvReservationName)
                        val metaText = view.findViewById<TextView>(R.id.tvReservationMeta)
                        val statusText = view.findViewById<TextView>(R.id.tvReservationStatus)
                        val btnOpen = view.findViewById<Button>(R.id.btnOpenProduct)
                        val btnDelete = view.findViewById<Button>(R.id.btnDelete)

                        val doc = list[position]
                        val name = doc.getString("nombre") ?: ""
                        val productId = doc.getString("productId").orEmpty()
                        val price = doc.getDouble("precio") ?: 0.0
                        val currency = doc.getString("currency") ?: "EUR"
                        val quantity = doc.getDouble("quantity") ?: 1.0
                        val unit = doc.getString("unit") ?: "kg"
                        val expiresAtMillis = doc.getTimestamp("expiresAt")?.toDate()?.time

                        UiMotion.makePressable(view, btnOpen, btnDelete)
                        if (isNewView) {
                            UiMotion.showSurface(view, fromY = 14f)
                        }
                        nameText.text = name
                        metaText.text = getString(
                            R.string.reservation_card_meta,
                            MarketFormat.formatMoney(this@ReservationsActivity, price, currency),
                            MarketFormat.formatQuantity(this@ReservationsActivity, quantity, unit)
                        )
                        statusText.text = formatReservationExpiry(expiresAtMillis)

                        val openProduct = View.OnClickListener {
                            if (productId.isBlank()) return@OnClickListener
                            startActivity(
                                Intent(this@ReservationsActivity, DetailActivity::class.java)
                                    .putExtra("productId", productId)
                            )
                        }
                        view.setOnClickListener(openProduct)
                        btnOpen.setOnClickListener(openProduct)
                        btnOpen.visibility = if (productId.isBlank()) View.GONE else View.VISIBLE

                        btnDelete.setOnClickListener {
                            ReservationService.releaseReservation(db, doc.id)
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        this@ReservationsActivity,
                                        getString(R.string.reservation_deleted),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    loadReservations()
                                }
                        }

                        return view
                    }
                }

                listView.adapter = adapter
            }
    }

    private fun formatReservationExpiry(expiresAtMillis: Long?): String {
        val confirmText = getString(R.string.reservation_confirm_seller)
        if (expiresAtMillis == null) return confirmText

        val remainingMinutes = ((expiresAtMillis - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
        val expiryText = when {
            remainingMinutes == 0L -> getString(R.string.reservation_expired_status)
            remainingMinutes <= 10L -> getString(R.string.reservation_expires_soon)
            remainingMinutes < 60L -> getString(R.string.reservation_expires_in_minutes, remainingMinutes.toInt())
            else -> {
                val hours = (remainingMinutes / 60L).toInt().coerceAtLeast(1)
                getString(R.string.reservation_expires_in_hours, hours)
            }
        }

        return "$expiryText - $confirmText"
    }
}
