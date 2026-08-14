package com.crome.forecastpoint.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.crome.forecastpoint.R
import com.crome.forecastpoint.data.GeocodeResult
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted
import com.crome.forecastpoint.ui.theme.PrimaryBlue
import com.crome.forecastpoint.ui.theme.SurfaceDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.ScaleBarOverlay

/** Clean light basemap — fewer visual elements, readable place names. */
private val CartoLightTiles: OnlineTileSourceBase = object : XYTileSource(
    "CartoPositron",
    1,
    18,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/light_all/",
        "https://b.basemaps.cartocdn.com/light_all/",
        "https://c.basemaps.cartocdn.com/light_all/",
        "https://d.basemaps.cartocdn.com/light_all/",
    ),
    "© OpenStreetMap © CARTO",
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
    }
}

/**
 * Map picker:
 * - Tap places a **red selection pin**; city-name chip appears — tap chip to accept.
 * - GPS shows a distinct **blue “you are here” pin** that stays on the map and
 *   updates as the device moves (never removed by choosing a location).
 * - FAB (bottom-right) toggles search overlay.
 * - Search defaults to **top** unless [searchAtBottom] is true (settings).
 */
@Composable
fun MapScreen(
    initialLat: Double = 39.8283,
    initialLon: Double = -98.5795,
    initialZoom: Double = 4.0,
    searchAtBottom: Boolean = false,
    onSearch: suspend (String) -> List<GeocodeResult>,
    onReverseGeocode: suspend (Double, Double) -> GeocodeResult,
    onConfirmLocation: (GeocodeResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Tap the map to drop a pin, then tap the name to accept") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodeResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var locating by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<GeocodeResult?>(null) }
    var locationPermitted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED,
        )
    }

    val mapView = remember {
        createMapView(context, initialLat, initialLon, initialZoom)
    }
    // Red teardrop — chosen weather location (tap / search)
    val selectionMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Selected"
            id = "selection"
            ContextCompat.getDrawable(context, R.drawable.ic_map_selection_pin)?.let { icon = it }
        }
    }
    // Blue GPS disk — device position only (persistent while map is open)
    val myLocationMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "My location"
            id = "my_location"
            setInfoWindow(null)
            ContextCompat.getDrawable(context, R.drawable.ic_map_my_location)?.let { icon = it }
        }
    }

    fun flyTo(lat: Double, lon: Double, zoom: Double = 11.0) {
        mapView.controller.animateTo(GeoPoint(lat, lon), zoom, 600L)
    }

    /** Ensure my-location marker is on the map (without touching selection). */
    fun ensureMyLocationMarkerVisible() {
        if (!mapView.overlays.contains(myLocationMarker)) {
            mapView.overlays.add(myLocationMarker)
        }
    }

    fun updateMyLocationPin(lat: Double, lon: Double, fly: Boolean = false) {
        myLocationMarker.position = GeoPoint(lat, lon)
        ensureMyLocationMarkerVisible()
        if (fly) {
            flyTo(lat, lon, mapView.zoomLevelDouble.coerceAtLeast(11.0))
        }
        mapView.invalidate()
    }

    /** Place / move the red selection pin only (does not remove GPS pin). */
    fun placeSelectionMarker(lat: Double, lon: Double) {
        mapView.overlays.removeAll { it is Marker && it.id == "selection" }
        selectionMarker.position = GeoPoint(lat, lon)
        mapView.overlays.add(selectionMarker)
        ensureMyLocationMarkerVisible()
        mapView.invalidate()
    }

    fun selectPoint(lat: Double, lon: Double, known: GeocodeResult? = null) {
        placeSelectionMarker(lat, lon)
        flyTo(lat, lon, mapView.zoomLevelDouble.coerceAtLeast(10.0))
        if (known != null) {
            pending = known
            status = "Tap “${known.name}” to use this location"
            return
        }
        resolving = true
        status = "Looking up place name…"
        scope.launch {
            val place = runCatching { onReverseGeocode(lat, lon) }.getOrElse {
                GeocodeResult(
                    name = String.format("%.3f, %.3f", lat, lon),
                    displayName = "Selected point",
                    latitude = lat,
                    longitude = lon,
                )
            }
            pending = place
            resolving = false
            status = "Tap “${place.name}” to use this location"
        }
    }

    fun centerOnMyLocation(fly: Boolean = true) {
        locating = true
        val loc = lastKnownLocation(context)
        if (loc != null) {
            updateMyLocationPin(loc.latitude, loc.longitude, fly = fly)
            status = "Blue pin = your location · Tap the map to pick a weather spot"
            locating = false
        } else {
            status = "Location unavailable — enable GPS or pick on map"
            locating = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            locationPermitted = true
            centerOnMyLocation(fly = true)
        } else {
            locationPermitted = false
            status = "Location permission denied — search or tap the map"
            locating = false
            if (initialZoom >= 6.0) {
                flyTo(initialLat, initialLon, initialZoom)
            }
        }
    }

    LaunchedEffect(Unit) {
        pending = null
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            centerOnMyLocation(fly = true)
        } else {
            if (initialZoom >= 6.0) {
                flyTo(initialLat, initialLon, initialZoom)
                status = "Tap the map to drop a pin, then tap the name to accept"
            }
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    // Map lifecycle (resume/pause always while this screen is composed)
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Live GPS updates for the blue pin while permitted + map open
    DisposableEffect(locationPermitted) {
        if (!locationPermitted) {
            return@DisposableEffect onDispose { }
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                updateMyLocationPin(location.latitude, location.longitude, fly = false)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        try {
            val providers = buildList {
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    add(LocationManager.GPS_PROVIDER)
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    add(LocationManager.NETWORK_PROVIDER)
                }
            }
            for (p in providers) {
                lm.requestLocationUpdates(
                    p,
                    /* minTimeMs = */ 4_000L,
                    /* minDistanceM = */ 8f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
        } catch (_: SecurityException) {
            // Permission revoked mid-session
        }
        onDispose {
            try {
                lm.removeUpdates(listener)
            } catch (_: Exception) {
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1B262C)),
    ) {
        // Full-screen map
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.setMultiTouchControls(true)
                mapView.isHorizontalMapRepetitionEnabled = false
                mapView.isVerticalMapRepetitionEnabled = false
                mapView.setFlingEnabled(true)

                val receiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (p == null) return false
                        selectPoint(p.latitude, p.longitude)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                }
                mapView.overlays.add(0, MapEventsOverlay(receiver))
                mapView
            },
            update = { /* keep instance */ },
        )

        // Status strip — sits under top search when open
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(2f),
        ) {
            // Search overlay at TOP (default unless settings put it at bottom)
            AnimatedVisibility(
                visible = searchVisible && !searchAtBottom,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
            ) {
                MapSearchBar(
                    query = query,
                    onQueryChange = { q ->
                        query = q
                        searchJob?.cancel()
                        if (q.isBlank()) {
                            results = emptyList()
                            searching = false
                        } else {
                            searchJob = scope.launch {
                                delay(350)
                                searching = true
                                results = runCatching { onSearch(q) }.getOrDefault(emptyList())
                                searching = false
                            }
                        }
                    },
                    results = results,
                    searching = searching,
                    locating = locating,
                    onResultClick = { r ->
                        query = r.name
                        results = emptyList()
                        searchVisible = false
                        selectPoint(r.latitude, r.longitude, known = r)
                    },
                    onMyLocationClick = {
                        requestOrGoToMyLocation(
                            context,
                            permissionLauncher,
                            ::centerOnMyLocation,
                        )
                    },
                    onClose = { searchVisible = false },
                )
            }

            StatusLine(status)
        }

        // Accept chip (only after user picks a point)
        pending?.let { place ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 48.dp)
                    .padding(horizontal = 24.dp)
                    .zIndex(3f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PrimaryBlue,
                    shadowElevation = 6.dp,
                    modifier = Modifier.clickable {
                        status = "Loading weather for ${place.name}…"
                        onConfirmLocation(place)
                    },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Place,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = place.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Start,
                            )
                            Text(
                                text = "Tap to use this location",
                                color = Color(0xFFBBDEFB),
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Accept",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Box(
                    Modifier
                        .size(width = 14.dp, height = 8.dp)
                        .background(PrimaryBlue, RoundedCornerShape(1.dp)),
                )
            }
        }

        if (resolving) {
            CircularProgressIndicator(
                Modifier
                    .align(Alignment.Center)
                    .zIndex(4f),
                color = PrimaryBlue,
            )
        }

        // Search at BOTTOM (settings)
        AnimatedVisibility(
            visible = searchVisible && searchAtBottom,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 80.dp) // clear of FAB
                .zIndex(2f),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            MapSearchBar(
                query = query,
                onQueryChange = { q ->
                    query = q
                    searchJob?.cancel()
                    if (q.isBlank()) {
                        results = emptyList()
                        searching = false
                    } else {
                        searchJob = scope.launch {
                            delay(350)
                            searching = true
                            results = runCatching { onSearch(q) }.getOrDefault(emptyList())
                            searching = false
                        }
                    }
                },
                results = results,
                searching = searching,
                locating = locating,
                onResultClick = { r ->
                    query = r.name
                    results = emptyList()
                    searchVisible = false
                    selectPoint(r.latitude, r.longitude, known = r)
                },
                onMyLocationClick = {
                    requestOrGoToMyLocation(
                        context,
                        permissionLauncher,
                        ::centerOnMyLocation,
                    )
                },
                onClose = { searchVisible = false },
            )
        }

        // FAB always on top of map
        FloatingActionButton(
            onClick = { searchVisible = !searchVisible },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .zIndex(5f),
            containerColor = PrimaryBlue,
            contentColor = Color.White,
            shape = CircleShape,
        ) {
            Icon(
                imageVector = if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                contentDescription = if (searchVisible) "Hide search" else "Show search",
            )
        }
    }
}

