package com.example.taller3samuelemperador.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3samuelemperador.location.LocationManager
import com.example.taller3samuelemperador.model.User
import com.example.taller3samuelemperador.repository.FirebaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository()
    private val locationManager = LocationManager(application)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _onlineUsers = MutableStateFlow<List<User>>(emptyList())
    val onlineUsers: StateFlow<List<User>> = _onlineUsers.asStateFlow()

    private val _isLocationEnabled = MutableStateFlow(false)
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    private var locationJob: Job? = null

    companion object {
        private const val TAG = "MapViewModel"
    }

    init {
        Log.d(TAG, "MapViewModel initialized")

        // Marcar usuario como online al iniciar
        viewModelScope.launch {
            repository.currentUser?.uid?.let { uid ->
                Log.d(TAG, "Setting user $uid as online")
                repository.updateUserOnlineStatus(uid, true)
            }
        }

        // Iniciar observadores
        observeCurrentUser()
        observeOnlineUsers()
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            repository.observeCurrentUser()
                .catch { e ->
                    Log.e(TAG, "Error observing current user", e)
                }
                .collect { user ->
                    Log.d(TAG, "Current user updated: ${user?.name}")
                    _currentUser.value = user
                }
        }
    }

    private fun observeOnlineUsers() {
        viewModelScope.launch {
            repository.observeOnlineUsers()
                .catch { e ->
                    Log.e(TAG, "Error observing online users", e)
                }
                .collect { users ->
                    Log.d(TAG, "Online users updated: ${users.size} users")
                    users.forEach { user ->
                        Log.d(TAG, "- ${user.name}: isOnline=${user.isOnline}, lat=${user.latitude}, lng=${user.longitude}")
                    }
                    _onlineUsers.value = users
                }
        }
    }

    fun toggleLocationSharing(enabled: Boolean) {
        Log.d(TAG, "Toggle location sharing: $enabled")
        _isLocationEnabled.value = enabled

        if (enabled) {
            startLocationUpdates()
        } else {
            stopLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        val uid = repository.currentUser?.uid
        if (uid == null) {
            Log.e(TAG, "Cannot start location updates: user is null")
            return
        }

        Log.d(TAG, "Starting location updates for user $uid")

        // Cancelar job anterior si existe
        locationJob?.cancel()

        locationJob = viewModelScope.launch {
            try {
                locationManager.getLocationUpdates()
                    .catch { e ->
                        Log.e(TAG, "Error in location updates", e)
                        _isLocationEnabled.value = false
                    }
                    .collect { location ->
                        Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
                        repository.updateUserLocation(
                            uid,
                            location.latitude,
                            location.longitude
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in location updates", e)
                _isLocationEnabled.value = false
            }
        }
    }

    private fun stopLocationUpdates() {
        Log.d(TAG, "Stopping location updates")
        locationJob?.cancel()
        locationJob = null

        val uid = repository.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                // Limpiar ubicación en la base de datos
                repository.updateUserLocation(uid, 0.0, 0.0)
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing location", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "MapViewModel cleared")

        // Detener actualizaciones de ubicación
        if (_isLocationEnabled.value) {
            stopLocationUpdates()
        }

        // Marcar usuario como offline
        viewModelScope.launch {
            repository.currentUser?.uid?.let { uid ->
                Log.d(TAG, "Setting user $uid as offline")
                repository.updateUserOnlineStatus(uid, false)
            }
        }
    }
}