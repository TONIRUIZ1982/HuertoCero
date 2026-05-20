package com.huertocero.huertocero

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

        UiMotion.makePressable(btnBack)
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

                val list = result.documents
                val adapter = object : BaseAdapter() {
                    override fun getCount(): Int = list.size
                    override fun getItem(position: Int) = list[position]
                    override fun getItemId(position: Int) = position.toLong()

                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = layoutInflater.inflate(R.layout.item_reservation, parent, false)

                        val tv = view.findViewById<TextView>(R.id.tvReservation)
                        val btnDelete = view.findViewById<Button>(R.id.btnDelete)

                        val doc = list[position]
                        val name = doc.getString("nombre") ?: ""
                        val price = doc.getDouble("precio") ?: 0.0
                        val currency = doc.getString("currency") ?: "EUR"
                        val quantity = doc.getDouble("quantity") ?: 1.0
                        val unit = doc.getString("unit") ?: "kg"

                        UiMotion.makePressable(btnDelete)
                        tv.text = "$name - ${
                            MarketFormat.formatMoney(this@ReservationsActivity, price, currency)
                        } - ${
                            MarketFormat.formatQuantity(this@ReservationsActivity, quantity, unit)
                        }"

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
}
