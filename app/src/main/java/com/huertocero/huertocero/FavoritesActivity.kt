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

class FavoritesActivity : HuertoActivity() {

    private lateinit var listView: ListView
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: Button

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        listView = findViewById(R.id.listFavorites)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }
        loadFavorites()
    }

    private fun loadFavorites() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("favorites")
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
                        val view = layoutInflater.inflate(R.layout.item_favorite, parent, false)
                        val name = view.findViewById<TextView>(R.id.tvItemName)
                        val btnDelete = view.findViewById<Button>(R.id.btnDelete)
                        val doc = list[position]

                        val productName = doc.getString("name") ?: ""
                        val price = doc.getDouble("price") ?: 0.0

                        name.text = "$productName - $price EUR"

                        btnDelete.setOnClickListener {
                            db.collection("users")
                                .document(userId)
                                .collection("favorites")
                                .document(doc.id)
                                .delete()

                            Toast.makeText(this@FavoritesActivity, getString(R.string.removed), Toast.LENGTH_SHORT).show()
                            loadFavorites()
                        }

                        return view
                    }
                }

                listView.adapter = adapter
            }
    }
}
