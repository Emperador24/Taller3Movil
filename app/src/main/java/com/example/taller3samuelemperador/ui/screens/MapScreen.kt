package com.example.taller3samuelemperador.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.taller3samuelemperador.model.User
import com.example.taller3samuelemperador.ui.viewmodel.MapViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNavigateToProfile: () -> Unit,
    onLogout: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val currentUser by viewModel.currentUser.collectAsState()
    val onlineUsers by viewModel.onlineUsers.collectAsState()
    val isLocationEnabled by viewModel.isLocationEnabled.collectAsState()

    // Cache de íconos personalizados
    var userMarkerIcons by remember { mutableStateOf<Map<String, BitmapDescriptor>>(emptyMap()) }
    var currentUserIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(4.6097, -74.0817), // Bogotá por defecto
            12f
        )
    }

    // Función para crear bitmap circular con borde
    fun getCircularBitmapWithBorder(bitmap: Bitmap, borderColor: Int): Bitmap {
        val size = 160
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Dibujar círculo de fondo blanco
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Recortar imagen en círculo
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = android.graphics.RectF(8f, 8f, size - 8f, size - 8f)
        canvas.drawOval(rectF, paint)
        canvas.drawBitmap(bitmap, rect, rectF, paint)

        // Dibujar borde
        paint.xfermode = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.color = borderColor
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)

        return output
    }

    // Función para crear marcador circular con imagen
    suspend fun createMarkerIconFromUrl(imageUrl: String, borderColor: Int): BitmapDescriptor? {
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(120, 120)
                    .build()

                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap(120, 120, Bitmap.Config.ARGB_8888)
                    val circularBitmap = getCircularBitmapWithBorder(bitmap, borderColor)
                    withContext(Dispatchers.Main) {
                        BitmapDescriptorFactory.fromBitmap(circularBitmap)
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // Cargar íconos de usuarios online
    LaunchedEffect(onlineUsers) {
        val newIcons = mutableMapOf<String, BitmapDescriptor>()
        onlineUsers.forEach { user ->
            if (user.profileImageUrl.isNotEmpty()) {
                createMarkerIconFromUrl(
                    user.profileImageUrl,
                    android.graphics.Color.GREEN
                )?.let { icon ->
                    newIcons[user.uid] = icon
                }
            }
        }
        userMarkerIcons = newIcons
    }

    // Cargar ícono del usuario actual
    LaunchedEffect(currentUser?.profileImageUrl) {
        currentUser?.profileImageUrl?.takeIf { it.isNotEmpty() }?.let { imageUrl ->
            currentUserIcon = createMarkerIconFromUrl(
                imageUrl,
                android.graphics.Color.BLUE
            )
        }
    }

    // Solicitar permisos de ubicación al iniciar
    LaunchedEffect(Unit) {
        if (!locationPermissionsState.allPermissionsGranted) {
            locationPermissionsState.launchMultiplePermissionRequest()
        }
    }

    // Actualizar cámara cuando cambia la ubicación del usuario
    LaunchedEffect(currentUser?.latitude, currentUser?.longitude) {
        currentUser?.let { user ->
            if (user.latitude != 0.0 && user.longitude != 0.0) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(user.latitude, user.longitude),
                        15f
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa en Tiempo Real") },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Perfil")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (locationPermissionsState.allPermissionsGranted) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = isLocationEnabled && locationPermissionsState.permissions.any { it.status.isGranted },
                        mapType = MapType.NORMAL
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        myLocationButtonEnabled = true,
                        compassEnabled = true
                    )
                ) {
                    // Marcador del usuario actual
                    currentUser?.let { user ->
                        if (user.latitude != 0.0 && user.longitude != 0.0 && isLocationEnabled) {
                            Marker(
                                state = MarkerState(
                                    position = LatLng(user.latitude, user.longitude)
                                ),
                                title = "Tú: ${user.name}",
                                snippet = "Tu ubicación actual",
                                icon = currentUserIcon ?: BitmapDescriptorFactory
                                    .defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                            )
                        }
                    }

                    // Marcadores de otros usuarios online
                    onlineUsers.forEach { user ->
                        if (user.latitude != 0.0 && user.longitude != 0.0) {
                            Marker(
                                state = MarkerState(
                                    position = LatLng(user.latitude, user.longitude)
                                ),
                                title = user.name,
                                snippet = "Online",
                                icon = userMarkerIcons[user.uid] ?: BitmapDescriptorFactory
                                    .defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                            )
                        }
                    }
                }

                // Control de switch para ubicación
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (isLocationEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = if (isLocationEnabled) "Compartiendo ubicación"
                            else "Ubicación desactivada",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Switch(
                            checked = isLocationEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.toggleLocationSharing(enabled)
                            }
                        )
                    }

                    if (isLocationEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Usuarios online: ${onlineUsers.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Lista de usuarios online
                if (onlineUsers.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .widthIn(max = 200.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                shape = MaterialTheme.shapes.medium
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Usuarios Online",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        onlineUsers.forEach { user ->
                            OnlineUserItem(user)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            } else {
                // Pantalla de permisos
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Permisos de ubicación requeridos",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Esta aplicación necesita acceso a tu ubicación para funcionar correctamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        locationPermissionsState.launchMultiplePermissionRequest()
                    }) {
                        Text("Otorgar permisos")
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineUserItem(user: User) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (user.profileImageUrl.isNotEmpty()) {
            AsyncImage(
                model = user.profileImageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.firstOrNull()?.toString() ?: "?",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.Green)
        )
    }
}