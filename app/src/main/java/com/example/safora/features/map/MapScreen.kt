package com.example.safora.features.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

    // Map UI Preferences
    var isDarkMode by remember { mutableStateOf(true) } // Dark mode enabled by default
    var broadcastToNearby by remember { mutableStateOf(true) } // Toggle sending SOS to nearby people

    // Tactical dark color matrix filter for night / dark mode
    val darkFilter = remember {
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -0.85f, 0f, 0f, 0f, 240f,
                    0f, -0.85f, 0f, 0f, 240f,
                    0f, 0f, -0.85f, 0f, 240f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

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

    Box(modifier = Modifier.fillMaxSize().background(CharcoalDark)) {
        // OpenStreetMap Android View
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(17.0)
                    controller.setCenter(GeoPoint(location.first, location.second))

                    if (isDarkMode) {
                        overlayManager.tilesOverlay.setColorFilter(darkFilter)
                    }

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
                // Apply dark or daylight map style dynamically
                if (isDarkMode) {
                    mapView.overlayManager.tilesOverlay.setColorFilter(darkFilter)
                } else {
                    mapView.overlayManager.tilesOverlay.setColorFilter(null)
                }

                // Clear dynamic markers except MyLocationNewOverlay
                val myLocOverlay = mapView.overlays.firstOrNull { it is MyLocationNewOverlay }
                mapView.overlays.clear()
                myLocOverlay?.let { mapView.overlays.add(it) }

                val userGeo = GeoPoint(location.first, location.second)

                // User Location Pin
                val userMarker = Marker(mapView).apply {
                    position = userGeo
                    title = if (isRealGps) "YOUR LIVE LOCATION (GPS LOCKED)" else "ESTIMATED POSITION"
                    snippet = "Lat: ${"%.5f".format(location.first)}, Lng: ${"%.5f".format(location.second)}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.add(userMarker)

                // Safety Perimeter Circle around user (150m accuracy ring)
                val circle = Polygon.pointsAsCircle(userGeo, 130.0)
                val accuracyCircle = Polygon(mapView).apply {
                    points = circle
                    fillPaint.color = android.graphics.Color.argb(40, 37, 99, 235)
                    outlinePaint.color = android.graphics.Color.argb(140, 37, 99, 235)
                    outlinePaint.strokeWidth = 2.5f
                }
                mapView.overlays.add(accuracyCircle)

                // Responder Markers (displayed if broadcast to nearby is active)
                if (broadcastToNearby) {
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
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Status Header Card
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalDark.copy(alpha = 0.92f)),
            border = BorderStroke(1.dp, CharcoalBorder),
            shape = RoundedCornerShape(14.dp)
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
                            text = if (isRealGps) "LIVE GPS ACTIVE" else "SEARCHING GPS FIX...",
                            color = if (isRealGps) EmeraldSafe else AmberWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${"%.5f".format(location.first)}, ${"%.5f".format(location.second)}",
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }

                // Quick Map Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Dark / Light Mode Toggle
                    IconButton(
                        onClick = { isDarkMode = !isDarkMode },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(CharcoalSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Toggle Dark Mode",
                            tint = if (isDarkMode) CyanAccent else AmberWarning,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Center on Location
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
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(CharcoalSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Center on me",
                            tint = if (isRealGps) BluePrimary else AmberWarning,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // COMMUNITY SOS BROADCAST TOGGLE (Allow sending SOS to nearby people or not)
        // =========================================================================
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (broadcastToNearby) CharcoalSurfaceElevated.copy(alpha = 0.95f) else CharcoalSurface.copy(alpha = 0.95f)
            ),
            border = BorderStroke(
                1.dp,
                if (broadcastToNearby) EmeraldSafe.copy(alpha = 0.4f) else CharcoalBorder
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (broadcastToNearby) EmeraldSafe.copy(alpha = 0.15f) else TextTertiary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (broadcastToNearby) Icons.Default.Podcasts else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (broadcastToNearby) EmeraldSafe else TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (broadcastToNearby) "Nearby Community SOS: ON" else "Private Mode: SOS Contacts Only",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = if (broadcastToNearby) {
                                "Alerts sent to ${responders.size} verified nearby responders within 1.5 km"
                            } else {
                                "SOS broadcasts only to your emergency contacts list"
                            },
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                Switch(
                    checked = broadcastToNearby,
                    onCheckedChange = { broadcastToNearby = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EmeraldSafe,
                        checkedTrackColor = EmeraldSafe.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = CharcoalBorder
                    )
                )
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
                            text = if (broadcastToNearby) {
                                "Broadcasting live GPS to ${responders.size} community responders & emergency contacts"
                            } else {
                                "Broadcasting live GPS exclusively to trusted emergency contacts"
                            },
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
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = if (incident.state != IncidentState.IDLE) 150.dp else 84.dp)
        ) {
            selectedResponder?.let { resp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceElevated),
                    border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.3f)),
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
                            Text("Verified Community Responder (4.9 ★)", color = AmberWarning, fontSize = 11.sp)
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
