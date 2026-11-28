package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

object FirebaseStorageTest {
    fun test(context: Context) {
        try {
            val storage = FirebaseStorage.getInstance()
            val ref = storage.reference.child("test/${UUID.randomUUID()}.txt")
            ref.putBytes("Hello World".toByteArray())
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { uri ->
                        Log.d("FirebaseStorageTest", "Success: $uri")
                    }.addOnFailureListener { e ->
                        Log.e("FirebaseStorageTest", "Failed to get download URL", e)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseStorageTest", "Upload Failed", e)
                }
        } catch(e: Exception) {
            Log.e("FirebaseStorageTest", "Exception", e)
        }
    }
}
