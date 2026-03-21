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

        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnFavorites?.setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }

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
            val products = marker.tag as? List<Product> ?: return@setOnMarkerClickListener false
            showProductsDialog(products)
            true
        }

        loadProducts()
    }

    // 🔥 AGRUPAR PRODUCTOS POR UBICACIÓN
    private fun loadProducts() {

        db.collection("products")
            .addSnapshotListener { result, error ->

                if (error != null || result == null) return@addSnapshotListener

                map.clear()

                val grouped = mutableMapOf<String, MutableList<Product>>()

                for (doc in result) {

                    val product = doc.toObject(Product::class.java)
                    product.id = doc.id

                    if (product.lat != null && product.lng != null) {

                        val key = "${product.lat}_${product.lng}"

                        if (!grouped.containsKey(key)) {
                            grouped[key] = mutableListOf()
                        }

                        grouped[key]?.add(product)
                    }
                }

                for ((_, products) in grouped) {

                    val first = products.first()

                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(first.lat!!, first.lng!!))
                            .title("${products.size} producto(s)")
                    )

                    marker?.tag = products
                }
            }
    }

    // 🔥 POPUP CON FLECHAS + RESERVA INDIVIDUAL
    private fun showProductsDialog(productList: List<Product>) {

        var products = productList.toMutableList()
        var currentIndex = 0

        val view = layoutInflater.inflate(R.layout.dialog_product, null)

        val img = view.findViewById<ImageView>(R.id.imgProduct)
        val name = view.findViewById<TextView>(R.id.tvName)
        val desc = view.findViewById<TextView>(R.id.tvDescription)
        val price = view.findViewById<TextView>(R.id.tvPrice)

        val btnReserve = view.findViewById<Button>(R.id.btnReserve)
        val btnNavigate = view.findViewById<Button>(R.id.btnNavigate)
        val btnFavorite = view.findViewById<Button>(R.id.btnFavorite)

        val btnPrev = view.findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = view.findViewById<ImageButton>(R.id.btnNext)
        val tvCounter = view.findViewById<TextView>(R.id.tvCounter)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        fun mostrarProducto() {

            val product = products[currentIndex]

            name.text = product.name
            desc.text = product.description
            price.text = "${product.getPriceAsDouble()} €"

            Glide.with(this)
                .load(product.imageUrl.ifEmpty { "https://via.placeholder.com/300" })
                .into(img)

            tvCounter.text = "${currentIndex + 1}/${products.size}"

            btnPrev.isEnabled = currentIndex > 0
            btnNext.isEnabled = currentIndex < products.size - 1

            btnNavigate.setOnClickListener {
                val uri = Uri.parse("google.navigation:q=${product.lat},${product.lng}")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }

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

                Toast.makeText(this@MapActivity, "Añadido a favoritos ❤️", Toast.LENGTH_SHORT).show()
            }

            btnReserve.setOnClickListener {

                val product = products[currentIndex]

                val data = hashMapOf(
                    "nombre" to product.name,
                    "precio" to product.getPriceAsDouble()
                )

                db.collection("reservas").add(data)

                db.collection("products").document(product.id).delete()

                Toast.makeText(this@MapActivity, "Producto reservado", Toast.LENGTH_SHORT).show()

                // 🔥 ELIMINAR SOLO ESTE PRODUCTO
                products.removeAt(currentIndex)

                if (products.isEmpty()) {
                    dialog.dismiss()
                } else {
                    if (currentIndex >= products.size) {
                        currentIndex = products.size - 1
                    }
                    mostrarProducto()
                }
            }
        }

        btnNext.setOnClickListener {
            if (currentIndex < products.size - 1) {
                currentIndex++
                mostrarProducto()
            }
        }

        btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                mostrarProducto()
            }
        }

        mostrarProducto()
        dialog.show()
    }

    // 🔥 CREAR PRODUCTO
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
            .setNeutralButton("Añadir otro", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {

            val btnGuardar = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnOtro = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)

            fun guardarProducto(limpiar: Boolean) {

                val nombre = name.text.toString()
                val descripcion = desc.text.toString()
                val precio = price.text.toString().toDoubleOrNull() ?: 0.0

                if (nombre.isEmpty()) {
                    Toast.makeText(this, "Introduce nombre", Toast.LENGTH_SHORT).show()
                    return
                }

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
                                name = nombre,
                                description = descripcion,
                                price = precio,
                                lat = latLng.latitude,
                                lng = latLng.longitude,
                                imageUrl = uri.toString()
                            )

                            db.collection("products").add(product)

                            Toast.makeText(this, "Producto guardado", Toast.LENGTH_SHORT).show()

                            if (limpiar) {
                                name.setText("")
                                desc.setText("")
                                price.setText("")
                                selectedImageUri = null
                            } else {
                                dialog.dismiss()
                            }
                        }

                } else {

                    val product = Product(
                        name = nombre,
                        description = descripcion,
                        price = precio,
                        lat = latLng.latitude,
                        lng = latLng.longitude,
                        imageUrl = ""
                    )

                    db.collection("products").add(product)

                    Toast.makeText(this, "Producto guardado", Toast.LENGTH_SHORT).show()

                    if (limpiar) {
                        name.setText("")
                        desc.setText("")
                        price.setText("")
                    } else {
                        dialog.dismiss()
                    }
                }
            }

            btnGuardar.setOnClickListener {
                guardarProducto(false)
            }

            btnOtro.setOnClickListener {
                guardarProducto(true)
            }
        }

        dialog.show()
    }
}