package com.example.taller3samuelemperador.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taller3samuelemperador.model.User
import com.example.taller3samuelemperador.repository.FirebaseRepository
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository()

    fun register(
        name: String,
        email: String,
        password: String,
        phone: String,
        callback: (Result<User>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.registerUser(email, password, name, phone)
            callback(result)
        }
    }

    fun login(
        email: String,
        password: String,
        callback: (Result<User>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.loginUser(email, password)
            callback(result)
        }
    }
}