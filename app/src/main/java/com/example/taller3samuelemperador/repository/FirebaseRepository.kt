package com.example.taller3samuelemperador.repository

import android.net.Uri
import com.example.taller3samuelemperador.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val usersRef = database.getReference("users")

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    // Registro de usuario
    suspend fun registerUser(
        email: String,
        password: String,
        name: String,
        phone: String
    ): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("UID is null")

            val user = User(
                uid = uid,
                name = name,
                email = email,
                phone = phone,
                isOnline = false
            )

            usersRef.child(uid).setValue(user.toMap()).await()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Login de usuario
    suspend fun loginUser(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("UID is null")

            // Obtener datos del usuario
            val snapshot = usersRef.child(uid).get().await()
            val user = snapshot.getValue(User::class.java)
                ?: throw Exception("User data not found")

            // Marcar como online
            updateUserOnlineStatus(uid, true)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Cerrar sesión
    suspend fun logoutUser() {
        currentUser?.uid?.let { uid ->
            updateUserOnlineStatus(uid, false)
        }
        auth.signOut()
    }

    // Actualizar estado online
    suspend fun updateUserOnlineStatus(uid: String, isOnline: Boolean) {
        try {
            usersRef.child(uid).child("isOnline").setValue(isOnline).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Actualizar ubicación
    suspend fun updateUserLocation(uid: String, latitude: Double, longitude: Double) {
        try {
            val updates = mapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "timestamp" to System.currentTimeMillis()
            )
            usersRef.child(uid).updateChildren(updates).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Actualizar perfil
    suspend fun updateUserProfile(
        uid: String,
        name: String,
        phone: String
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "name" to name,
                "phone" to phone
            )
            usersRef.child(uid).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Cambiar contraseña
    suspend fun changePassword(newPassword: String): Result<Unit> {
        return try {
            currentUser?.updatePassword(newPassword)?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Subir imagen de perfil
    suspend fun uploadProfileImage(uid: String, imageUri: Uri): Result<String> {
        return try {
            val ref = storage.reference.child("profile_images/$uid.jpg")
            ref.putFile(imageUri).await()
            val downloadUrl = ref.downloadUrl.await().toString()

            usersRef.child(uid).child("profileImageUrl").setValue(downloadUrl).await()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Obtener usuario actual
    suspend fun getCurrentUser(): User? {
        return try {
            val uid = currentUser?.uid ?: return null
            val snapshot = usersRef.child(uid).get().await()
            snapshot.getValue(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // Observar usuario actual
    fun observeCurrentUser(): Flow<User?> = callbackFlow {
        val uid = currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                trySend(user)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        usersRef.child(uid).addValueEventListener(listener)

        awaitClose {
            usersRef.child(uid).removeEventListener(listener)
        }
    }

    // Observar usuarios online
    fun observeOnlineUsers(): Flow<List<User>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = mutableListOf<User>()
                snapshot.children.forEach { child ->
                    child.getValue(User::class.java)?.let { user ->
                        if (user.isOnline && user.uid != currentUser?.uid) {
                            users.add(user)
                        }
                    }
                }
                trySend(users)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        usersRef.addValueEventListener(listener)

        awaitClose {
            usersRef.removeEventListener(listener)
        }
    }
}