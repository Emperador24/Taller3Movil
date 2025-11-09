package com.example.taller3samuelemperador.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3samuelemperador.location.LocationManager
import com.example.taller3samuelemperador.model.User
import com.example.taller3samuelemperador.repository.FirebaseRepository
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

    init {
        observeCurrentUser()
        observeOnlineUsers()
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            repository.observeCurrentUser().collect { user ->
                _currentUser.value = user
            }
        }
    }

    private fun observeOnlineUsers() {
        viewModelScope.launch {
            repository.observeOnlineUsers().collect { users ->
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

        viewModelScope.launch {
            locationManager.getLocationUpdates().collect { location ->
                repository.updateUserLocation(
                    uid,
                    location.latitude,
                    location.longitude
                )
            }
        }
    }

    private fun stopLocationUpdates() {
        val uid = repository.currentUser?.uid ?: return

        viewModelScope.launch {
            // Limpiar ubicación en la base de datos
            repository.updateUserLocation(uid, 0.0, 0.0)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_isLocationEnabled.value) {
            stopLocationUpdates()
        }
    }
}