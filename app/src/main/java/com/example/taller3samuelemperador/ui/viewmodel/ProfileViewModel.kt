package com.example.taller3samuelemperador.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3samuelemperador.model.User
import com.example.taller3samuelemperador.repository.FirebaseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        observeCurrentUser()
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            repository.observeCurrentUser().collect { user ->
                _currentUser.value = user
            }
        }
    }

    fun updateProfile(
        name: String,
        phone: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val uid = repository.currentUser?.uid ?: return

        viewModelScope.launch {
            val result = repository.updateUserProfile(uid, name, phone)
            callback(result)
        }
    }

    fun changePassword(
        newPassword: String,
        callback: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.changePassword(newPassword)
            callback(result)
        }
    }

    fun uploadProfileImage(
        imageUri: Uri,
        callback: (Result<String>) -> Unit
    ) {
        val uid = repository.currentUser?.uid ?: return

        viewModelScope.launch {
            val result = repository.uploadProfileImage(uid, imageUri)
            callback(result)
        }
    }
}