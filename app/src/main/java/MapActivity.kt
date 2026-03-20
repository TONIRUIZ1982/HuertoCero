package com.huertocero.huertocero

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private val db = FirebaseFirestore.getInstance()

    private var selectedImageUri: Uri? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                Toast.makeText(this, "Imagen seleccionada", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnFavorites = findViewById<Button?>(R.id.btnFavorites)
        val btnReservations = findViewById<Button?>(R.id.btnReservations)

        // 🔴 LOGOUT
        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // ❤️ FAVORITOS
        btnFavorites?.setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }

        // 📦 RESERVAS
        btnReservations?.setOnClickListener {
            startActivity(Intent(this, ReservationsActivity::class.java))
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        val valencia = LatLng(39.4699, -0.3763)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(valencia, 12f))

        map.setOnMapClickListener { latLng ->
            showAddProductDialog(latLng)
        }

        map.setOnMarkerClickListener { marker ->
            val product = marker.tag as? Product ?: return@setOnMarkerClickListener false
            showProductDialog(product)
            true
        }

        loadProducts()
    }

    private fun loadProducts() {
        db.collection("products").get().addOnSuccessListener { result ->

            map.clear()

            for (doc in result) {

                val product = doc.toObject(Product::class.java)
                product.id = doc.id

                if (product.lat != null && product.lng != null) {

                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(product.lat!!, product.lng!!))
                            .title(product.name ?: "Producto")
                    )

                    marker?.tag = product
                }
            }
        }
    }

    private fun showAddProductDialog(latLng: LatLng) {

        selectedImageUri = null

        val view = layoutInflater.inflate(R.layout.dialog_add_product, null)

        val name = view.findViewById<EditText>(R.id.etName)
        val desc = view.findViewById<EditText>(R.id.etDescription)
        val price = view.findViewById<EditText>(R.id.etPrice)
        val btnImage = view.findViewById<Button>(R.id.btnImage)

        btnImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Nuevo producto")
            .setView(view)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {

            val btnGuardar = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            btnGuardar.setOnClickListener {

                val storageRef = FirebaseStorage.getInstance().reference

                if (selectedImageUri != null) {

                    val fileName = "images/${System.currentTimeMillis()}.jpg"
                    val imageRef = storageRef.child(fileName)

                    imageRef.putFile(selectedImageUri!!)
                        .continueWithTask { task ->
                            if (!task.isSuccessful) throw task.exception!!
                            imageRef.downloadUrl
                        }
                        .addOnSuccessListener { uri ->

                            val product = Product(
                                name = name.text.toString(),
                                description = desc.text.toString(),
                                price = price.text.toString().toDoubleOrNull() ?: 0.0,
                                lat = latLng.latitude,
                                lng = latLng.longitude,
                                imageUrl = uri.toString()
                            )

                            db.collection("products").add(product)

                            Toast.makeText(this, "Producto guardado", Toast.LENGTH_SHORT).show()
                            loadProducts()
                            dialog.dismiss()
                        }

                } else {

                    val product = Product(
                        name = name.text.toString(),
                        description = desc.text.toString(),
                        price = price.text.toString().toDoubleOrNull() ?: 0.0,
                        lat = latLng.latitude,
                        lng = latLng.longitude,
                        imageUrl = ""
                    )

                    db.collection("products").add(product)

                    Toast.makeText(this, "Producto guardado", Toast.LENGTH_SHORT).show()
                    loadProducts()
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showProductDialog(product: Product) {

        val view = layoutInflater.inflate(R.layout.dialog_product, null)

        val img = view.findViewById<ImageView>(R.id.imgProduct)
        val name = view.findViewById<TextView>(R.id.tvName)
        val desc = view.findViewById<TextView>(R.id.tvDescription)
        val price = view.findViewById<TextView>(R.id.tvPrice)
        val btnReserve = view.findViewById<Button>(R.id.btnReserve)
        val btnNavigate = view.findViewById<Button>(R.id.btnNavigate)
        val btnFavorite = view.findViewById<Button>(R.id.btnFavorite)

        name.text = product.name
        desc.text = product.description
        price.text = product.getPriceAsDouble().toString() + " €"

        Glide.with(this)
            .load(product.imageUrl.ifEmpty { "https://via.placeholder.com/300" })
            .into(img)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        // ❤️ FAVORITOS
        btnFavorite.setOnClickListener {

            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener

            val data = hashMapOf(
                "productId" to product.id,
                "name" to product.name,
                "price" to product.getPriceAsDouble()
            )

            db.collection("users")
                .document(userId)
                .collection("favorites")
                .document(product.id)
                .set(data)

            Toast.makeText(this, "Añadido a favoritos ❤️", Toast.LENGTH_SHORT).show()
        }

        // 🗺️ NAVEGAR
        btnNavigate.setOnClickListener {
            val uri = Uri.parse("google.navigation:q=${product.lat},${product.lng}")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }

        // 🛒 RESERVAR
        btnReserve.setOnClickListener {

            val data = hashMapOf(
                "nombre" to product.name,
                "precio" to product.getPriceAsDouble()
            )

            // guardar reserva
            db.collection("reservas").add(data)

            db.collection("products").document(product.id).delete()

            Toast.makeText(this, "Producto reservado", Toast.LENGTH_SHORT).show()

            dialog.dismiss()
            loadProducts()
        }

        dialog.show()
    }
}