private fun requestOrGoToMyLocation(
    context: Context,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    centerOnMyLocation: () -> Unit,
) {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    if (fine || coarse) {
        centerOnMyLocation()
    } else {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }
}

@Composable
private fun StatusLine(status: String) {
    Text(
        text = status,
        color = OnSurfaceMuted,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xE61B262C))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun MapSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<GeocodeResult>,
    searching: Boolean,
    locating: Boolean,
    onResultClick: (GeocodeResult) -> Unit,
    onMyLocationClick: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceDark.copy(alpha = 0.96f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search city…", color = OnSurfaceMuted) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = OnSurfaceMuted)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Color(0xFF546E7A),
                    cursorColor = PrimaryBlue,
                ),
                shape = RoundedCornerShape(10.dp),
            )
            IconButton(onClick = onMyLocationClick) {
                if (locating) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(22.dp)
                            .padding(2.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryBlue,
                    )
                } else {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = "My location",
                        tint = PrimaryBlue,
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Hide search", tint = OnSurfaceMuted)
            }
        }

        if (searching) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(8.dp)
                    .height(20.dp),
                strokeWidth = 2.dp,
                color = PrimaryBlue,
            )
        }

        if (results.isNotEmpty()) {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .padding(top = 4.dp),
            ) {
                items(results, key = { "${it.latitude},${it.longitude},${it.name}" }) { r ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onResultClick(r) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text(r.name, color = Color.White, fontSize = 15.sp)
                        Text(r.displayName, color = OnSurfaceMuted, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun createMapView(
    context: Context,
    lat: Double,
    lon: Double,
    zoom: Double,
): MapView {
    Configuration.getInstance().userAgentValue = context.packageName
    Configuration.getInstance().osmdroidBasePath = context.cacheDir
    Configuration.getInstance().osmdroidTileCache = context.cacheDir.resolve("osmdroid")

    return MapView(context).apply {
        setTileSource(CartoLightTiles)
        setMultiTouchControls(true)
        setFlingEnabled(true)
        isTilesScaledToDpi = true
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        controller.setZoom(zoom)
        controller.setCenter(GeoPoint(lat, lon))
        minZoomLevel = 3.0
        maxZoomLevel = 18.0
        val scale = ScaleBarOverlay(this)
        scale.setAlignBottom(true)
        scale.setAlignRight(false)
        overlays.add(scale)
    }
}

private fun lastKnownLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
    return providers.mapNotNull { p ->
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                lm.getLastKnownLocation(p)
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        }
    }.maxByOrNull { it.time }
}
