package com.huertocero.huertocero

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.android.material.bottomsheet.BottomSheetDialog


import kotlin.math.abs
import java.util.Locale
import java.util.TimeZone

class MapActivity : HuertoActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var selectedImageUri: Uri? = null
    private var addProductImagePreview: ImageView? = null
    private var addProductImageButton: Button? = null
    private val allProducts = mutableListOf<Product>()
    private val knownProductIds = mutableSetOf<String>()
    private var hasLoadedInitialProducts = false
    private var selectedCategoryFilter = ProductCategories.FILTER_ALL
    private var isMapMode = false
    private var activeSearchQuery = ""
    private var searchSortMode = SearchSort.RELEVANCE
    private var userLatLng: LatLng? = null
    private val followedCategoryCache = mutableSetOf<String>()
    private val followedSellerCache = mutableSetOf<String>()

    private enum class SearchSort {
        RELEVANCE,
        DISTANCE,
        PRICE_LOW,
        PRICE_HIGH,
        STOCK
    }

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

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        EngagementTracker.trackDailyOpen()
        loadImpactSummary()
        loadFollowSignals()

        val actionButtons = listOfNotNull(
            findViewById<Button?>(R.id.btnLogout),
            findViewById<View?>(R.id.btnPrivacy),
            findViewById<View?>(R.id.btnFavorites),
            findViewById<View?>(R.id.btnMessages),
            findViewById<Button?>(R.id.btnFilter),
            findViewById<View?>(R.id.btnQuickSell),
            findViewById<View?>(R.id.btnQuickMap),
            findViewById<View?>(R.id.btnQuickReservations),
            findViewById<View?>(R.id.btnQuickFavorites),
            findViewById<View?>(R.id.btnQuickInvite),
            findViewById<View?>(R.id.btnQuickImpact)
        )
        UiMotion.makePressable(*actionButtons.toTypedArray())
        UiMotion.reveal(
            findViewById(R.id.homeTopBar),
            findViewById(R.id.quickActionsGrid),
            findViewById(R.id.productFeedPanel),
            findViewById(R.id.bottomNav)
        )
        playScreenTransition()

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        findViewById<View?>(R.id.btnPrivacy)?.setOnClickListener {
            showAccountPanel()
        }

        findViewById<View?>(R.id.btnFavorites)?.setOnClickListener {
            setMapMode(false)
            findViewById<HorizontalScrollView?>(R.id.productRailScroll)?.smoothScrollTo(0, 0)
        }

        findViewById<View?>(R.id.searchBar)?.setOnClickListener {
            showSearchPanel()
        }

        findViewById<View?>(R.id.btnMessages)?.setOnClickListener {
            startActivity(Intent(this, ChatsActivity::class.java))
        }

        findViewById<View?>(R.id.btnQuickSell)?.setOnClickListener {
            showAddProductAtCurrentLocation()
        }

        findViewById<View?>(R.id.btnQuickMap)?.setOnClickListener {
            setMapMode(true)
        }

        findViewById<View?>(R.id.btnQuickReservations)?.setOnClickListener {
            startActivity(Intent(this, ReservationsActivity::class.java))
        }

        findViewById<View?>(R.id.btnQuickFavorites)?.setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }

        findViewById<View?>(R.id.btnQuickInvite)?.setOnClickListener {
            shareHuertoCero()
        }

        findViewById<View?>(R.id.btnQuickImpact)?.setOnClickListener {
            showImpactPanel()
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
            if (isMapMode) showAddProductDialog(latLng)
        }

        findViewById<Button?>(R.id.btnFilter)?.setOnClickListener {
            showSearchPanel(openFilters = true)
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
        setMapMode(false)
    }

    private fun setMapMode(enabled: Boolean) {
        isMapMode = enabled
        val mapView = findViewById<View?>(R.id.map)
        val watermark = findViewById<View?>(R.id.brandWatermark)
        val topBar = findViewById<View?>(R.id.homeTopBar)
        val quickActions = findViewById<View?>(R.id.quickActionsGrid)
        val feedPanel = findViewById<View?>(R.id.productFeedPanel)
        val mapCreateHint = findViewById<View?>(R.id.mapCreateHint)
        val homeButton = findViewById<View?>(R.id.btnFavorites)
        val chatButton = findViewById<View?>(R.id.btnMessages)
        val profileButton = findViewById<View?>(R.id.btnPrivacy)

        listOf(mapView, watermark, topBar, quickActions, feedPanel, mapCreateHint).forEach { it?.animate()?.cancel() }
        mapView?.animate()
            ?.alpha(if (enabled) 1f else 0f)
            ?.setDuration(420L)
            ?.start()

        if (enabled) {
            watermark?.animate()
                ?.alpha(0f)
                ?.setDuration(190L)
                ?.withEndAction { watermark.visibility = View.GONE }
                ?.start()
            UiMotion.hideSurface(topBar, toY = -22f)
            UiMotion.hideSurface(quickActions, toY = -14f)
            UiMotion.hideSurface(feedPanel, toY = 26f)
            UiMotion.showSurface(mapCreateHint, fromY = -20f, delay = 140L)
        } else {
            UiMotion.hideSurface(mapCreateHint, toY = -14f)
            watermark?.visibility = View.VISIBLE
            watermark?.alpha = 0f
            watermark?.animate()
                ?.alpha(0.075f)
                ?.setStartDelay(30L)
                ?.setDuration(380L)
                ?.start()
            UiMotion.showSurface(topBar, fromY = -18f, delay = 70L)
            UiMotion.showSurface(quickActions, fromY = 22f, delay = 130L)
            UiMotion.showSurface(feedPanel, fromY = 26f, delay = 190L)
        }

        setNavButtonActive(homeButton, !enabled)
        setNavButtonActive(chatButton, false)
        setNavButtonActive(profileButton, false)

        if (enabled && ::map.isInitialized) {
            centerMapOnUserLocation()
            renderProductsOnMap()
        } else if (!enabled) {
            renderProductRail(filteredProductsForCurrentCategory())
        }
    }

    private fun setNavButtonActive(button: View?, active: Boolean) {
        button ?: return
        button.setBackgroundResource(if (active) R.drawable.nav_item_active_background else android.R.color.transparent)
        val color = ContextCompat.getColor(this, if (active) android.R.color.white else R.color.muted_ink)
        button.isSelected = active
        button.animate()
            .scaleX(if (active) 1.02f else 1f)
            .scaleY(if (active) 1.02f else 1f)
            .setDuration(180L)
            .start()

        if (button is LinearLayout) {
            for (index in 0 until button.childCount) {
                when (val child = button.getChildAt(index)) {
                    is ImageView -> child.setColorFilter(color)
                    is TextView -> child.setTextColor(color)
                }
            }
        } else if (button is Button) {
            button.setTextColor(color)
        }
    }

    private fun playScreenTransition(onMidpoint: (() -> Unit)? = null) {
        val overlay = findViewById<View?>(R.id.transitionOverlay) ?: return
        val logo = findViewById<View?>(R.id.transitionLogo) ?: return

        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        logo.scaleX = 0.82f
        logo.scaleY = 0.82f

        overlay.animate()
            .alpha(0.96f)
            .setDuration(180L)
            .withEndAction {
                onMidpoint?.invoke()
                logo.animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .setDuration(160L)
                    .withEndAction {
                        overlay.animate()
                            .alpha(0f)
                            .setDuration(260L)
                            .withEndAction {
                                overlay.visibility = View.GONE
                            }
                            .start()
                    }
                    .start()
            }
            .start()
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

        requestFreshLocationOrFallback()
    }

    private fun moveToLocation(location: Location) {
        userLatLng = LatLng(location.latitude, location.longitude)
        NotificationProfile.syncLocation(userLatLng ?: return)
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                userLatLng ?: LatLng(location.latitude, location.longitude),
                13f
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocationOrFallback() {
        val cancellation = CancellationTokenSource()
        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    moveToLocation(location)
                } else {
                    moveToLastKnownOrFallback(showToast = true)
                }
            }
            .addOnFailureListener {
                moveToLastKnownOrFallback(showToast = true)
            }
    }

    @SuppressLint("MissingPermission")
    private fun moveToLastKnownOrFallback(showToast: Boolean) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    moveToLocation(location)
                } else {
                    moveToFallbackLocation()
                    if (showToast) {
                        Toast.makeText(this, getString(R.string.location_unavailable), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                moveToFallbackLocation()
                if (showToast) {
                    Toast.makeText(this, getString(R.string.location_unavailable), Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun moveToFallbackLocation() {
        val fallback = regionalFallback()
        userLatLng = fallback.first
        NotificationProfile.syncLocation(fallback.first)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(fallback.first, fallback.second))
    }

    @SuppressLint("MissingPermission")
    private fun showAddProductAtCurrentLocation() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            Toast.makeText(this, getString(R.string.location_permission_needed), Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    if (::map.isInitialized) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    }
                    showAddProductDialog(latLng)
                } else {
                    openProductDialogFromLastKnownLocation()
                }
            }
            .addOnFailureListener {
                openProductDialogFromLastKnownLocation()
            }
    }

    @SuppressLint("MissingPermission")
    private fun openProductDialogFromLastKnownLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                val latLng = if (location != null) {
                    LatLng(location.latitude, location.longitude)
                } else if (::map.isInitialized) {
                    map.cameraPosition.target
                } else {
                    regionalFallback().first
                }

                if (::map.isInitialized) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
                showAddProductDialog(latLng)
            }
            .addOnFailureListener {
                val latLng = if (::map.isInitialized) map.cameraPosition.target else regionalFallback().first
                showAddProductDialog(latLng)
            }
    }

    private fun regionalFallback(): Pair<LatLng, Float> {
        val country = Locale.getDefault().country.uppercase(Locale.US)
        val zone = TimeZone.getDefault().id.lowercase(Locale.US)

        return when {
            country == "ES" || zone.contains("madrid") -> LatLng(39.4699, -0.3763) to 12f
            country == "CN" || zone.contains("shanghai") || zone.contains("chongqing") -> LatLng(31.2304, 121.4737) to 11f
            country == "US" || zone.contains("new_york") -> LatLng(40.7128, -74.0060) to 11f
            country == "MX" -> LatLng(19.4326, -99.1332) to 11f
            country == "BR" -> LatLng(-23.5505, -46.6333) to 11f
            country == "FR" -> LatLng(48.8566, 2.3522) to 11f
            country == "DE" -> LatLng(52.52, 13.4050) to 11f
            country == "IT" -> LatLng(41.9028, 12.4964) to 11f
            country == "PT" -> LatLng(38.7223, -9.1393) to 11f
            country == "GB" -> LatLng(51.5072, -0.1276) to 11f
            country == "IN" -> LatLng(28.6139, 77.2090) to 11f
            country == "JP" -> LatLng(35.6762, 139.6503) to 11f
            country == "KR" -> LatLng(37.5665, 126.9780) to 11f
            else -> LatLng(39.4699, -0.3763) to 7f
        }
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
                    if (hasLoadedInitialProducts && product.id !in knownProductIds) {
                        notifyNewNearbyProduct(product)
                    }
                    knownProductIds.add(product.id)
                }

                hasLoadedInitialProducts = true
                renderProductsOnMap()
            }
    }

    private fun renderProductsOnMap() {
        if (!::map.isInitialized) return

        map.clear()
        val grouped = mutableMapOf<String, MutableList<Product>>()
        val filteredProducts = filteredProductsForCurrentCategory()
        renderProductRail(filteredProducts)

        for (product in filteredProducts) {
            val lat = product.lat
            val lng = product.lng

            if (lat == null || lng == null) continue

            grouped.getOrPut("${lat}_${lng}") { mutableListOf() }.add(product)
        }

        for ((_, products) in grouped) {
            val first = products.firstOrNull() ?: continue
            val lat = first.lat
            val lng = first.lng

            if (lat == null || lng == null) continue

            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(lat, lng))
                    .title(getString(R.string.map_marker_products, products.size))
                    .icon(createMarkerIcon(products))
                    .anchor(0.5f, 1f)
            )

            marker?.tag = products
        }
    }

    private fun filteredProductsForCurrentCategory(): List<Product> {
        return filteredProducts(activeSearchQuery, selectedCategoryFilter, searchSortMode)
    }

    private fun showSearchPanel(openFilters: Boolean = false) {
        val dialog = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.bottom_sheet_background)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val title = TextView(this).apply {
            text = getString(R.string.search_products_title)
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.ink))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(title)

        val input = EditText(this).apply {
            hint = getString(R.string.search_input_hint)
            setText(activeSearchQuery)
            setSingleLine(true)
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.ink))
            setHintTextColor(ContextCompat.getColor(this@MapActivity, R.color.muted_ink))
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.auth_input_background)
            setPadding(dp(16), 0, dp(16), 0)
            setSelectAllOnFocus(true)
        }
        root.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply {
            topMargin = dp(14)
        })

        val categorySpinner = Spinner(this)
        val categoryLabels = ProductCategories.filters.map { filterDisplayName(it) }
        categorySpinner.adapter = readableSpinnerAdapter(categoryLabels)
        categorySpinner.setSelection(ProductCategories.filters.indexOf(selectedCategoryFilter).coerceAtLeast(0))
        root.addView(categorySpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = if (openFilters) dp(12) else dp(8)
        })

        val sortRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val sortScroll = HorizontalScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isHorizontalScrollBarEnabled = false
            addView(sortRow)
        }
        root.addView(sortScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply {
            topMargin = dp(10)
        })

        val saveSearchButton = Button(this).apply {
            text = getString(R.string.save_search_alert)
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.button_secondary)
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.brand_olive))
            textSize = 13f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(saveSearchButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply {
            topMargin = dp(8)
        })

        val resultTitle = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.ink))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(resultTitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply {
            addView(results)
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360)).apply {
            topMargin = dp(10)
        })

        var localSort = if (openFilters && searchSortMode == SearchSort.RELEVANCE) SearchSort.DISTANCE else searchSortMode
        var localCategory = selectedCategoryFilter

        fun renderSortChips() {
            sortRow.removeAllViews()
            listOf(
                SearchSort.RELEVANCE to getString(R.string.sort_relevance),
                SearchSort.DISTANCE to getString(R.string.sort_distance),
                SearchSort.PRICE_LOW to getString(R.string.sort_price_low),
                SearchSort.PRICE_HIGH to getString(R.string.sort_price_high),
                SearchSort.STOCK to getString(R.string.sort_stock)
            ).forEach { (mode, label) ->
                val chip = createSearchChip(label, mode == localSort).apply {
                    setOnClickListener {
                        localSort = mode
                        searchSortMode = mode
                        renderSortChips()
                        renderSearchResults(input.text?.toString().orEmpty(), localCategory, localSort, resultTitle, results, dialog)
                    }
                }
                sortRow.addView(chip)
            }
        }

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                localCategory = ProductCategories.filters.getOrElse(position) { ProductCategories.FILTER_ALL }
                selectedCategoryFilter = localCategory
                updateFilterButtonText()
                renderSearchResults(input.text?.toString().orEmpty(), localCategory, localSort, resultTitle, results, dialog)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                activeSearchQuery = s?.toString().orEmpty()
                findViewById<TextView?>(R.id.tvSearchText)?.text =
                    activeSearchQuery.takeIf { it.isNotBlank() } ?: getString(R.string.search_placeholder)
                renderSearchResults(activeSearchQuery, localCategory, localSort, resultTitle, results, dialog)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        saveSearchButton.setOnClickListener {
            val query = input.text?.toString().orEmpty().trim()
            val category = ProductCategories.filters.getOrElse(categorySpinner.selectedItemPosition) {
                ProductCategories.FILTER_ALL
            }
            EngagementTracker.saveSearch(query, category, localSort.name)
            Toast.makeText(this, getString(R.string.search_alert_saved), Toast.LENGTH_SHORT).show()
        }

        renderSortChips()
        renderSearchResults(activeSearchQuery, localCategory, localSort, resultTitle, results, dialog)
        dialog.setContentView(root)
        dialog.setOnShowListener {
            input.requestFocus()
            getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
    }

    private fun createSearchChip(label: String, selected: Boolean): TextView {
        return TextView(this).apply {
            text = label
            gravity = android.view.Gravity.CENTER
            background = ContextCompat.getDrawable(this@MapActivity, if (selected) R.drawable.category_pill_active else R.drawable.category_pill)
            setTextColor(ContextCompat.getColor(this@MapActivity, if (selected) android.R.color.white else R.color.brand_olive))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                marginEnd = dp(8)
            }
        }
    }

    private fun renderSearchResults(
        query: String,
        category: String,
        sort: SearchSort,
        title: TextView,
        container: LinearLayout,
        dialog: BottomSheetDialog
    ) {
        val products = filteredProducts(query, category, sort)
        container.removeAllViews()
        title.text = resources.getQuantityString(R.plurals.search_results_count, products.size, products.size)
        renderProductRail(products)
        if (::map.isInitialized) renderProductsOnMap()

        if (products.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.search_no_results)
                setTextColor(ContextCompat.getColor(this@MapActivity, R.color.muted_ink))
                textSize = 14f
                setPadding(0, dp(28), 0, dp(28))
                gravity = android.view.Gravity.CENTER
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            return
        }

        products.take(25).forEach { product ->
            container.addView(createSearchResultRow(product, dialog))
        }
    }

    private fun createSearchResultRow(product: Product, dialog: BottomSheetDialog): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.auth_card_background)
            elevation = dp(4).toFloat()
            setPadding(dp(10), dp(10), dp(12), dp(10))
            isClickable = true
            isFocusable = true
        }

        val image = ImageView(this).apply {
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.product_image_round)
            contentDescription = getString(R.string.product_image)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        row.addView(image, LinearLayout.LayoutParams(dp(64), dp(64)))

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        row.addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
        })

        textColumn.addView(TextView(this).apply {
            text = product.name
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.ink))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        val distance = distanceKmToProduct(product)?.let { "%.1f km".format(Locale.getDefault(), it) } ?: getString(R.string.distance_near)
        textColumn.addView(TextView(this).apply {
            text = "${filterDisplayName(product.category)} · $distance · ${MarketFormat.formatQuantity(this@MapActivity, product.getAvailableStock(), product.normalizedUnit())}"
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.muted_ink))
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        row.addView(TextView(this).apply {
            text = MarketFormat.formatMoney(this@MapActivity, product.getPriceAsDouble(), product.normalizedCurrency())
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.green_primary))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.END
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(8)
        })

        Glide.with(this)
            .load(product.imageUrl.ifEmpty { "https://via.placeholder.com/300" })
            .into(image)

        UiMotion.makePressable(row)
        row.setOnClickListener {
            dialog.dismiss()
            EngagementTracker.productEvent("view_search", product)
            showProductsDialog(listOf(product))
        }

        return row.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun filteredProducts(query: String, category: String, sort: SearchSort): List<Product> {
        val normalizedQuery = query.trim().lowercase(Locale.getDefault())
        val words = normalizedQuery.split(" ").filter { it.isNotBlank() }

        return allProducts.filter {
            category == ProductCategories.FILTER_ALL ||
                ProductCategories.normalize(it.category) == category
        }.filter { product ->
            if (words.isEmpty()) {
                true
            } else {
                val searchable = listOf(
                    product.name,
                    product.description,
                    product.category,
                    product.normalizedUnit()
                ).joinToString(" ").lowercase(Locale.getDefault())
                words.all { word -> searchable.contains(word) }
            }
        }.let { products ->
            when (sort) {
                SearchSort.DISTANCE -> products.sortedBy { distanceKmToProduct(it) ?: Double.MAX_VALUE }
                SearchSort.PRICE_LOW -> products.sortedBy { it.getPriceAsDouble() }
                SearchSort.PRICE_HIGH -> products.sortedByDescending { it.getPriceAsDouble() }
                SearchSort.STOCK -> products.sortedByDescending { it.getAvailableStock() }
                SearchSort.RELEVANCE -> products.sortedByDescending { product -> feedScore(product) }
            }
        }
    }

    private fun feedScore(product: Product): Double {
        val scarcityBoost = if (product.getAvailableStock() in 0.1..2.0) 60.0 else 0.0
        val trustBoost = product.getFavoriteCountAsLong() * 4.0 + product.getReservationCountAsLong() * 8.0
        val followBoost = when {
            product.sellerId.isNotBlank() && product.sellerId in followedSellerCache -> 80.0
            ProductCategories.normalize(product.category) in followedCategoryCache -> 45.0
            else -> 0.0
        }
        val freshnessBoost = product.createdAt?.let {
            val hours = (System.currentTimeMillis() - it.toDate().time) / 3_600_000.0
            (48.0 - hours).coerceAtLeast(0.0)
        } ?: 0.0
        return scarcityBoost + trustBoost + freshnessBoost + followBoost
    }

    private fun distanceKmToProduct(product: Product): Double? {
        val origin = userLatLng ?: if (::map.isInitialized) map.cameraPosition.target else null
        val lat = product.lat
        val lng = product.lng
        if (origin == null || lat == null || lng == null) return null

        val result = FloatArray(1)
        Location.distanceBetween(origin.latitude, origin.longitude, lat, lng, result)
        return result[0] / 1000.0
    }

    private fun renderProductRail(products: List<Product>) {
        val panel = findViewById<View?>(R.id.productFeedPanel) ?: return
        val rail = findViewById<LinearLayout?>(R.id.productRail) ?: return
        rail.removeAllViews()

        if (isMapMode) {
            panel.visibility = View.GONE
            return
        }

        val title = findViewById<TextView?>(R.id.tvFeedTitle)
        val badge = findViewById<TextView?>(R.id.tvFeedBadge)
        val todayDrops = products.filter { isFreshToday(it) }.sortedByDescending { feedScore(it) }
        val isDiscoveryHome = activeSearchQuery.isBlank() && selectedCategoryFilter == ProductCategories.FILTER_ALL
        val displayProducts = if (isDiscoveryHome && todayDrops.isNotEmpty()) {
            title?.text = getString(R.string.today_near_you)
            badge?.text = getString(R.string.daily_drop_badge)
            todayDrops.take(12)
        } else {
            title?.text = if (activeSearchQuery.isBlank()) getString(R.string.fresh_nearby) else getString(R.string.search_products_title)
            badge?.text = getString(R.string.badge_km0)
            products.take(12)
        }
        if (displayProducts.isEmpty()) {
            panel.visibility = View.VISIBLE
            rail.addView(createEmptyHeroCard())
            return
        }

        panel.visibility = View.VISIBLE
        displayProducts.forEach { product ->
            rail.addView(createFeedCard(product))
        }
    }

    private fun createFeedCard(product: Product): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.auth_card_background)
            elevation = dp(8).toFloat()
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(12), dp(10))
            isClickable = true
            isFocusable = true
        }

        val image = ImageView(this).apply {
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.product_image_round)
            contentDescription = getString(R.string.product_image)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        card.addView(image, LinearLayout.LayoutParams(dp(106), dp(106)))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        card.addView(content, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = dp(10)
        })

        val title = TextView(this).apply {
            text = product.name
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.ink))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        content.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val meta = TextView(this).apply {
            text = buildFeedMeta(product)
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.muted_ink))
            textSize = 11f
            maxLines = 1
        }
        content.addView(meta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2)
        })

        val badges = discoveryBadges(product)
        if (badges.isNotEmpty()) {
            val badgeRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            badges.take(3).forEach { badge ->
                badgeRow.addView(discoveryBadgeView(badge.first, badge.second), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(24)
                ).apply {
                    marginEnd = dp(6)
                })
            }
            content.addView(badgeRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(7)
            })
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val price = TextView(this).apply {
            text = MarketFormat.formatMoney(this@MapActivity, product.getPriceAsDouble(), product.normalizedCurrency())
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.green_primary))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        val add = TextView(this).apply {
            text = getString(R.string.reserve_one_tap)
            gravity = android.view.Gravity.CENTER
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.mini_add_background)
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        bottom.addView(price, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(add, LinearLayout.LayoutParams(dp(78), dp(30)))
        content.addView(bottom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        Glide.with(this)
            .load(product.imageUrl.ifEmpty { "https://via.placeholder.com/300" })
            .into(image)

        UiMotion.makePressable(card, add)
        card.setOnClickListener {
            EngagementTracker.productEvent("view", product)
            showProductsDialog(listOf(product))
        }
        add.setOnClickListener {
            quickReserve(product, add)
        }

        return card.apply {
            layoutParams = LinearLayout.LayoutParams(dp(336), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginEnd = dp(14)
            }
        }
    }

    private fun createEmptyHeroCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.auth_card_background)
            elevation = dp(8).toFloat()
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(12), dp(10))
            isClickable = true
            isFocusable = true
        }

        val image = ImageView(this).apply {
            setImageResource(R.drawable.hero_market_global)
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.product_image_round)
            contentDescription = getString(R.string.product_image)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        card.addView(image, LinearLayout.LayoutParams(dp(108), dp(108)))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        card.addView(content, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = dp(12)
        })

        val title = TextView(this).apply {
            text = getString(R.string.fresh_nearby)
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.ink))
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        content.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val body = TextView(this).apply {
            text = getString(R.string.publish_first_product)
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.muted_ink))
            textSize = 13f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        content.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(5)
        })

        UiMotion.makePressable(card)
        card.setOnClickListener {
            if (::map.isInitialized) {
                showAddProductDialog(map.cameraPosition.target)
            }
        }

        return card.apply {
            layoutParams = LinearLayout.LayoutParams(dp(336), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginEnd = dp(14)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun isFreshToday(product: Product): Boolean {
        if (product.publishedDateKey == GeoEngagement.todayKey()) return true
        return product.createdAt?.let {
            System.currentTimeMillis() - it.toDate().time <= 24L * 60L * 60L * 1000L
        } ?: false
    }

    private fun buildFeedMeta(product: Product): String {
        val parts = mutableListOf<String>()
        val distance = distanceKmToProduct(product)
        if (distance == null) {
            parts.add(getString(R.string.distance_near))
        } else if (distance < 1.0) {
            parts.add(getString(R.string.distance_meters, (distance * 1000).toInt().coerceAtLeast(50)))
        } else {
            parts.add(getString(R.string.distance_km, distance))
        }
        if (isFreshToday(product)) parts.add(getString(R.string.new_today))
        if (product.getAvailableStock() in 0.1..2.0) {
            parts.add(getString(R.string.only_left, MarketFormat.formatQuantity(this, product.getAvailableStock(), product.normalizedUnit())))
        }
        return parts.joinToString("  -  ")
    }

    private fun discoveryBadges(product: Product): List<Pair<String, Boolean>> {
        val badges = mutableListOf<Pair<String, Boolean>>()
        val distance = distanceKmToProduct(product)
        val isLowStock = product.getAvailableStock() in 0.1..2.0

        if (isFreshToday(product)) badges.add(getString(R.string.new_today) to false)
        if (distance != null && distance <= 2.0) badges.add(getString(R.string.badge_nearby) to false)
        if (isLowStock) badges.add(getString(R.string.badge_low_stock) to true)
        if (product.getReservationCountAsLong() >= 3 || product.getFavoriteCountAsLong() >= 3) {
            badges.add(getString(R.string.badge_popular) to false)
        }

        return badges
    }

    private fun discoveryBadgeView(label: String, isUrgent: Boolean): TextView {
        return TextView(this).apply {
            text = label
            background = ContextCompat.getDrawable(
                this@MapActivity,
                if (isUrgent) R.drawable.scarcity_chip_background else R.drawable.category_chip_background
            )
            setTextColor(ContextCompat.getColor(this@MapActivity, if (isUrgent) R.color.brand_red else R.color.brand_olive))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(9), 0, dp(9), 0)
        }
    }

    private fun quickReserve(product: Product, source: View) {
        val buyerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val available = product.getAvailableStock()
        if (available <= 0.0) {
            Toast.makeText(this, getString(R.string.reservation_error_stock), Toast.LENGTH_SHORT).show()
            return
        }

        val quantity = if (available < 0.5) available else available.coerceAtMost(1.0)
        EngagementTracker.productEvent("quick_reserve", product)
        ReservationService.reserveProduct(db, product, buyerId, quantity)
            .addOnSuccessListener {
                UiMotion.celebrate(source)
                product.stockReserved = product.getStockReservedAsDouble() + quantity
                product.reservationCount = product.getReservationCountAsLong() + 1
                EngagementTracker.addImpactAfterReservation(product, quantity)
                db.collection("products").document(product.id)
                    .update("reservationCount", FieldValue.increment(1))
                loadImpactSummary()
                renderProductRail(filteredProductsForCurrentCategory())
                Toast.makeText(
                    this,
                    getString(R.string.quick_reserved, MarketFormat.formatQuantity(this, quantity, product.normalizedUnit())),
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->
                val message = if (error.message == "not_enough_stock") {
                    getString(R.string.reservation_error_stock)
                } else {
                    getString(R.string.reservation_error_generic)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun shareHuertoCero() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.buy_local))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.quick_invite)))
    }

    private fun openNavigationToProduct(product: Product) {
        val lat = product.lat
        val lng = product.lng

        if (lat == null || lng == null) {
            Toast.makeText(this, getString(R.string.location_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=w")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            "nearby_products",
            "HuertoCero",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notify_new_product_title)
        }
        manager.createNotificationChannel(channel)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun notifyNewNearbyProduct(product: Product) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (product.sellerId.isNotBlank() && product.sellerId == currentUserId) return
        val distance = distanceKmToProduct(product)
        if (distance != null && distance > 8.0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(this, "nearby_products")
            .setSmallIcon(R.drawable.ic_launcher_huertocero)
            .setContentTitle(getString(R.string.notify_new_product_title))
            .setContentText(getString(R.string.notify_new_product_body, product.name))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(product.id.hashCode(), notification)
    }

    private fun loadImpactSummary() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val reservations = doc.getLong("impactReservations") ?: 0L
                val km = doc.getLong("impactKmSavedEstimate") ?: 0L
                findViewById<TextView?>(R.id.tvImpact)?.text = if (reservations > 0) {
                    getString(R.string.impact_summary, reservations.toInt(), km.toInt())
                } else {
                    getString(R.string.impact_empty)
                }
            }
    }

    private fun loadFollowSignals() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(userId)
            .collection("followedCategories")
            .addSnapshotListener { snapshot, _ ->
                followedCategoryCache.clear()
                snapshot?.documents
                    ?.mapNotNull { it.getString("category") }
                    ?.mapTo(followedCategoryCache) { ProductCategories.normalize(it) }
                if (!isMapMode) renderProductRail(filteredProductsForCurrentCategory())
            }

        db.collection("users").document(userId)
            .collection("followedSellers")
            .addSnapshotListener { snapshot, _ ->
                followedSellerCache.clear()
                snapshot?.documents
                    ?.mapNotNull { it.getString("sellerId") }
                    ?.filterTo(followedSellerCache) { it.isNotBlank() }
                if (!isMapMode) renderProductRail(filteredProductsForCurrentCategory())
            }
    }

    private fun showImpactPanel() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val reservations = doc.getLong("impactReservations") ?: 0L
                val km = doc.getLong("impactKmSavedEstimate") ?: 0L
                val kg = doc.getDouble("impactLocalKg") ?: (doc.getLong("impactLocalKg")?.toDouble() ?: 0.0)
                val streak = doc.getLong("localStreakDays") ?: 1L

                val root = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ContextCompat.getDrawable(this@MapActivity, R.drawable.bottom_sheet_background)
                    setPadding(dp(20), dp(20), dp(20), dp(20))
                }
                root.addView(TextView(this).apply {
                    text = getString(R.string.impact_panel_title)
                    setTextColor(ContextCompat.getColor(this@MapActivity, R.color.ink))
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                })
                root.addView(TextView(this).apply {
                    text = getString(R.string.impact_panel_body)
                    setTextColor(ContextCompat.getColor(this@MapActivity, R.color.muted_ink))
                    textSize = 14f
                    setPadding(0, dp(6), 0, dp(10))
                })

                listOf(
                    getString(R.string.impact_stat_reservations, reservations),
                    getString(R.string.impact_stat_kg, kg),
                    getString(R.string.impact_stat_km, km),
                    getString(R.string.impact_stat_streak, streak)
                ).forEach { label ->
                    root.addView(TextView(this).apply {
                        text = label
                        background = ContextCompat.getDrawable(this@MapActivity, R.drawable.action_tile_background)
                        setTextColor(ContextCompat.getColor(this@MapActivity, R.color.brand_olive))
                        textSize = 16f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(dp(16), 0, dp(16), 0)
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
                        topMargin = dp(10)
                    })
                }

                BottomSheetDialog(this).apply {
                    setContentView(root)
                    show()
                }
            }
    }

    private fun showAccountPanel() {
        val user = FirebaseAuth.getInstance().currentUser

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.bottom_sheet_background)
            setPadding(dp(22), dp(22), dp(22), dp(24))
        }

        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo_huerto_cero_mark)
            contentDescription = getString(R.string.app_name)
        }, LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            bottomMargin = dp(10)
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.account_panel_title)
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.ink))
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(TextView(this).apply {
            text = user?.email ?: getString(R.string.account_panel_subtitle)
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.muted_ink))
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
        })

        val stats = TextView(this).apply {
            text = getString(R.string.account_stats, 0, 0, 0)
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.category_chip_background)
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.brand_olive))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
        }
        root.addView(stats, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)).apply {
            bottomMargin = dp(8)
        })
        loadAccountStats(stats)

        val dialog = BottomSheetDialog(this)

        root.addView(accountPanelButton(getString(R.string.my_products), R.drawable.button_secondary) {
            dialog.dismiss()
            startActivity(Intent(this, MyProductsActivity::class.java))
        })

        root.addView(accountPanelButton(getString(R.string.privacy_support), R.drawable.button_secondary) {
            dialog.dismiss()
            startActivity(Intent(this, PrivacyActivity::class.java))
        })

        root.addView(accountPanelButton(getString(R.string.logout), R.drawable.button_primary) {
            dialog.dismiss()
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        })

        root.addView(accountPanelButton(getString(R.string.delete_account), R.drawable.button_danger) {
            dialog.dismiss()
            showDeleteAccountDialog()
        })

        dialog.setContentView(root)
        dialog.show()
    }

    private fun loadAccountStats(target: TextView) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        var products = 0
        var reservations = 0
        var favorites = 0
        var pending = 3

        fun updateIfReady() {
            pending -= 1
            if (pending <= 0) {
                target.text = getString(R.string.account_stats, products, reservations, favorites)
            }
        }

        db.collection("products")
            .whereEqualTo("sellerId", userId)
            .get()
            .addOnCompleteListener {
                products = it.result?.size() ?: 0
                updateIfReady()
            }

        db.collection("reservas")
            .whereEqualTo("buyerId", userId)
            .get()
            .addOnCompleteListener {
                reservations = it.result?.size() ?: 0
                updateIfReady()
            }

        db.collection("users")
            .document(userId)
            .collection("favorites")
            .get()
            .addOnCompleteListener {
                favorites = it.result?.size() ?: 0
                updateIfReady()
            }
    }

    private fun accountPanelButton(textValue: String, backgroundRes: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = textValue
            background = ContextCompat.getDrawable(this@MapActivity, backgroundRes)
            setTextColor(if (backgroundRes == R.drawable.button_secondary) {
                ContextCompat.getColor(this@MapActivity, R.color.brand_olive)
            } else {
                Color.WHITE
            })
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            UiMotion.makePressable(this)
        }.also { button ->
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun showDeleteAccountDialog() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val passwordInput = EditText(this).apply {
            hint = getString(R.string.password_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setPadding(dp(16), 0, dp(16), 0)
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.auth_input_background)
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.ink))
            setHintTextColor(ContextCompat.getColor(this@MapActivity, R.color.muted_ink))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(8), dp(2), 0)
            addView(TextView(this@MapActivity).apply {
                text = getString(R.string.delete_account_message)
                setTextColor(ContextCompat.getColor(this@MapActivity, R.color.muted_ink))
                textSize = 14f
            })
            addView(passwordInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                topMargin = dp(14)
            })
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_account_title))
            .setView(content)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete_account_confirm), null)
            .create()

        dialog.setOnShowListener {
            val deleteButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            deleteButton.setTextColor(ContextCompat.getColor(this, R.color.brand_red))
            deleteButton.setOnClickListener {
                val email = user.email
                val password = passwordInput.text.toString()

                if (!email.isNullOrBlank() && password.length < 6) {
                    passwordInput.error = getString(R.string.password_min)
                    return@setOnClickListener
                }

                deleteButton.isEnabled = false
                deleteButton.text = getString(R.string.deleting_account)

                val reauthTask = if (!email.isNullOrBlank()) {
                    user.reauthenticate(EmailAuthProvider.getCredential(email, password))
                } else {
                    com.google.android.gms.tasks.Tasks.forResult(null)
                }

                reauthTask
                    .continueWithTask { task ->
                        if (!task.isSuccessful) throw task.exception ?: IllegalStateException("reauth_failed")
                        AccountDeletionService.deleteCurrentAccount()
                    }
                    .addOnSuccessListener {
                        Toast.makeText(this, getString(R.string.account_deleted), Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { error ->
                        deleteButton.isEnabled = true
                        deleteButton.text = getString(R.string.delete_account_confirm)
                        Toast.makeText(
                            this,
                            getString(R.string.account_delete_error, error.localizedMessage ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }

        dialog.show()
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
            if (selectedCategoryFilter == ProductCategories.FILTER_ALL) {
                getString(R.string.filter_short)
            } else {
                filterDisplayName(selectedCategoryFilter)
            }
    }

    private fun createMarkerIcon(products: List<Product>) =
        BitmapDescriptorFactory.fromBitmap(createMarkerBitmap(products.firstOrNull()?.category.orEmpty(), products.size))

    private fun createMarkerBitmap(category: String, count: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(112, 132, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2F5233")
            style = Paint.Style.FILL
        }
        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 23f
            isFakeBoldText = true
        }

        val path = Path().apply {
            moveTo(56f, 128f)
            cubicTo(27f, 92f, 10f, 72f, 10f, 45f)
            cubicTo(10f, 19f, 30f, 4f, 56f, 4f)
            cubicTo(82f, 4f, 102f, 19f, 102f, 45f)
            cubicTo(102f, 72f, 85f, 92f, 56f, 128f)
            close()
        }

        canvas.drawPath(path, pinPaint)
        canvas.drawCircle(56f, 45f, 31f, platePaint)
        drawCategoryGlyph(canvas, ProductCategories.normalize(category), 56f, 45f)

        if (count > 1) {
            canvas.drawCircle(86f, 25f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E4572E")
                style = Paint.Style.FILL
            })
            canvas.drawText(count.toString(), 86f, 33f, textPaint)
        }

        return bitmap
    }

    private fun drawCategoryGlyph(canvas: Canvas, category: String, cx: Float, cy: Float) {
        val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4E9F3D"); style = Paint.Style.FILL }
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2F5233"); style = Paint.Style.FILL }
        val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E4572E"); style = Paint.Style.FILL }
        val orange = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F4B942"); style = Paint.Style.FILL }
        val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#A7D129"); style = Paint.Style.FILL }
        val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2D9CDB"); style = Paint.Style.FILL }
        val brown = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B8752B"); style = Paint.Style.FILL }
        val cream = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF1C7"); style = Paint.Style.FILL }

        when (category) {
            "Frutas" -> {
                canvas.drawCircle(cx - 7f, cy + 2f, 11f, red)
                canvas.drawCircle(cx + 8f, cy + 4f, 10f, orange)
                canvas.drawOval(RectF(cx - 1f, cy - 19f, cx + 20f, cy - 8f), green)
            }
            "Verduras" -> {
                canvas.drawOval(RectF(cx - 18f, cy - 3f, cx + 18f, cy + 17f), green)
                canvas.drawOval(RectF(cx - 12f, cy - 18f, cx + 3f, cy + 4f), green)
                canvas.drawOval(RectF(cx + 2f, cy - 18f, cx + 17f, cy + 4f), green)
            }
            "Carniceria" -> {
                canvas.drawRoundRect(RectF(cx - 19f, cy - 12f, cx + 19f, cy + 14f), 12f, 12f, red)
                canvas.drawCircle(cx + 8f, cy - 3f, 6f, cream)
            }
            "Pescaderia" -> {
                canvas.drawOval(RectF(cx - 22f, cy - 12f, cx + 14f, cy + 12f), blue)
                val tail = Path().apply {
                    moveTo(cx + 13f, cy)
                    lineTo(cx + 25f, cy - 12f)
                    lineTo(cx + 25f, cy + 12f)
                    close()
                }
                canvas.drawPath(tail, blue)
                canvas.drawCircle(cx - 10f, cy - 3f, 3f, dark)
            }
            "Panaderia" -> {
                canvas.drawOval(RectF(cx - 22f, cy - 13f, cx + 22f, cy + 15f), brown)
                canvas.drawOval(RectF(cx - 12f, cy - 8f, cx - 3f, cy + 8f), cream)
                canvas.drawOval(RectF(cx + 3f, cy - 8f, cx + 12f, cy + 8f), cream)
            }
            "Lacteos" -> {
                canvas.drawRoundRect(RectF(cx - 14f, cy - 20f, cx + 14f, cy + 18f), 6f, 6f, blue)
                canvas.drawRect(cx - 9f, cy - 27f, cx + 9f, cy - 18f, blue)
                canvas.drawRoundRect(RectF(cx - 9f, cy - 1f, cx + 9f, cy + 12f), 4f, 4f, cream)
            }
            "Despensa", "Comida preparada" -> {
                canvas.drawRoundRect(RectF(cx - 20f, cy - 16f, cx + 20f, cy + 17f), 8f, 8f, yellow)
                canvas.drawCircle(cx - 7f, cy + 1f, 6f, orange)
                canvas.drawCircle(cx + 8f, cy + 1f, 6f, red)
            }
            "Huevos" -> {
                canvas.drawOval(RectF(cx - 17f, cy - 18f, cx - 1f, cy + 17f), cream)
                canvas.drawOval(RectF(cx + 1f, cy - 18f, cx + 17f, cy + 17f), cream)
            }
            "Miel" -> {
                canvas.drawRoundRect(RectF(cx - 16f, cy - 17f, cx + 16f, cy + 18f), 8f, 8f, yellow)
                canvas.drawRect(cx - 13f, cy - 22f, cx + 13f, cy - 14f, brown)
            }
            else -> {
                canvas.drawOval(RectF(cx - 8f, cy - 22f, cx + 20f, cy - 4f), green)
                canvas.drawOval(RectF(cx - 18f, cy - 5f, cx + 16f, cy + 18f), dark)
            }
        }
    }

    private fun showProductsDialog(productList: List<Product>) {
        val products = productList.toMutableList()
        var currentIndex = 0

        val view = layoutInflater.inflate(R.layout.dialog_product, null)

        val img = view.findViewById<ImageView>(R.id.imgProduct)
        val name = view.findViewById<TextView>(R.id.tvName)
        val desc = view.findViewById<TextView>(R.id.tvDescription)
        val price = view.findViewById<TextView>(R.id.tvPrice)
        val stock = view.findViewById<TextView>(R.id.tvStock)
        val swipeHint = view.findViewById<TextView>(R.id.tvSwipeHint)
        val category = view.findViewById<TextView>(R.id.tvCategory)
        val sellerRating = view.findViewById<TextView>(R.id.tvSellerRating)

        val btnReserve = view.findViewById<Button>(R.id.btnReserve)
        val btnNavigate = view.findViewById<Button>(R.id.btnNavigate)
        val btnFavorite = view.findViewById<Button>(R.id.btnFavorite)
        val btnChat = view.findViewById<Button>(R.id.btnChat)
        val btnReport = view.findViewById<Button>(R.id.btnReport)
        val btnShare = view.findViewById<Button>(R.id.btnShare)
        val btnSeller = view.findViewById<Button>(R.id.btnSeller)
        val btnReview = view.findViewById<Button>(R.id.btnReview)
        val btnFollow = view.findViewById<Button>(R.id.btnFollow)

        val btnPrev = view.findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = view.findViewById<ImageButton>(R.id.btnNext)
        val tvCounter = view.findViewById<TextView>(R.id.tvCounter)
        UiMotion.makePressable(
            btnReserve,
            btnNavigate,
            btnFavorite,
            btnChat,
            btnReport,
            btnShare,
            btnSeller,
            btnReview,
            btnFollow,
            btnPrev,
            btnNext
        )

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(view)

        fun showProduct(direction: Int = 0) {
            val product = products[currentIndex]

            name.text = product.name
            desc.text = product.description
            price.text = MarketFormat.formatMoney(this, product.getPriceAsDouble(), product.normalizedCurrency())
            val availableQuantity = MarketFormat.formatQuantity(this, product.getAvailableStock(), product.normalizedUnit())
            val isScarce = product.getAvailableStock() <= 2.0
            stock.text = if (isScarce) {
                getString(R.string.only_left, availableQuantity)
            } else {
                getString(R.string.available_stock, availableQuantity)
            }
            stock.setBackgroundResource(if (isScarce) R.drawable.scarcity_chip_background else R.drawable.category_chip_background)
            stock.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isScarce) R.color.scarcity else R.color.brand_olive
                )
            )
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
                openNavigationToProduct(product)
            }

            btnFavorite.setOnClickListener {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener

                val data = hashMapOf(
                    "productId" to product.id,
                    "name" to product.name,
                    "price" to product.getPriceAsDouble(),
                    "currency" to product.normalizedCurrency(),
                    "category" to ProductCategories.normalize(product.category),
                    "sellerId" to product.sellerId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                db.collection("users")
                    .document(userId)
                    .collection("favorites")
                    .document(product.id)
                    .set(data)

                db.collection("products").document(product.id)
                    .update("favoriteCount", FieldValue.increment(1))
                EngagementTracker.followSignals(product)
                EngagementTracker.productEvent("favorite", product)
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

            btnFollow.setOnClickListener {
                EngagementTracker.followSignals(product)
                EngagementTracker.productEvent("follow", product)
                UiMotion.celebrate(btnFollow)
                Toast.makeText(this, getString(R.string.follow_saved), Toast.LENGTH_SHORT).show()
            }

            btnReview.setOnClickListener {
                showReviewDialog(product)
            }

            loadSellerRating(product.sellerId, sellerRating)

            btnReport.setOnClickListener {
                reportProduct(product)
            }

            btnShare.setOnClickListener {
                EngagementTracker.productEvent("share", product)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.share_product_text, product.name))
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_product)))
            }

            btnReserve.setOnClickListener {
                val buyerId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
                showReserveQuantityDialog(product, buyerId) { reservedQuantity ->
                    UiMotion.celebrate(btnReserve)
                    Toast.makeText(
                        this,
                        "${getString(R.string.product_reserved)}. ${getString(R.string.reservation_hold_notice)}",
                        Toast.LENGTH_LONG
                    ).show()

                    product.stockReserved = product.getStockReservedAsDouble() + reservedQuantity
                    product.reservationCount = product.getReservationCountAsLong() + 1
                    EngagementTracker.addImpactAfterReservation(product, reservedQuantity)
                    db.collection("products").document(product.id)
                        .update("reservationCount", FieldValue.increment(1))
                    loadImpactSummary()
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
        val stockTotal = view.findViewById<EditText>(R.id.etStockTotal)
        val preview = view.findViewById<ImageView>(R.id.imgPreview)
        val btnImage = view.findViewById<Button>(R.id.btnImage)
        val categorySpinner = view.findViewById<Spinner>(R.id.spinnerCategory)
        val unitSpinner = view.findViewById<Spinner>(R.id.spinnerUnit)
        val currencySpinner = view.findViewById<Spinner>(R.id.spinnerCurrency)

        addProductImagePreview = preview
        addProductImageButton = btnImage
        configureCategorySpinner(categorySpinner)
        configureSimpleSpinner(unitSpinner, MarketFormat.units)
        configureSimpleSpinner(currencySpinner, MarketFormat.currencies)

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
                val stock = stockTotal.text.toString().toDoubleOrNull() ?: 1.0
                val categoria = ProductCategories.all.getOrElse(categorySpinner.selectedItemPosition) {
                    ProductCategories.OTHER
                }
                val unit = MarketFormat.units.getOrElse(unitSpinner.selectedItemPosition) { "kg" }
                val currency = MarketFormat.currencies.getOrElse(currencySpinner.selectedItemPosition) { "EUR" }

                if (nombre.isEmpty() || stock <= 0.0) {
                    Toast.makeText(this, getString(R.string.fill_name), Toast.LENGTH_SHORT).show()
                    return
                }

                setDialogButtonsEnabled(btnGuardar, btnOtro, false)

                saveProductWithImage(
                    nombre = nombre,
                    descripcion = descripcion,
                    precio = precio,
                    categoria = categoria,
                    stockTotal = stock,
                    unit = unit,
                    currency = currency,
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
                        stockTotal.setText("")
                        selectedImageUri = null
                        preview.setImageResource(R.drawable.hero_market_global)
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
        stockTotal: Double,
        unit: String,
        currency: String,
        lat: Double,
        lng: Double,
        onComplete: (Boolean) -> Unit
    ) {
        val imageUri = selectedImageUri
        val sellerId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

        fun saveProduct(imageUrl: String) {
            val product = Product(
                name = nombre,
                description = descripcion,
                price = precio,
                lat = lat,
                lng = lng,
                imageUrl = imageUrl,
                sellerId = sellerId,
                category = ProductCategories.normalize(categoria),
                stockTotal = stockTotal,
                stockReserved = 0.0,
                unit = MarketFormat.normalizeUnit(unit),
                currency = MarketFormat.normalizeCurrency(currency),
                geoCell = GeoEngagement.geoCell(lat, lng),
                publishedDateKey = GeoEngagement.todayKey()
            )

            db.collection("products").add(product)
                .addOnSuccessListener { ref ->
                    ref.update(
                        mapOf(
                            "createdAt" to FieldValue.serverTimestamp(),
                            "viewCount" to 0,
                            "favoriteCount" to 0,
                            "reservationCount" to 0
                        )
                    )
                    onComplete(true)
                }
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
            .child("product_images/$sellerId/${System.currentTimeMillis()}_${imageUri.lastPathSegment ?: "product"}.jpg")

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
        spinner.adapter = readableSpinnerAdapter(categoryLabels)
        val selectedIndex = ProductCategories.all.indexOf(ProductCategories.normalize(selectedCategory))
        spinner.setSelection(selectedIndex.coerceAtLeast(0))
    }

    private fun configureSimpleSpinner(spinner: Spinner, values: List<String>, selectedValue: String = values.first()) {
        spinner.adapter = readableSpinnerAdapter(values)
        spinner.setSelection(values.indexOf(selectedValue).coerceAtLeast(0))
    }

    private fun readableSpinnerAdapter(values: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.item_spinner_readable, values).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown_readable)
        }
    }

    private fun showReserveQuantityDialog(product: Product, buyerId: String, onReserved: (Double) -> Unit) {
        var selectedQuantity = 1.0.coerceAtMost(product.getAvailableStock()).coerceAtLeast(0.5)
        val quantityText = TextView(this).apply {
            text = MarketFormat.formatQuantity(this@MapActivity, selectedQuantity, product.normalizedUnit())
            gravity = android.view.Gravity.CENTER
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.ink))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val minus = Button(this).apply {
            text = "-"
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.button_secondary)
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.brand_olive))
        }
        val plus = Button(this).apply {
            text = "+"
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.button_secondary)
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.brand_olive))
        }
        val stepper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            background = ContextCompat.getDrawable(this@MapActivity, R.drawable.quantity_stepper_background)
            setPadding(10, 8, 10, 8)
            addView(minus, LinearLayout.LayoutParams(96, 72))
            addView(quantityText, LinearLayout.LayoutParams(0, 72, 1f))
            addView(plus, LinearLayout.LayoutParams(96, 72))
        }
        fun updateQuantity(delta: Double) {
            selectedQuantity = (selectedQuantity + delta)
                .coerceAtLeast(0.5)
                .coerceAtMost(product.getAvailableStock())
            quantityText.text = MarketFormat.formatQuantity(this, selectedQuantity, product.normalizedUnit())
            UiMotion.celebrate(quantityText)
        }
        minus.setOnClickListener { updateQuantity(-0.5) }
        plus.setOnClickListener { updateQuantity(0.5) }
        UiMotion.makePressable(minus, plus)

        val available = MarketFormat.formatQuantity(this, product.getAvailableStock(), product.normalizedUnit())
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
            addView(TextView(this@MapActivity).apply {
                text = getString(R.string.available_stock, available)
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@MapActivity, R.color.muted_ink))
            })
            addView(stepper, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 18
            })
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(product.name)
            .setView(layout)
            .setPositiveButton(getString(R.string.reserve), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val quantity = selectedQuantity
                if (quantity <= 0.0) {
                    Toast.makeText(this, getString(R.string.reservation_error_quantity), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                ReservationService.reserveProduct(db, product, buyerId, quantity)
                    .addOnSuccessListener {
                        dialog.dismiss()
                        onReserved(quantity)
                    }
                    .addOnFailureListener { error ->
                        val message = if (error.message == "not_enough_stock") {
                            getString(R.string.reservation_error_stock)
                        } else {
                            getString(R.string.reservation_error_generic)
                        }
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
            }
        }

        dialog.show()
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
        val ratings = arrayOf(
            getString(R.string.rating_option_5),
            getString(R.string.rating_option_4),
            getString(R.string.rating_option_3),
            getString(R.string.rating_option_2),
            getString(R.string.rating_option_1)
        )

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
