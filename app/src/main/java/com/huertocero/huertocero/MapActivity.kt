package com.huertocero.huertocero

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        db = FirebaseFirestore.getInstance()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val valencia = LatLng(39.4699, -0.3763)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(valencia, 12f))

        loadProducts()
    }

    private fun loadProducts() {

        db.collection("products")
            .get()
            .addOnSuccessListener { result ->

                for (document in result) {

                    val lat = document.getDouble("lat")
                    val lng = document.getDouble("lng")
                    val title = document.getString("title")

                    if (lat != null && lng != null) {

                        val location = LatLng(lat, lng)

                        mMap.addMarker(
                            MarkerOptions()
                                .position(location)
                                .title(title)
                        )
                    }
                }
            }
    }
}