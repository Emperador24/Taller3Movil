package com.example.taller3samuelemperador.ui.viewmodel

import android.app.Application
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

    init {
        observeCurrentUser()
        observeOnlineUsers()

        // Marcar usuario como online al iniciar
        viewModelScope.launch {
            repository.currentUser?.uid?.let { uid ->
                repository.updateUserOnlineStatus(uid, true)
            }
        }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            repository.observeCurrentUser()
                .catch { e ->
                    // Manejar error silenciosamente
                    e.printStackTrace()
                }
                .collect { user ->
                    _currentUser.value = user
                }
        }
    }

    private fun observeOnlineUsers() {
        viewModelScope.launch {
            repository.observeOnlineUsers()
                .catch { e ->
                    // Manejar error silenciosamente
                    e.printStackTrace()
                }
                .collect { users ->
                    _onlineUsers.value = users
                }
        }
    }

    fun toggleLocationSharing(enabled: Boolean) {
        _isLocationEnabled.value = enabled

        if (enabled) {
            startLocationUpdates()
        } else {
            stopLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        val uid = repository.currentUser?.uid ?: return

        // Cancelar job anterior si existe
        locationJob?.cancel()

        locationJob = viewModelScope.launch {
            try {
                locationManager.getLocationUpdates()
                    .catch { e ->
                        e.printStackTrace()
                        _isLocationEnabled.value = false
                    }
                    .collect { location ->
                        repository.updateUserLocation(
                            uid,
                            location.latitude,
                            location.longitude
                        )
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                _isLocationEnabled.value = false
            }
        }
    }

    private fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null

        val uid = repository.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                // Limpiar ubicación en la base de datos
                repository.updateUserLocation(uid, 0.0, 0.0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        // Detener actualizaciones de ubicación
        if (_isLocationEnabled.value) {
            stopLocationUpdates()
        }

        // Marcar usuario como offline
        viewModelScope.launch {
            repository.currentUser?.uid?.let { uid ->
                repository.updateUserOnlineStatus(uid, false)
            }
        }
    }
}