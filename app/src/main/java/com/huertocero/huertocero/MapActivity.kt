package com.huertocero.huertocero

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.abs

class MapActivity : HuertoActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var selectedImageUri: Uri? = null
    private var addProductImagePreview: ImageView? = null
    private var addProductImageButton: Button? = null
    private val allProducts = mutableListOf<Product>()
    private var selectedCategoryFilter = ProductCategories.FILTER_ALL

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                centerMapOnUserLocation()
            } else {
                moveToFallbackLocation()
                Toast.makeText(this, getString(R.string.location_permission_needed), Toast.LENGTH_SHORT).show()
            }
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                addProductImagePreview?.setImageURI(uri)
                addProductImageButton?.text = getString(R.string.change_image)
                Toast.makeText(this, getString(R.string.image_ready), Toast.LENGTH_SHORT).show()
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

        findViewById<Button?>(R.id.btnLanguage)?.setOnClickListener {
            LanguageDialog.show(this)
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

        centerMapForCurrentUser()
        updateFilterButtonText()

        map.setOnMapClickListener { latLng ->
            showAddProductDialog(latLng)
        }

        findViewById<Button?>(R.id.btnAddProduct)?.setOnClickListener {
            showAddProductDialog(map.cameraPosition.target)
        }

        findViewById<Button?>(R.id.btnFilter)?.setOnClickListener {
            showCategoryFilterDialog()
        }

        map.setOnMarkerClickListener { marker ->
            val products = (marker.tag as? List<*>)
                ?.filterIsInstance<Product>()
                ?.takeIf { it.isNotEmpty() }
                ?: return@setOnMarkerClickListener false
            showProductsDialog(products)
            true
        }

        loadProducts()
    }

    private fun centerMapForCurrentUser() {
        if (hasLocationPermission()) {
            centerMapOnUserLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun centerMapOnUserLocation() {
        if (!::map.isInitialized || !hasLocationPermission()) {
            moveToFallbackLocation()
            return
        }

        runCatching {
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
        }

        val location = getBestLastKnownLocation()
        if (location != null) {
            moveToLocation(location)
        } else {
            requestFreshLocationOrFallback()
        }
    }

    private fun moveToLocation(location: Location) {
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                13f
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation(): Location? {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.getProviders(true)
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocationOrFallback() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        if (provider == null) {
            moveToFallbackLocation()
            Toast.makeText(this, getString(R.string.location_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var centered = false
        lateinit var listener: LocationListener

        val timeout = Runnable {
            if (!centered) {
                runCatching { manager.removeUpdates(listener) }
                moveToFallbackLocation()
                Toast.makeText(this, getString(R.string.location_unavailable), Toast.LENGTH_SHORT).show()
            }
        }

        listener = LocationListener { location ->
            centered = true
            handler.removeCallbacks(timeout)
            runCatching { manager.removeUpdates(listener) }
            moveToLocation(location)
        }

        runCatching {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            handler.postDelayed(timeout, 5000L)
        }.onFailure {
            handler.removeCallbacks(timeout)
            moveToFallbackLocation()
            Toast.makeText(this, getString(R.string.location_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveToFallbackLocation() {
        val spainCenter = LatLng(40.4168, -3.7038)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(spainCenter, 6f))
    }

    private fun loadProducts() {
        db.collection("products")
            .addSnapshotListener { result, error ->
                if (error != null || result == null) return@addSnapshotListener

                allProducts.clear()

                for (doc in result) {
                    val product = doc.toObject(Product::class.java)
                    product.id = doc.id

                    val lat = product.lat
                    val lng = product.lng

                    if (lat == null || lng == null) continue

                    allProducts.add(product)
                }

                renderProductsOnMap()
            }
    }

    private fun renderProductsOnMap() {
        if (!::map.isInitialized) return

        map.clear()
        val grouped = mutableMapOf<String, MutableList<Product>>()
        val filteredProducts = allProducts.filter {
            selectedCategoryFilter == ProductCategories.FILTER_ALL ||
                ProductCategories.normalize(it.category) == selectedCategoryFilter
        }

        for (product in filteredProducts) {
            val lat = product.lat
            val lng = product.lng

            if (lat == null || lng == null) continue

            grouped.getOrPut("${lat}_${lng}") { mutableListOf() }.add(product)
        }

        findViewById<TextView?>(R.id.tvMapHint)?.text = if (filteredProducts.isEmpty()) {
            if (selectedCategoryFilter == ProductCategories.FILTER_ALL) {
                getString(R.string.publish_first_product)
            } else {
                getString(R.string.no_category_products, filterDisplayName(selectedCategoryFilter))
            }
        } else {
            getString(R.string.published_products_count, filteredProducts.size)
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
                    .icon(createMarkerIcon(products.size))
                    .anchor(0.5f, 1f)
            )

            marker?.tag = products
        }
    }

    private fun showCategoryFilterDialog() {
        val filterLabels = ProductCategories.filters
            .map { filterDisplayName(it) }
            .toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.filter_products))
            .setItems(filterLabels) { _, which ->
                selectedCategoryFilter = ProductCategories.filters[which]
                updateFilterButtonText()
                renderProductsOnMap()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun filterDisplayName(filter: String): String {
        return if (filter == ProductCategories.FILTER_ALL) {
            getString(R.string.category_all)
        } else {
            getString(ProductCategories.labelRes(filter))
        }
    }

    private fun updateFilterButtonText() {
        findViewById<Button?>(R.id.btnFilter)?.text =
            getString(R.string.filter_label, filterDisplayName(selectedCategoryFilter))
    }

    private fun createMarkerIcon(count: Int) =
        BitmapDescriptorFactory.fromBitmap(createMarkerBitmap(count))

    private fun createMarkerBitmap(count: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(96, 118, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#274C2E")
            style = Paint.Style.FILL
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#89C64A")
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 32f
            isFakeBoldText = true
        }

        val path = Path().apply {
            moveTo(48f, 114f)
            cubicTo(22f, 82f, 10f, 64f, 10f, 42f)
            cubicTo(10f, 18f, 27f, 4f, 48f, 4f)
            cubicTo(69f, 4f, 86f, 18f, 86f, 42f)
            cubicTo(86f, 64f, 74f, 82f, 48f, 114f)
            close()
        }

        canvas.drawPath(path, pinPaint)
        canvas.drawCircle(48f, 42f, 28f, accentPaint)
        canvas.drawText(count.toString(), 48f, 53f, textPaint)

        return bitmap
    }

    private fun showProductsDialog(productList: List<Product>) {
        val products = productList.toMutableList()
        var currentIndex = 0

        val view = layoutInflater.inflate(R.layout.dialog_product, null)

        val img = view.findViewById<ImageView>(R.id.imgProduct)
        val name = view.findViewById<TextView>(R.id.tvName)
        val desc = view.findViewById<TextView>(R.id.tvDescription)
        val price = view.findViewById<TextView>(R.id.tvPrice)
        val swipeHint = view.findViewById<TextView>(R.id.tvSwipeHint)
        val category = view.findViewById<TextView>(R.id.tvCategory)
        val sellerRating = view.findViewById<TextView>(R.id.tvSellerRating)

        val btnReserve = view.findViewById<Button>(R.id.btnReserve)
        val btnNavigate = view.findViewById<Button>(R.id.btnNavigate)
        val btnFavorite = view.findViewById<Button>(R.id.btnFavorite)
        val btnChat = view.findViewById<Button>(R.id.btnChat)
        val btnReport = view.findViewById<Button>(R.id.btnReport)
        val btnSeller = view.findViewById<Button>(R.id.btnSeller)
        val btnReview = view.findViewById<Button>(R.id.btnReview)

        val btnPrev = view.findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = view.findViewById<ImageButton>(R.id.btnNext)
        val tvCounter = view.findViewById<TextView>(R.id.tvCounter)

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(view)

        fun showProduct(direction: Int = 0) {
            val product = products[currentIndex]

            name.text = product.name
            desc.text = product.description
            price.text = "${product.getPriceAsDouble()} EUR"
            category.text = getString(ProductCategories.labelRes(product.category))

            Glide.with(this)
                .load(product.imageUrl.ifEmpty { "https://via.placeholder.com/300" })
                .into(img)

            tvCounter.text = "${currentIndex + 1}/${products.size}"
            swipeHint.visibility = if (products.size > 1) View.VISIBLE else View.GONE

            btnPrev.isEnabled = currentIndex > 0
            btnNext.isEnabled = currentIndex < products.size - 1
            btnPrev.alpha = if (btnPrev.isEnabled) 1f else 0.35f
            btnNext.alpha = if (btnNext.isEnabled) 1f else 0.35f

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

                Toast.makeText(this, getString(R.string.favorite_saved), Toast.LENGTH_SHORT).show()
            }

            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            btnChat.visibility =
                if (product.sellerId.isNotBlank() && product.sellerId != currentUserId) View.VISIBLE else View.GONE
            btnReview.visibility =
                if (product.sellerId.isNotBlank() && product.sellerId != currentUserId) View.VISIBLE else View.GONE
            btnChat.setOnClickListener {
                startProductChat(product)
            }

            btnSeller.visibility = if (product.sellerId.isNotBlank()) View.VISIBLE else View.GONE
            btnSeller.setOnClickListener {
                startActivity(
                    Intent(this, SellerProfileActivity::class.java)
                        .putExtra("sellerId", product.sellerId)
                )
            }

            btnReview.setOnClickListener {
                showReviewDialog(product)
            }

            loadSellerRating(product.sellerId, sellerRating)

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

                Toast.makeText(this, getString(R.string.product_reserved), Toast.LENGTH_SHORT).show()
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

        img.setOnTouchListener { _, event ->
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
        val categorySpinner = view.findViewById<Spinner>(R.id.spinnerCategory)

        addProductImagePreview = preview
        addProductImageButton = btnImage
        configureCategorySpinner(categorySpinner)

        btnImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.new_product))
            .setView(view)
            .setPositiveButton(getString(R.string.save), null)
            .setNeutralButton(getString(R.string.add_another), null)
            .setNegativeButton(getString(R.string.cancel), null)
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
                val categoria = ProductCategories.all.getOrElse(categorySpinner.selectedItemPosition) {
                    ProductCategories.OTHER
                }

                if (nombre.isEmpty()) {
                    Toast.makeText(this, getString(R.string.fill_name), Toast.LENGTH_SHORT).show()
                    return
                }

                setDialogButtonsEnabled(btnGuardar, btnOtro, false)

                saveProductWithImage(
                    nombre = nombre,
                    descripcion = descripcion,
                    precio = precio,
                    categoria = categoria,
                    lat = latLng.latitude,
                    lng = latLng.longitude
                ) { success ->
                    setDialogButtonsEnabled(btnGuardar, btnOtro, true)

                    if (!success) return@saveProductWithImage

                    Toast.makeText(this, getString(R.string.product_saved), Toast.LENGTH_SHORT).show()

                    if (limpiar) {
                        name.setText("")
                        desc.setText("")
                        price.setText("")
                        selectedImageUri = null
                        preview.setImageResource(R.drawable.auth_hero_market)
                        btnImage.text = getString(R.string.add_image)
                        categorySpinner.setSelection(ProductCategories.all.indexOf(ProductCategories.OTHER))
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
        categoria: String,
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
                sellerId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                category = ProductCategories.normalize(categoria)
            )

            db.collection("products").add(product)
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener {
                    Toast.makeText(this, getString(R.string.product_save_error), Toast.LENGTH_LONG).show()
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
                Toast.makeText(this, getString(R.string.image_upload_error), Toast.LENGTH_LONG).show()
                onComplete(false)
            }
    }

    private fun setDialogButtonsEnabled(saveButton: Button, addAnotherButton: Button, enabled: Boolean) {
        saveButton.isEnabled = enabled
        addAnotherButton.isEnabled = enabled
        saveButton.text = if (enabled) getString(R.string.save) else getString(R.string.saving)
        addAnotherButton.text = if (enabled) getString(R.string.add_another) else getString(R.string.saving)
    }

    private fun configureCategorySpinner(spinner: Spinner, selectedCategory: String = ProductCategories.OTHER) {
        val categoryLabels = ProductCategories.all.map { getString(ProductCategories.labelRes(it)) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val selectedIndex = ProductCategories.all.indexOf(ProductCategories.normalize(selectedCategory))
        spinner.setSelection(selectedIndex.coerceAtLeast(0))
    }

    private fun loadSellerRating(sellerId: String, target: TextView) {
        if (sellerId.isBlank()) {
            target.text = getString(R.string.seller_pending)
            return
        }

        db.collection("reviews")
            .whereEqualTo("sellerId", sellerId)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    target.text = getString(R.string.seller_new)
                    return@addOnSuccessListener
                }

                val ratings = result.documents.mapNotNull { it.getDouble("rating") }
                val average = ratings.average()
                target.text = getString(R.string.seller_rating, "%.1f".format(average), ratings.size)
            }
            .addOnFailureListener {
                target.text = getString(R.string.rating_unavailable)
            }
    }

    private fun showReviewDialog(product: Product) {
        val reviewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ratings = arrayOf("5 - Excelente", "4 - Muy bien", "3 - Correcto", "2 - Mejorable", "1 - Mala experiencia")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rate_seller))
            .setItems(ratings) { _, which ->
                val rating = 5 - which
                db.collection("reviews").add(
                    mapOf(
                        "sellerId" to product.sellerId,
                        "productId" to product.id,
                        "productName" to product.name,
                        "reviewerId" to reviewerId,
                        "rating" to rating,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                Toast.makeText(this, getString(R.string.rating_sent), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun startProductChat(product: Product) {
        val buyerId = FirebaseAuth.getInstance().currentUser?.uid
        val sellerId = product.sellerId

        if (buyerId == null || sellerId.isBlank()) {
            Toast.makeText(this, getString(R.string.no_seller_for_product), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, getString(R.string.chat_open_error), Toast.LENGTH_SHORT).show()
            }
    }

    private fun reportProduct(product: Product) {
        val reporterId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val reasons = arrayOf(
            getString(R.string.fake_product),
            getString(R.string.inappropriate_content),
            getString(R.string.misleading_price),
            getString(R.string.other_reason)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.report_product))
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
                Toast.makeText(this, getString(R.string.report_sent), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
