package com.example.safora.features.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.data.model.IncidentState
import com.example.safora.data.model.Responder
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.services.location.SaforaLocationClient
import com.example.safora.ui.theme.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val repository = remember { SaforaRepository.getInstance(context) }
    val location by repository.currentLocation.collectAsState()
    val isRealGps by repository.isRealLocationLocked.collectAsState()
    val responders by repository.responders.collectAsState()
    val incident by repository.currentIncident.collectAsState()

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var selectedResponder by remember { mutableStateOf<Responder?>(null) }
    var hasLocationPermission by remember { mutableStateOf(PermissionHelper.hasLocationPermission(context)) }

    // On-demand location permission requester
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        hasLocationPermission = granted
        if (granted) {
            val locationClient = SaforaLocationClient(context)
            locationClient.requestCurrentLocation { loc ->
                mapViewRef?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
            }
            locationClient.startLocationUpdates()
        }
    }

    // Auto-request GPS & start updates if permission granted
    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val locationClient = SaforaLocationClient(context)
            locationClient.requestCurrentLocation { loc ->
                mapViewRef?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
            }
            locationClient.startLocationUpdates()
            onDispose {
                locationClient.stopLocationUpdates()
            }
        } else {
            onDispose { }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // OpenStreetMap Android View
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(17.0)
                    controller.setCenter(GeoPoint(location.first, location.second))

                    // Built-in location overlay for real blue pulsing GPS dot
                    if (PermissionHelper.hasLocationPermission(ctx)) {
                        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                        locationOverlay.enableMyLocation()
                        locationOverlay.enableFollowLocation()
                        overlays.add(locationOverlay)
                    }

                    mapViewRef = this
                }
            },
            update = { mapView ->
                // Clear dynamic markers except MyLocationNewOverlay
                val myLocOverlay = mapView.overlays.firstOrNull { it is MyLocationNewOverlay }
                mapView.overlays.clear()
                myLocOverlay?.let { mapView.overlays.add(it) }

                val userGeo = GeoPoint(location.first, location.second)

                // User Location Pin
                val userMarker = Marker(mapView).apply {
                    position = userGeo
                    title = if (isRealGps) "YOUR LIVE LOCATION (GPS LOCKED)" else "CURRENT POSITION"
                    snippet = "Lat: ${"%.5f".format(location.first)}, Lng: ${"%.5f".format(location.second)}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.add(userMarker)

                // Add Safety Perimeter Circle around user (150m accuracy ring)
                val circle = Polygon.pointsAsCircle(userGeo, 120.0)
                val accuracyCircle = Polygon(mapView).apply {
                    points = circle
                    fillPaint.color = android.graphics.Color.argb(35, 37, 99, 235)
                    outlinePaint.color = android.graphics.Color.argb(120, 37, 99, 235)
                    outlinePaint.strokeWidth = 2f
                }
                mapView.overlays.add(accuracyCircle)

                // Responder Markers
                responders.forEach { responder ->
                    val respMarker = Marker(mapView).apply {
                        position = GeoPoint(responder.latitude, responder.longitude)
                        title = "${responder.name} (${responder.distanceMeters}m)"
                        snippet = "${responder.role} • ETA ${responder.etaMinutes} min"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            selectedResponder = responder
                            true
                        }
                    }
                    mapView.overlays.add(respMarker)
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Status Header Card
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalDark.copy(alpha = 0.94f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isRealGps) EmeraldSafe else AmberWarning)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRealGps) "LIVE PHONE GPS LOCKED" else "ACQUIRING GPS FIX...",
                            color = if (isRealGps) EmeraldSafe else AmberWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${"%.5f".format(location.first)}, ${"%.5f".format(location.second)}",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { mapViewRef?.controller?.zoomIn() }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = TextPrimary)
                    }
                    IconButton(
                        onClick = { mapViewRef?.controller?.zoomOut() }
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = TextPrimary)
                    }
                    IconButton(
                        onClick = {
                            if (!hasLocationPermission) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                val locationClient = SaforaLocationClient(context)
                                locationClient.requestCurrentLocation { loc ->
                                    mapViewRef?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Center on me",
                            tint = if (isRealGps) BluePrimary else AmberWarning
                        )
                    }
                }
            }
        }

        // Permission Request Warning Banner if user rejected location
        if (!hasLocationPermission) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                color = AmberWarning.copy(alpha = 0.95f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Precise location disabled. Enable GPS for emergency tracking.",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Enable", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        // Active Incident Banner
        if (incident.state != IncidentState.IDLE && incident.state != IncidentState.RESOLVED && incident.state != IncidentState.CANCELLED) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 84.dp)
                    .fillMaxWidth(),
                color = CrimsonEmergencyDark,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "INCIDENT LOCATION BROADCASTING",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Broadcasting live GPS to ${responders.size} nearby community responders",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Selected Responder Detail Bottom Card
        AnimatedVisibility(
            visible = selectedResponder != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = if (incident.state != IncidentState.IDLE) 150.dp else 84.dp)
        ) {
            selectedResponder?.let { resp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(resp.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                            Text("${resp.role} • ${resp.distanceMeters}m away • ETA ~${resp.etaMinutes} min", color = TextSecondary, fontSize = 11.sp)
                            Text("Rating: ${resp.rating} ★ (Opted-in Verified Responder)", color = AmberWarning, fontSize = 11.sp)
                        }
                        IconButton(onClick = { selectedResponder = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextTertiary)
                        }
                    }
                }
            }
        }
    }
}
