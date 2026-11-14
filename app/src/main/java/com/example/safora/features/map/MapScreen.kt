package com.example.safora.features.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
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
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.ui.theme.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen() {
    val repository = SaforaRepository.instance
    val context = LocalContext.current
    val location by repository.currentLocation.collectAsState()
    val responders by repository.responders.collectAsState()
    val incident by repository.currentIncident.collectAsState()

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // OpenStreetMap Android View
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(16.0)
                    controller.setCenter(GeoPoint(location.first, location.second))
                    mapViewRef = this
                }
            },
            update = { mapView ->
                mapView.overlays.clear()

                // User Location Marker
                val userGeo = GeoPoint(location.first, location.second)
                val userMarker = Marker(mapView).apply {
                    position = userGeo
                    title = "You (Live SAFORA GPS)"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.add(userMarker)

                // Responder Markers
                responders.forEach { responder ->
                    val respMarker = Marker(mapView).apply {
                        position = GeoPoint(responder.latitude, responder.longitude)
                        title = "${responder.name} (${responder.distanceMeters}m away)"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mapView.overlays.add(respMarker)
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Info Card on top of map
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalDark.copy(alpha = 0.92f)),
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
                    Text(
                        text = "SAFETY PATROL DISPATCH",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${responders.count { it.isAvailable }} Responders Nearby",
                        color = EmeraldSafe,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                IconButton(
                    onClick = {
                        mapViewRef?.controller?.animateTo(GeoPoint(location.first, location.second))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Center on me",
                        tint = BluePrimary
                    )
                }
            }
        }

        // Active incident banner if SOS triggered
        if (incident.state != com.example.safora.data.model.IncidentState.IDLE &&
            incident.state != com.example.safora.data.model.IncidentState.RESOLVED
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 90.dp)
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
                            text = "ACTIVE INCIDENT DISPATCH",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Broadcasting GPS to opted-in responders",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
