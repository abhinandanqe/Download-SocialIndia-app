package com.example.model

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class IndianBank(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val logoUrl: String = "",
    val primaryColor: Long = 0xFF1E3A8A
)

object IndianBanksData {
    val DEFAULT_BANKS = listOf(
        IndianBank(
            id = "sbi",
            name = "State Bank of India (SBI)",
            code = "SBIN",
            logoUrl = "https://images.seeklogo.com/logo-png/33/1/state-bank-of-india-sbi-logo-png_seeklogo-330691.png",
            primaryColor = 0xFF280071
        ),
        IndianBank(
            id = "hdfc",
            name = "HDFC Bank",
            code = "HDFC",
            logoUrl = "https://images.seeklogo.com/logo-png/2/1/hdfc-bank-logo-png_seeklogo-27204.png",
            primaryColor = 0xFF004C8F
        ),
        IndianBank(
            id = "icici",
            name = "ICICI Bank",
            code = "ICIC",
            logoUrl = "https://images.seeklogo.com/logo-png/2/1/icici-bank-logo-png_seeklogo-27206.png",
            primaryColor = 0xFFB7292F
        ),
        IndianBank(
            id = "pnb",
            name = "Punjab National Bank (PNB)",
            code = "PUNB",
            logoUrl = "https://images.seeklogo.com/logo-png/33/1/punjab-national-bank-pnb-logo-png_seeklogo-330678.png",
            primaryColor = 0xFFA1132B
        ),
        IndianBank(
            id = "axis",
            name = "Axis Bank",
            code = "UTIB",
            logoUrl = "https://images.seeklogo.com/logo-png/1/1/axis-bank-logo-png_seeklogo-15632.png",
            primaryColor = 0xFF97144D
        ),
        IndianBank(
            id = "bob",
            name = "Bank of Baroda",
            code = "BARB",
            logoUrl = "https://images.seeklogo.com/logo-png/1/1/bank-of-baroda-logo-png_seeklogo-15989.png",
            primaryColor = 0xFFF26522
        ),
        IndianBank(
            id = "canara",
            name = "Canara Bank",
            code = "CNRB",
            logoUrl = "https://images.seeklogo.com/logo-png/33/1/canara-bank-logo-png_seeklogo-330623.png",
            primaryColor = 0xFF0083CA
        ),
        IndianBank(
            id = "kotak",
            name = "Kotak Mahindra Bank",
            code = "KKBK",
            logoUrl = "https://images.seeklogo.com/logo-png/7/1/kotak-mahindra-bank-logo-png_seeklogo-74673.png",
            primaryColor = 0xFFED1C24
        ),
        IndianBank(
            id = "union",
            name = "Union Bank of India",
            code = "UBIN",
            logoUrl = "https://images.seeklogo.com/logo-png/33/1/union-bank-of-india-logo-png_seeklogo-330713.png",
            primaryColor = 0xFF005691
        ),
        IndianBank(
            id = "indusind",
            name = "IndusInd Bank",
            code = "INDB",
            logoUrl = "https://images.seeklogo.com/logo-png/33/1/indusind-bank-logo-png_seeklogo-330646.png",
            primaryColor = 0xFF8B1D41
        ),
        IndianBank(
            id = "boi",
            name = "Bank of India (BOI)",
            code = "BKID",
            logoUrl = "https://images.seeklogo.com/logo-png/33/1/bank-of-india-boi-logo-png_seeklogo-330617.png",
            primaryColor = 0xFFE05A12
        ),
        IndianBank(
            id = "central_bank",
            name = "Central Bank of India",
            code = "CBIN",
            logoUrl = "https://images.seeklogo.com/logo-png/33/1/central-bank-of-india-logo-png_seeklogo-330627.png",
            primaryColor = 0xFF00457C
        ),
        IndianBank(
            id = "indian_bank",
            name = "Indian Bank",
            code = "IDIB",
            logoUrl = "https://images.seeklogo.com/logo-png/33/1/indian-bank-logo-png_seeklogo-330645.png",
            primaryColor = 0xFF193F7A
        ),
        IndianBank(
            id = "idfc",
            name = "IDFC FIRST Bank",
            code = "IDFB",
            logoUrl = "https://images.seeklogo.com/logo-png/42/1/idfc-first-bank-logo-png_seeklogo-427926.png",
            primaryColor = 0xFF9E1B32
        ),
        IndianBank(
            id = "yes_bank",
            name = "YES Bank",
            code = "YESB",
            logoUrl = "https://images.seeklogo.com/logo-png/33/1/yes-bank-logo-png_seeklogo-330717.png",
            primaryColor = 0xFF003865
        ),
        IndianBank(
            id = "paytm",
            name = "Paytm Payments Bank",
            code = "PYTM",
            logoUrl = "https://images.seeklogo.com/logo-png/30/1/paytm-logo-png_seeklogo-303102.png",
            primaryColor = 0xFF002E6E
        ),
        IndianBank(
            id = "airtel",
            name = "Airtel Payments Bank",
            code = "AIRP",
            logoUrl = "https://images.seeklogo.com/logo-png/30/1/airtel-payments-bank-logo-png_seeklogo-303120.png",
            primaryColor = 0xFFEE1D52
        ),
        IndianBank(
            id = "ippb",
            name = "India Post Payments Bank (IPPB)",
            code = "IPOS",
            logoUrl = "https://images.seeklogo.com/logo-png/33/1/india-post-payments-bank-logo-png_seeklogo-330644.png",
            primaryColor = 0xFFD81B60
        )
    )

    var BANKS = DEFAULT_BANKS

    suspend fun fetchBanks(): List<IndianBank> {
        return try {
            val db = FirebaseFirestore.getInstance()
            val snap = db.collection("admin_banks").get().await()
            if (!snap.isEmpty) {
                BANKS = snap.documents.mapNotNull { doc ->
                    doc.toObject(IndianBank::class.java)?.copy(id = doc.id)
                }
                BANKS
            } else {
                // Batch seed to Firestore instantly in a single network request
                val batch = db.batch()
                for (b in DEFAULT_BANKS) {
                    batch.set(db.collection("admin_banks").document(b.id), b)
                }
                batch.commit().await()
                DEFAULT_BANKS
            }
        } catch (e: Exception) {
            DEFAULT_BANKS
        }
    }
}

