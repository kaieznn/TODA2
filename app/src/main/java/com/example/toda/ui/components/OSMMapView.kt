package com.example.toda.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.example.toda.R

@Composable
fun OSMMapView(
    pickupLocation: GeoPoint?,
    dropoffLocation: GeoPoint?,
    onMapClick: (GeoPoint) -> Unit,
    onPickupLocationDragged: (GeoPoint) -> Unit = {},
    onDropoffLocationDragged: (GeoPoint) -> Unit = {},
    modifier: Modifier = Modifier,
    restrictToBarangay177: Boolean = false,
    enableZoom: Boolean = true,
    enableDrag: Boolean = true
) {
    val context = LocalContext.current

    // Initialize OSMDroid configuration
    DisposableEffect(context) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(enableZoom)
            setBuiltInZoomControls(enableZoom)

            // Set initial view
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(14.74800540601891, 121.0499004))

            // Enable map scrolling and dragging
            isTilesScaledToDpi = true
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false

            // Set minimum and maximum zoom levels
            minZoomLevel = 14.0
            maxZoomLevel = 20.0

            // Restrict map bounds if needed
            if (restrictToBarangay177) {
                val centerLat = 14.74800540601891
                val centerLon = 121.0499004
                val offset = 0.01

                val boundingBox = BoundingBox(
                    centerLat + offset,
                    centerLon + offset,
                    centerLat - offset,
                    centerLon - offset
                )
                setScrollableAreaLimitDouble(boundingBox)
            }
        }
    }

    AndroidView(
        factory = { mapView },
        update = { mapView ->
            mapView.overlayManager.clear()

            // Add map click listener
            val mapEventsReceiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    p?.let { onMapClick(it) }
                    return true
                }

                override fun longPressHelper(p: GeoPoint?): Boolean {
                    return false
                }
            }

            val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
            mapView.overlayManager.add(0, mapEventsOverlay)

            // Add pickup marker
            pickupLocation?.let { location ->
                val pickupMarker = Marker(mapView).apply {
                    position = location
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Pickup Location"

                    // Try to use custom drawable, fallback to default
                    try {
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_pickup_pin)
                    } catch (e: Exception) {
                        // Use default OSM marker
                        icon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_mylocation)
                    }

                    isDraggable = enableDrag

                    setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                        override fun onMarkerDrag(marker: Marker?) {}

                        override fun onMarkerDragEnd(marker: Marker?) {
                            marker?.position?.let { newPosition ->
                                onPickupLocationDragged(newPosition)
                            }
                        }

                        override fun onMarkerDragStart(marker: Marker?) {}
                    })
                }
                mapView.overlayManager.add(pickupMarker)
            }

            // Add dropoff marker
            dropoffLocation?.let { location ->
                val dropoffMarker = Marker(mapView).apply {
                    position = location
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Destination"

                    // Try to use custom drawable, fallback to default
                    try {
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_destination_pin)
                    } catch (e: Exception) {
                        // Use default Android marker
                        icon = ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)
                    }

                    isDraggable = enableDrag

                    setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                        override fun onMarkerDrag(marker: Marker?) {}

                        override fun onMarkerDragEnd(marker: Marker?) {
                            marker?.position?.let { newPosition ->
                                onDropoffLocationDragged(newPosition)
                            }
                        }

                        override fun onMarkerDragStart(marker: Marker?) {}
                    })
                }
                mapView.overlayManager.add(dropoffMarker)
            }

            // Draw route if both locations exist
            if (pickupLocation != null && dropoffLocation != null) {
                val roadOverlay = Polyline(mapView).apply {
                    outlinePaint.color = android.graphics.Color.BLUE
                    outlinePaint.strokeWidth = 8.0f
                    addPoint(pickupLocation)
                    addPoint(dropoffLocation)
                }
                mapView.overlayManager.add(roadOverlay)
            }

            mapView.invalidate()
        },
        modifier = modifier
    )

    // Cleanup
    DisposableEffect(mapView) {
        onDispose {
            mapView.onDetach()
        }
    }
}