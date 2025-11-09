package com.example.taller3samuelemperador.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val isOnline: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val profileImageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "phone" to phone,
            "isOnline" to isOnline,
            "latitude" to latitude,
            "longitude" to longitude,
            "profileImageUrl" to profileImageUrl,
            "timestamp" to timestamp
        )
    }
}

data class LocationUpdate(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)