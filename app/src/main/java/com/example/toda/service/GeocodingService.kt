package com.example.toda.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class GeocodingService {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&zoom=18&addressdetails=1"
                val response = URL(url).readText()
                val json = JSONObject(response)

                // Extract meaningful address components
                val displayName = json.optString("display_name", "")
                val address = json.optJSONObject("address")

                if (address != null) {
                    val road = address.optString("road", "")
                    val suburb = address.optString("suburb", "")
                    val city = address.optString("city", address.optString("town", address.optString("village", "")))
                    val amenity = address.optString("amenity", "")
                    val shop = address.optString("shop", "")
                    val building = address.optString("building", "")

                    // Build a readable address
                    val parts = mutableListOf<String>()
                    if (amenity.isNotEmpty()) parts.add(amenity)
                    if (shop.isNotEmpty()) parts.add(shop)
                    if (building.isNotEmpty()) parts.add(building)
                    if (road.isNotEmpty()) parts.add(road)
                    if (suburb.isNotEmpty()) parts.add(suburb)
                    if (city.isNotEmpty()) parts.add(city)

                    return@withContext if (parts.isNotEmpty()) {
                        parts.take(3).joinToString(", ") // Limit to 3 components for readability
                    } else {
                        "Lat: ${String.format("%.4f", latitude)}, Lng: ${String.format("%.4f", longitude)}"
                    }
                }

                return@withContext displayName.split(",").take(2).joinToString(", ").ifEmpty {
                    "Lat: ${String.format("%.4f", latitude)}, Lng: ${String.format("%.4f", longitude)}"
                }
            } catch (e: Exception) {
                return@withContext "Lat: ${String.format("%.4f", latitude)}, Lng: ${String.format("%.4f", longitude)}"
            }
        }
    }
}