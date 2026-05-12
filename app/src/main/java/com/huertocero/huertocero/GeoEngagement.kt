package com.huertocero.huertocero

import com.google.android.gms.maps.model.LatLng
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

object GeoEngagement {
    private const val CELL_SCALE = 10.0

    fun geoCell(lat: Double, lng: Double): String {
        val latCell = floor(lat * CELL_SCALE).toInt()
        val lngCell = floor(lng * CELL_SCALE).toInt()
        return "${latCell}_${lngCell}"
    }

    fun nearbyCells(latLng: LatLng): List<String> {
        val latCell = floor(latLng.latitude * CELL_SCALE).toInt()
        val lngCell = floor(latLng.longitude * CELL_SCALE).toInt()
        val cells = mutableListOf<String>()

        for (latOffset in -1..1) {
            for (lngOffset in -1..1) {
                cells.add("${latCell + latOffset}_${lngCell + lngOffset}")
            }
        }

        return cells
    }

    fun todayKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
