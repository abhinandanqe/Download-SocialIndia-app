# Social India - Production-Ready Architecture (Firebase + WebRTC)

यह दस्तावेज़ Social India ऐप के लिए बिना किसी थर्ड-पार्टी SDK (जैसे Agora/Twilio) के पूरी तरह से Firebase और WebRTC पर आधारित प्रोडक्शन-रेडी आर्किटेक्चर का वर्णन करता है।

## 1. ऑडियो और वीडियो कॉलिंग (WebRTC + Firebase RTDB)

कॉलिंग के लिए, हम WebRTC का उपयोग करेंगे जो पीयर-टू-पीयर (P2P) कनेक्शन बनाता है। Firebase Realtime Database (RTDB) का उपयोग 'Signaling' के लिए किया जाता है ताकि दो यूज़र्स एक-दूसरे को Offer, Answer, और ICE Candidates भेज सकें।

### Signaling Architecture (RTDB)
- **Node**: `/calls/{userId}`
- **Types**: `offer`, `answer`, `candidate`, `end_call`, `heart`

### Kotlin Code Snippets: Signaling & WebRTC

हमने ऐप में `WebRTCManager.kt` जोड़ा है जो WebRTC की सभी जटिलताओं (PeerConnectionFactory, VideoCapturer, SDP Observers) को संभालता है। 

Signaling को Firebase में भेजने के लिए, हमें `WebRTCSignalingClient` को लागू करना होगा:

```kotlin
class FirebaseSignalingClient(
    private val rtdb: DatabaseReference,
    private val currentUserId: String
) : WebRTCSignalingClient {

    override fun sendOffer(targetUserId: String, sdp: String) {
        val payload = mapOf(
            "type" to "offer",
            "sdp" to sdp,
            "senderId" to currentUserId,
            "timestamp" to ServerValue.TIMESTAMP
        )
        rtdb.child("calls").child(targetUserId).setValue(payload)
    }

    override fun sendAnswer(targetUserId: String, sdp: String) {
        val payload = mapOf(
            "type" to "answer",
            "sdp" to sdp,
            "senderId" to currentUserId
        )
        rtdb.child("calls").child(targetUserId).setValue(payload)
    }

    override fun sendIceCandidate(targetUserId: String, candidate: IceCandidate) {
        val payload = mapOf(
            "type" to "candidate",
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp,
            "senderId" to currentUserId
        )
        rtdb.child("calls").child(targetUserId).push().setValue(payload)
    }
}
```

## 2. लाइव स्ट्रीमिंग (Firebase + WebRTC)

लाइव स्ट्रीमिंग के लिए भी WebRTC का उपयोग किया जाएगा। इसमें 'Broadcaster' (होस्ट) अपना वीडियो/ऑडियो भेजता है और 'Viewers' (दर्शक) उसे प्राप्त करते हैं।

### Firestore 'LiveStreams' Collection Structure:
```json
{
  "streamId": "uuid-1234",
  "hostId": "user_id",
  "status": "live", // 'live' or 'ended'
  "viewersCount": 150,
  "startedAt": "timestamp"
}
```

**Viewer & Reaction Logic (Firestore):**
```kotlin
fun incrementViewerCount(streamId: String) {
    firestore.collection("LiveStreams").document(streamId)
        .update("viewersCount", FieldValue.increment(1))
}

// रिएक्शंस के लिए RTDB या Firestore का इस्तेमाल करें ताकि रियल-टाइम एनीमेशन दिखे।
fun sendLiveReaction(streamId: String, emoji: String) {
    rtdb.child("LiveReactions").child(streamId).push().setValue(
        mapOf("emoji" to emoji, "timestamp" to ServerValue.TIMESTAMP)
    )
}
```

## 3. क्लाउड मैसेजिंग (Firebase Cloud Messaging - FCM)

ऐप में `SocialIndiaMessagingService.kt` लागू किया गया है। 
बैकएंड सर्वर (जैसे Node.js + Firebase Admin SDK) या Cloud Functions के माध्यम से, जब भी कोई कॉल या मैसेज आता है, तो FCM का डाटा पेलोड (Data Payload) भेजा जाता है:

**Backend Code (Node.js) Example for Call Notification:**
```javascript
const payload = {
  data: {
    type: "call",
    mediaType: "video",
    peerId: "user_789",
    peerName: "Priya",
    message: "Incoming video call..."
  },
  token: targetUserFcmToken
};
admin.messaging().send(payload);
```
ऐंड्रॉइड क्लाइंट इसे रिसीव करके `NotificationManager` के ज़रिए फुल-स्क्रीन इनकमिंग कॉल UI (`setFullScreenIntent`) ट्रिगर करता है।

## 4. एडवांस्ड सोशल फीचर्स

### स्टोरीज (24-Hour Expiry)
Firestore में स्टोरी सेव करते समय `expiresAt` फील्ड जोड़ें और क्लाइंट साइड पर फ़िल्टर करें:
```kotlin
val expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
val story = hashMapOf(
    "mediaUrl" to url,
    "createdAt" to FieldValue.serverTimestamp(),
    "expiresAt" to expiresAt
)
// Fetch Query
firestore.collection("stories")
    .whereGreaterThan("expiresAt", System.currentTimeMillis())
```

### फॉलो/अनफॉलो (Transaction)
ऐप के `SocialRepository.kt` में हमने `runTransaction` का इस्तेमाल किया है ताकि फालोअर्स का काउंट सिंक रहे।
```kotlin
firestore.runTransaction { transaction ->
    val followDoc = transaction.get(followDocRef)
    if (!followDoc.exists()) {
        transaction.set(followDocRef, followData)
        transaction.update(followerRef, "followingCount", FieldValue.increment(1))
        transaction.update(followingRef, "followerCount", FieldValue.increment(1))
    }
}
```

### मीडिया ऑप्टिमाइज़ेशन (Cloudinary)
ऐप में Cloudinary SDK (`kotlin-url-gen`) मौजूद है। वीडियो को ऑटो-थंबनेल में बदलने या कंप्रेस करने के लिए:
```kotlin
import com.cloudinary.Cloudinary
import com.cloudinary.Transformation

val cloudinary = Cloudinary("cloudinary://API_KEY:API_SECRET@CLOUD_NAME")

// वीडियो से थंबनेल (Image) प्राप्त करना
val thumbnailUrl = cloudinary.url()
    .resourceType("video")
    .transformation(Transformation().width(400).crop("limit").format("jpg"))
    .generate("social_india/videos/sample_video")

// वीडियो को ऑटो-ऑप्टिमाइज़ (Compress) करना
val optimizedVideoUrl = cloudinary.url()
    .resourceType("video")
    .transformation(Transformation().quality("auto").fetchFormat("auto"))
    .generate("social_india/videos/sample_video")
```

## 5. Offline-First आर्किटेक्चर

खराब नेटवर्क में ऐप क्रैश न हो और "Failed to send" एरर कम हो, इसके लिए Firestore Persistence इनेबल्ड है:
```kotlin
// RepositoryManager.kt में पहले से लागू:
val settings = FirebaseFirestoreSettings.Builder()
    .setPersistenceEnabled(true) // Offline caching
    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
    .build()
firestore.firestoreSettings = settings
```
इससे पोस्ट, लाइक्स, और मैसेज लोकल कैशे में सेव हो जाते हैं और नेटवर्क वापस आने पर बैकग्राउंड में अपने-आप सर्वर पर सिंक हो जाते हैं। 
