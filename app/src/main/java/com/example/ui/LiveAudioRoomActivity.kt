package com.example.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.model.RepositoryManager
import com.example.ui.theme.MyApplicationTheme
import com.example.zego.SocialIndiaZegoManager
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LiveAudioRoomActivity : AppCompatActivity() {
    private var roomID: String = ""
    private var isHost: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!RepositoryManager.isInitialized()) {
            RepositoryManager.init(applicationContext)
        }

        val userName = intent.getStringExtra("userName") ?: "User"
        roomID = intent.getStringExtra("roomID") ?: "audio_room_${System.currentTimeMillis()}"
        val roomTitle = intent.getStringExtra("roomTitle") ?: "Social India Audio Room"
        isHost = intent.getBooleanExtra("isHost", false)

        if (isHost) {
            val currentUid = RepositoryManager.getCurrentUserId()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val roomData = hashMapOf(
                        "roomId" to roomID,
                        "hostUid" to currentUid,
                        "hostUsername" to userName,
                        "title" to roomTitle,
                        "status" to "ACTIVE",
                        "startedAt" to FieldValue.serverTimestamp(),
                        "listenerCount" to 1,
                        "speakers" to listOf(currentUid),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    RepositoryManager.firestore.collection("liveAudioRooms").document(roomID).set(roomData)
                } catch (e: Exception) {
                    android.util.Log.e("LiveAudioRoomActivity", "Failed to register audio room", e)
                }
            }
        }

        setContent {
            MyApplicationTheme(darkTheme = true) {
                NativeLiveAudioRoomScreen(
                    roomId = roomID,
                    roomTitle = roomTitle,
                    isHost = isHost,
                    onLeaveRoom = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isHost && roomID.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RepositoryManager.firestore.collection("liveAudioRooms").document(roomID).update(
                        mapOf(
                            "status" to "ENDED",
                            "endedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } catch (_: Exception) {}
            }
        }
        SocialIndiaZegoManager.cleanup()
    }
}
