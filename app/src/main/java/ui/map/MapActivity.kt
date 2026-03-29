package com.huertocero.huertocero.ui.map

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.auth.FirebaseAuth
import com.huertocero.huertocero.*
import com.huertocero.huertocero.R

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var bottomSheet: View

    // TOP BUTTONS
    private lateinit var btnFavorites: Button
    private lateinit var btnReservations: Button
    private lateinit var btnLogout: Button

    // MODES
    private lateinit var layoutCreate: View
    private lateinit var layoutView: View

    // CREATE
    private lateinit var btnUploadImage: Button
    private lateinit var ivPreview: ImageView
    private lateinit var etName: EditText
    private lateinit var etDescription: EditText
    private lateinit var etPrice: EditText
    private lateinit var btnSaveProduct: Button
    private lateinit var btnAddAnother: Button

    // VIEW
    private lateinit var tvName: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvPrice: TextView
    private lateinit var btnFavorite: Button
    private lateinit var btnReserve: Button
    private lateinit var btnNavigate: Button

    // COMMON
    private lateinit var btnClose: Button

    private val repository = ProductRepository()

    private var tempMarker: Marker? = null
    private var selectedProduct: Product? = null

    // IMAGE PICKER
    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                ivPreview.visibility = View.VISIBLE
                ivPreview.setImageURI(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        setupTopButtons()
        setupMap()
        setupBottomSheet()
    }

    // ---------------- TOP ----------------

    private fun setupTopButtons() {

        btnFavorites = findViewById(R.id.btnFavorites)
        btnReservations = findViewById(R.id.btnReservations)
        btnLogout = findViewById(R.id.btnLogout)

        btnFavorites.setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }

        btnReservations.setOnClickListener {
            startActivity(Intent(this, ReservationsActivity::class.java))
        }

        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // ---------------- MAP ----------------

    private fun setupMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val valencia = LatLng(39.4699, -0.3763)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(valencia, 12f))

        loadProducts()

        // TAP MAPA → CREAR
        mMap.setOnMapClickListener { latLng ->
            tempMarker?.remove()
            tempMarker = mMap.addMarker(MarkerOptions().position(latLng))
            showCreateMode()
        }

        // TAP MARKER → VER
        mMap.setOnMarkerClickListener { marker ->
            val product = marker.tag as? Product ?: return@setOnMarkerClickListener false
            showViewMode(product)
            true
        }
    }

    // ---------------- FIREBASE ----------------

    private fun loadProducts() {

        repository.getProducts { list ->

            mMap.clear()

            list.forEach { product ->

                if (product.hasValidLocation()) {

                    val marker = mMap.addMarker(
                        MarkerOptions().position(product.getPosition())
                    )

                    marker?.tag = product
                }
            }
        }
    }

    private fun saveProduct() {

        val latLng = tempMarker?.position ?: return

        val product = Product(
            name = etName.text.toString(),
            description = etDescription.text.toString(),
            price = etPrice.text.toString(),
            lat = latLng.latitude,
            lng = latLng.longitude
        )

        repository.addProduct(product)

        Toast.makeText(this, "Producto guardado", Toast.LENGTH_SHORT).show()

        tempMarker?.remove()
        tempMarker = null

        clearForm()
        hideBottomSheet()
        loadProducts()
    }

    // ---------------- BOTTOM SHEET ----------------

    private fun setupBottomSheet() {

        bottomSheet = findViewById(R.id.bottomSheet)

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            isHideable = true
            state = BottomSheetBehavior.STATE_HIDDEN
        }

        // 🔥 CLAVE: evita bloqueo UI
        bottomSheet.visibility = View.GONE

        // layouts
        layoutCreate = bottomSheet.findViewById(R.id.layoutCreate)
        layoutView = bottomSheet.findViewById(R.id.layoutView)

        // create
        btnUploadImage = bottomSheet.findViewById(R.id.btnUploadImage)
        ivPreview = bottomSheet.findViewById(R.id.ivPreview)
        etName = bottomSheet.findViewById(R.id.etName)
        etDescription = bottomSheet.findViewById(R.id.etDescription)
        etPrice = bottomSheet.findViewById(R.id.etPrice)
        btnSaveProduct = bottomSheet.findViewById(R.id.btnSaveProduct)
        btnAddAnother = bottomSheet.findViewById(R.id.btnAddAnother)

        // view
        tvName = bottomSheet.findViewById(R.id.tvName)
        tvDescription = bottomSheet.findViewById(R.id.tvDescription)
        tvPrice = bottomSheet.findViewById(R.id.tvPrice)
        btnFavorite = bottomSheet.findViewById(R.id.btnFavorite)
        btnReserve = bottomSheet.findViewById(R.id.btnReserve)
        btnNavigate = bottomSheet.findViewById(R.id.btnNavigate)

        btnClose = bottomSheet.findViewById(R.id.btnClose)

        btnClose.setOnClickListener { hideBottomSheet() }

        btnUploadImage.setOnClickListener {
            imagePicker.launch("image/*")
        }

        btnSaveProduct.setOnClickListener { saveProduct() }

        btnAddAnother.setOnClickListener { clearForm() }

        btnFavorite.setOnClickListener {
            Toast.makeText(this, "Añadido a favoritos", Toast.LENGTH_SHORT).show()
        }

        btnReserve.setOnClickListener {
            Toast.makeText(this, "Producto reservado", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- MODOS ----------------

    private fun showCreateMode() {
        layoutCreate.visibility = View.VISIBLE
        layoutView.visibility = View.GONE
        showBottomSheet()
    }

    private fun showViewMode(product: Product) {

        selectedProduct = product

        layoutCreate.visibility = View.GONE
        layoutView.visibility = View.VISIBLE

        tvName.text = product.name
        tvDescription.text = product.description
        tvPrice.text = product.getFormattedPrice()

        btnNavigate.setOnClickListener {
            val uri = Uri.parse("google.navigation:q=${product.lat},${product.lng}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            startActivity(intent)
        }

        showBottomSheet()
    }

    // ---------------- UI ----------------

    private fun showBottomSheet() {
        bottomSheet.visibility = View.VISIBLE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun hideBottomSheet() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheet.visibility = View.GONE
    }

    private fun clearForm() {
        etName.text.clear()
        etDescription.text.clear()
        etPrice.text.clear()
        ivPreview.visibility = View.GONE
    }
}
