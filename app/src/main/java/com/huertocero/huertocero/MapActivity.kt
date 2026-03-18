package com.huertocero.huertocero

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var db: FirebaseFirestore

    // 🔥 IMAGEN
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        db = FirebaseFirestore.getInstance()

        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map)

        if (mapFragment is SupportMapFragment) {
            mapFragment.getMapAsync(this)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val valencia = LatLng(39.4699, -0.3763)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(valencia, 12f))

        loadProducts()

        mMap.setOnMapClickListener { latLng ->
            showAddProductDialog(latLng)
        }
    }

    private fun showAddProductDialog(latLng: LatLng) {

        val view = layoutInflater.inflate(R.layout.dialog_add_product, null)

        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etDescription = view.findViewById<EditText>(R.id.etDescription)
        val etPrice = view.findViewById<EditText>(R.id.etPrice)
        val btnSelectImage = view.findViewById<Button>(R.id.btnSelectImage)

        // 📸 SELECCIONAR IMAGEN
        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 100)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setTitle("Nuevo producto")
            .setPositiveButton("Guardar", null)
            .setNeutralButton("Añadir otro", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

        // 🔥 BOTÓN GUARDAR (CIERRA)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

            val title = etTitle.text.toString()
            val description = etDescription.text.toString()
            val price = etPrice.text.toString().toDoubleOrNull()

            if (title.isEmpty() || price == null) {
                Toast.makeText(this, "Datos inválidos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveProduct(title, description, price, latLng) {
                dialog.dismiss()
            }
        }

        // 🔥 BOTÓN AÑADIR OTRO (NO CIERRA)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {

            val title = etTitle.text.toString()
            val description = etDescription.text.toString()
            val price = etPrice.text.toString().toDoubleOrNull()

            if (title.isEmpty() || price == null) {
                Toast.makeText(this, "Datos inválidos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveProduct(title, description, price, latLng) {
                Toast.makeText(this, "Producto añadido", Toast.LENGTH_SHORT).show()

                // 🔥 limpiar campos
                etTitle.setText("")
                etDescription.setText("")
                etPrice.setText("")
                imageUri = null
            }
        }
    }

    // 🔥 FUNCIÓN GUARDAR PRODUCTO (REUTILIZABLE)
    private fun saveProduct(
        title: String,
        description: String,
        price: Double,
        latLng: LatLng,
        onSuccess: () -> Unit
    ) {

        if (imageUri != null) {

            val storageRef = FirebaseStorage.getInstance().reference
            val imageRef = storageRef.child("images/${System.currentTimeMillis()}.jpg")

            imageRef.putFile(imageUri!!)
                .continueWithTask { task ->
                    if (!task.isSuccessful) throw task.exception!!
                    imageRef.downloadUrl
                }
                .addOnSuccessListener { uri ->

                    val product = hashMapOf(
                        "title" to title,
                        "description" to description,
                        "price" to price,
                        "image" to uri.toString(),
                        "lat" to latLng.latitude,
                        "lng" to latLng.longitude
                    )

                    db.collection("products").add(product)
                        .addOnSuccessListener {
                            addMarker(latLng, title, price)
                            onSuccess()
                        }
                }

        } else {

            val product = hashMapOf(
                "title" to title,
                "description" to description,
                "price" to price,
                "lat" to latLng.latitude,
                "lng" to latLng.longitude
            )

            db.collection("products").add(product)
                .addOnSuccessListener {
                    addMarker(latLng, title, price)
                    onSuccess()
                }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            imageUri = data?.data
            Toast.makeText(this, "Imagen seleccionada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addMarker(latLng: LatLng, title: String, price: Double) {
        mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet("Precio: ${price}€")
        )
    }

    private fun loadProducts() {
        db.collection("products")
            .get()
            .addOnSuccessListener { result ->
                for (doc in result) {

                    val lat = doc.getDouble("lat")
                    val lng = doc.getDouble("lng")
                    val title = doc.getString("title") ?: "Producto"

                    val priceAny = doc.get("price")
                    val price = when (priceAny) {
                        is Double -> priceAny
                        is Long -> priceAny.toDouble()
                        is String -> priceAny.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }

                    if (lat != null && lng != null) {
                        val location = LatLng(lat, lng)
                        addMarker(location, title, price)
                    }
                }
            }
    }
}