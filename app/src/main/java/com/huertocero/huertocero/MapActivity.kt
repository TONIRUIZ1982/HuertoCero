package com.huertocero.huertocero

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlin.math.abs

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var selectedImageUri: Uri? = null
    private var addProductImagePreview: ImageView? = null
    private var addProductImageButton: Button? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                addProductImagePreview?.setImageURI(uri)
                addProductImageButton?.text = "Cambiar imagen"
                Toast.makeText(this, "Imagen lista para subir", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        findViewById<Button?>(R.id.btnPrivacy)?.setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }

        findViewById<Button?>(R.id.btnFavorites)?.setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }

        findViewById<Button?>(R.id.btnReservations)?.setOnClickListener {
            startActivity(Intent(this, ReservationsActivity::class.java))
        }

        findViewById<Button?>(R.id.btnMessages)?.setOnClickListener {
            startActivity(Intent(this, ChatsActivity::class.java))
        }

        findViewById<Button?>(R.id.btnMyProducts)?.setOnClickListener {
            startActivity(Intent(this, MyProductsActivity::class.java))
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

        findViewById<Button?>(R.id.btnAddProduct)?.setOnClickListener {
            showAddProductDialog(map.cameraPosition.target)
        }

        map.setOnMarkerClickListener { marker ->
            val products = marker.tag as? List<Product> ?: return@setOnMarkerClickListener false
            showProductsDialog(products)
            true
        }

        loadProducts()
    }

    private fun loadProducts() {
        db.collection("products")
            .addSnapshotListener { result, error ->
                if (error != null || result == null) return@addSnapshotListener

                map.clear()
                val grouped = mutableMapOf<String, MutableList<Product>>()
                var validProductCount = 0

                for (doc in result) {
                    val product = doc.toObject(Product::class.java)
                    product.id = doc.id

                    val lat = product.lat
                    val lng = product.lng

                    if (lat == null || lng == null) continue

                    validProductCount++
                    grouped.getOrPut("${lat}_${lng}") { mutableListOf() }.add(product)
                }

                findViewById<TextView?>(R.id.tvMapHint)?.text = if (validProductCount == 0) {
                    "Publica el primer producto moviendo el mapa y usando el boton inferior."
                } else {
                    "$validProductCount productos frescos publicados. Toca un marcador para verlos."
                }

                for ((_, products) in grouped) {
                    val first = products.firstOrNull() ?: continue
                    val lat = first.lat
                    val lng = first.lng

                    if (lat == null || lng == null) continue

                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title("${products.size} producto(s)")
                    )

                    marker?.tag = products
                }
            }
    }

    private fun showProductsDialog(productList: List<Product>) {
        val products = productList.toMutableList()
        var currentIndex = 0

        val view = layoutInflater.inflate(R.layout.dialog_product, null)

        val productDialogContent = view.findViewById<View>(R.id.productDialogContent)
        val img = view.findViewById<ImageView>(R.id.imgProduct)
        val name = view.findViewById<TextView>(R.id.tvName)
        val desc = view.findViewById<TextView>(R.id.tvDescription)
        val price = view.findViewById<TextView>(R.id.tvPrice)
        val swipeHint = view.findViewById<TextView>(R.id.tvSwipeHint)

        val btnReserve = view.findViewById<Button>(R.id.btnReserve)
        val btnNavigate = view.findViewById<Button>(R.id.btnNavigate)
        val btnFavorite = view.findViewById<Button>(R.id.btnFavorite)
        val btnChat = view.findViewById<Button>(R.id.btnChat)
        val btnReport = view.findViewById<Button>(R.id.btnReport)

        val btnPrev = view.findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = view.findViewById<ImageButton>(R.id.btnNext)
        val tvCounter = view.findViewById<TextView>(R.id.tvCounter)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        fun showProduct(direction: Int = 0) {
            val product = products[currentIndex]

            name.text = product.name
            desc.text = product.description
            price.text = "${product.getPriceAsDouble()} EUR"

            Glide.with(this)
                .load(product.imageUrl.ifEmpty { "https://via.placeholder.com/300" })
                .into(img)

            tvCounter.text = "${currentIndex + 1}/${products.size}"
            swipeHint.visibility = if (products.size > 1) View.VISIBLE else View.GONE

            btnPrev.isEnabled = currentIndex > 0
            btnNext.isEnabled = currentIndex < products.size - 1

            if (direction != 0) {
                img.alpha = 0f
                img.translationX = if (direction > 0) 48f else -48f
                img.animate().alpha(1f).translationX(0f).setDuration(180L).start()
            }

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

                Toast.makeText(this, "Guardado en favoritos", Toast.LENGTH_SHORT).show()
            }

            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            btnChat.visibility =
                if (product.sellerId.isNotBlank() && product.sellerId != currentUserId) View.VISIBLE else View.GONE
            btnChat.setOnClickListener {
                startProductChat(product)
            }

            btnReport.setOnClickListener {
                reportProduct(product)
            }

            btnReserve.setOnClickListener {
                val buyerId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener

                db.collection("reservas").add(
                    mapOf(
                        "productId" to product.id,
                        "nombre" to product.name,
                        "precio" to product.getPriceAsDouble(),
                        "buyerId" to buyerId,
                        "sellerId" to product.sellerId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                db.collection("products").document(product.id).delete()

                Toast.makeText(this, "Producto reservado", Toast.LENGTH_SHORT).show()
                products.removeAt(currentIndex)

                if (products.isEmpty()) {
                    dialog.dismiss()
                } else {
                    if (currentIndex >= products.size) currentIndex = products.size - 1
                    showProduct()
                }
            }
        }

        fun goToProduct(newIndex: Int) {
            if (newIndex !in products.indices || newIndex == currentIndex) return

            val direction = if (newIndex > currentIndex) 1 else -1
            currentIndex = newIndex
            showProduct(direction)
        }

        btnNext.setOnClickListener { goToProduct(currentIndex + 1) }
        btnPrev.setOnClickListener { goToProduct(currentIndex - 1) }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val startEvent = e1 ?: return false
                val diffX = e2.x - startEvent.x
                val diffY = e2.y - startEvent.y

                if (abs(diffX) > abs(diffY) && abs(diffX) > 90f && abs(velocityX) > 120f) {
                    if (diffX < 0) {
                        goToProduct(currentIndex + 1)
                    } else {
                        goToProduct(currentIndex - 1)
                    }
                    return true
                }

                return false
            }
        })

        productDialogContent.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        showProduct()
        dialog.show()
    }

    private fun showAddProductDialog(latLng: LatLng) {
        selectedImageUri = null

        val view = layoutInflater.inflate(R.layout.dialog_add_product, null)

        val name = view.findViewById<EditText>(R.id.etName)
        val desc = view.findViewById<EditText>(R.id.etDescription)
        val price = view.findViewById<EditText>(R.id.etPrice)
        val preview = view.findViewById<ImageView>(R.id.imgPreview)
        val btnImage = view.findViewById<Button>(R.id.btnImage)

        addProductImagePreview = preview
        addProductImageButton = btnImage

        btnImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Nuevo producto")
            .setView(view)
            .setPositiveButton("Guardar", null)
            .setNeutralButton("Agregar otro", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnDismissListener {
            addProductImagePreview = null
            addProductImageButton = null
            selectedImageUri = null
        }

        dialog.setOnShowListener {
            val btnGuardar = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnOtro = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)

            fun guardar(limpiar: Boolean) {
                val nombre = name.text.toString().trim()
                val descripcion = desc.text.toString().trim()
                val precio = price.text.toString().toDoubleOrNull() ?: 0.0

                if (nombre.isEmpty()) {
                    Toast.makeText(this, "Introduce nombre", Toast.LENGTH_SHORT).show()
                    return
                }

                setDialogButtonsEnabled(btnGuardar, btnOtro, false)

                saveProductWithImage(
                    nombre = nombre,
                    descripcion = descripcion,
                    precio = precio,
                    lat = latLng.latitude,
                    lng = latLng.longitude
                ) { success ->
                    setDialogButtonsEnabled(btnGuardar, btnOtro, true)

                    if (!success) return@saveProductWithImage

                    Toast.makeText(this, "Producto guardado", Toast.LENGTH_SHORT).show()

                    if (limpiar) {
                        name.setText("")
                        desc.setText("")
                        price.setText("")
                        selectedImageUri = null
                        preview.setImageResource(R.drawable.auth_hero_market)
                        btnImage.text = "Agregar imagen"
                    } else {
                        dialog.dismiss()
                    }
                }
            }

            btnGuardar.setOnClickListener { guardar(false) }
            btnOtro.setOnClickListener { guardar(true) }
        }

        dialog.show()
    }

    private fun saveProductWithImage(
        nombre: String,
        descripcion: String,
        precio: Double,
        lat: Double,
        lng: Double,
        onComplete: (Boolean) -> Unit
    ) {
        val imageUri = selectedImageUri

        fun saveProduct(imageUrl: String) {
            val product = Product(
                name = nombre,
                description = descripcion,
                price = precio,
                lat = lat,
                lng = lng,
                imageUrl = imageUrl,
                sellerId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            )

            db.collection("products").add(product)
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener {
                    Toast.makeText(this, "No se pudo guardar el producto", Toast.LENGTH_LONG).show()
                    onComplete(false)
                }
        }

        if (imageUri == null) {
            saveProduct("")
            return
        }

        val imageRef = storage.reference
            .child("product_images/${System.currentTimeMillis()}_${imageUri.lastPathSegment ?: "product"}.jpg")

        imageRef.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let { throw it }
                }
                imageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                saveProduct(downloadUri.toString())
            }
            .addOnFailureListener {
                Toast.makeText(this, "No se pudo subir la imagen", Toast.LENGTH_LONG).show()
                onComplete(false)
            }
    }

    private fun setDialogButtonsEnabled(saveButton: Button, addAnotherButton: Button, enabled: Boolean) {
        saveButton.isEnabled = enabled
        addAnotherButton.isEnabled = enabled
        saveButton.text = if (enabled) "Guardar" else "Guardando..."
        addAnotherButton.text = if (enabled) "Agregar otro" else "Guardando..."
    }

    private fun startProductChat(product: Product) {
        val buyerId = FirebaseAuth.getInstance().currentUser?.uid
        val sellerId = product.sellerId

        if (buyerId == null || sellerId.isBlank()) {
            Toast.makeText(this, "Este producto aun no tiene vendedor asociado", Toast.LENGTH_SHORT).show()
            return
        }

        val conversationId = listOf(product.id, buyerId, sellerId).sorted().joinToString("_")
        val conversationData = mapOf(
            "productId" to product.id,
            "productName" to product.name,
            "buyerId" to buyerId,
            "sellerId" to sellerId,
            "participants" to listOf(buyerId, sellerId),
            "lastMessage" to "",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("conversations")
            .document(conversationId)
            .set(conversationData, SetOptions.merge())
            .addOnSuccessListener {
                startActivity(
                    Intent(this, ChatActivity::class.java)
                        .putExtra("conversationId", conversationId)
                        .putExtra("productName", product.name)
                )
            }
            .addOnFailureListener {
                Toast.makeText(this, "No se pudo abrir el chat", Toast.LENGTH_SHORT).show()
            }
    }

    private fun reportProduct(product: Product) {
        val reporterId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val reasons = arrayOf("Producto falso", "Contenido inapropiado", "Precio o informacion enganosa", "Otro motivo")

        AlertDialog.Builder(this)
            .setTitle("Reportar producto")
            .setItems(reasons) { _, which ->
                db.collection("reports").add(
                    mapOf(
                        "type" to "product",
                        "productId" to product.id,
                        "productName" to product.name,
                        "sellerId" to product.sellerId,
                        "reporterId" to reporterId,
                        "reason" to reasons[which],
                        "status" to "new",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                Toast.makeText(this, "Reporte enviado para revision", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
