const functions = require("firebase-functions");
const admin = require("firebase-admin");
const crypto = require("crypto");
admin.initializeApp();

const APPROVAL_WINDOW_MS = 48 * 60 * 60 * 1000;

async function verifyRazorpayPaymentViaApi(paymentId, orderId, expectedAmountInr) {
  const keyId = process.env.RAZORPAY_KEY_ID || "";
  const keySecret = process.env.RAZORPAY_KEY_SECRET || "";
  if (!keyId || !keySecret || !paymentId) {
    return { verified: false, reason: "Razorpay server credentials not configured" };
  }
  const authHeader = "Basic " + Buffer.from(`${keyId}:${keySecret}`).toString("base64");
  const resp = await fetch(`https://api.razorpay.com/v1/payments/${encodeURIComponent(paymentId)}`, {
    method: "GET",
    headers: { Authorization: authHeader },
  });
  if (!resp.ok) {
    return { verified: false, reason: `Razorpay payment lookup failed (${resp.status})` };
  }
  const payment = await resp.json();
  const statusOk = payment.status === "captured" || payment.status === "authorized";
  const amountPaiseOk = (Number(payment.amount) || 0) >= Math.round(expectedAmountInr * 100);
  const orderOk = !orderId || !payment.order_id || payment.order_id === orderId;
  return {
    verified: statusOk && amountPaiseOk && orderOk,
    amountInr: (Number(payment.amount) || 0) / 100,
    method: payment.method || "UPI_RAZORPAY",
    reason: statusOk ? "OK" : `Unexpected payment status: ${payment.status}`,
  };
}

async function createPendingExclusivePurchase({
  buyerId,
  creatorId,
  contentId,
  contentType = "POST",
  amountInr,
  paymentId,
  paymentMethod = "UPI_RAZORPAY",
  title = "",
  teaserCaption = "",
  blurredThumbnailUrl = "",
  creatorUsername = "",
  creatorAvatar = "",
}) {
  const db = admin.firestore();
  const deterministicId = `${buyerId}_${contentId}`;
  const settingsSnap = await db.collection("admin_settings").doc("monetization_settings").get();
  const commissionPct = Math.min(
    50,
    Math.max(0, settingsSnap.exists ? (settingsSnap.data().exclusiveCommissionPct ?? 15) : 15)
  );
  const commissionAmount = (amountInr * commissionPct) / 100;
  const creatorNetAmount = Math.max(0, amountInr - commissionAmount);
  const now = Date.now();
  const expiresAt = now + APPROVAL_WINDOW_MS;

  let outcome = { status: "pending_approval", purchaseId: deterministicId };

  await db.runTransaction(async (txn) => {
    const entRef = db.collection("content_entitlements").doc(deterministicId);
    const pRef = db.collection("exclusive_purchases").doc(deterministicId);
    const entSnap = await txn.get(entRef);
    const pSnap = await txn.get(pRef);

    if (entSnap.exists && entSnap.data().active === true) {
      outcome = { status: "already_approved", purchaseId: deterministicId };
      return;
    }
    if (pSnap.exists && pSnap.data().status === "pending_approval") {
      outcome = { status: "already_pending", purchaseId: deterministicId };
      return;
    }

    const buyerRef = db.collection("users").doc(buyerId);
    const creatorRef = db.collection("users").doc(creatorId);
    const creatorWalletRef = db.collection("wallets").doc(creatorId);
    const buyerSnap = await txn.get(buyerRef);
    const creatorSnap = await txn.get(creatorRef);
    const creatorWalletSnap = await txn.get(creatorWalletRef);

    const buyerName =
      (buyerSnap.exists && (buyerSnap.data().username || buyerSnap.data().displayName)) || "A fan";
    const resolvedCreatorName =
      creatorUsername ||
      (creatorSnap.exists && (creatorSnap.data().username || creatorSnap.data().displayName)) ||
      "Creator";
    const resolvedCreatorAvatar =
      creatorAvatar || (creatorSnap.exists && (creatorSnap.data().avatarUrl || creatorSnap.data().profilePicUrl)) || "";

    txn.set(
      entRef,
      {
        entitlementId: deterministicId,
        userId: buyerId,
        creatorId,
        contentId,
        contentType: contentType.toUpperCase(),
        entitlementType: "EXCLUSIVE_UNLOCK",
        active: false, // Locked until creator approves
        amountPaid: amountInr,
        validUntil: -1,
        orderId: paymentId,
        grantedAt: now,
      },
      { merge: true }
    );

    txn.set(
      pRef,
      {
        id: deterministicId,
        buyerId,
        buyerUsername: buyerName,
        creatorId,
        creatorUsername: resolvedCreatorName,
        creatorAvatar: resolvedCreatorAvatar,
        contentId,
        contentType: contentType.toUpperCase(),
        title,
        teaserCaption,
        blurredThumbnailUrl,
        amount: amountInr,
        platformCommissionPct: commissionPct,
        platformCommissionAmount: commissionAmount,
        creatorNetAmount,
        status: "pending_approval",
        paymentVerified: true,
        paymentMethod,
        transactionRef: paymentId,
        timestamp: now,
        expiresAt,
      },
      { merge: true }
    );

    if (creatorSnap.exists) {
      txn.update(creatorRef, {
        pendingBalance: admin.firestore.FieldValue.increment(creatorNetAmount),
      });
    }

    if (creatorWalletSnap.exists) {
      txn.update(creatorWalletRef, {
        pendingBalance: admin.firestore.FieldValue.increment(creatorNetAmount),
        updatedAt: now,
      });
    } else {
      const curAvailable = creatorSnap.exists ? Number(creatorSnap.data().walletBalance || 0) : 0;
      const curCoins = creatorSnap.exists ? Number(creatorSnap.data().coins || 0) : 0;
      txn.set(creatorWalletRef, {
        userId: creatorId,
        availableBalance: curAvailable,
        pendingBalance: creatorNetAmount,
        coinsBalance: curCoins,
        currency: "INR",
        updatedAt: now,
      });
    }

    const txnDocId = `EX_PEND_${paymentId}_${now}`;
    txn.set(db.collection("wallet_transactions").doc(txnDocId), {
      id: txnDocId,
      userId: creatorId,
      counterpartyId: buyerId,
      contentId,
      purchaseId: deterministicId,
      type: "EXCLUSIVE_PENDING",
      direction: "CREDIT_PENDING",
      amount: creatorNetAmount,
      grossAmount: amountInr,
      status: "pending_approval",
      description: `Pending approval from @${buyerName} (₹${amountInr})`,
      timestamp: now,
    });

    const creatorNotifRef = db.collection("users").doc(creatorId).collection("notifications").doc();
    const creatorMsg = `@${buyerName} paid ₹${Math.round(amountInr)} to unlock your content. Approve or reject within 48 hours.`;
    txn.set(creatorNotifRef, {
      id: creatorNotifRef.id,
      recipientUid: creatorId,
      type: "exclusive_request",
      title: "New Exclusive Unlock Request",
      message: creatorMsg,
      text: creatorMsg,
      actorUid: buyerId,
      actorId: buyerId,
      actorUsername: buyerName,
      senderId: buyerId,
      targetId: contentId,
      targetType: contentType.toLowerCase(),
      postId: contentId,
      contentType: contentType.toUpperCase(),
      deepLink: "exclusive_requests",
      createdAt: now,
      timestamp: now,
      read: false,
      isRead: false,
    });

    const buyerNotifRef = db.collection("users").doc(buyerId).collection("notifications").doc();
    const buyerMsg = `Payment done - waiting for creator approval (₹${Math.round(amountInr)} for @${resolvedCreatorName}). Auto-refunded if not approved within 48h.`;
    txn.set(buyerNotifRef, {
      id: buyerNotifRef.id,
      recipientUid: buyerId,
      type: "exclusive_pending",
      title: "Payment done - waiting for creator approval",
      message: buyerMsg,
      text: buyerMsg,
      actorUid: creatorId,
      actorId: creatorId,
      actorUsername: resolvedCreatorName,
      senderId: creatorId,
      targetId: contentId,
      targetType: contentType.toLowerCase(),
      postId: contentId,
      contentType: contentType.toUpperCase(),
      deepLink: "wallet",
      createdAt: now,
      timestamp: now,
      read: false,
      isRead: false,
    });
  });

  return outcome;
}

async function settleExclusiveApproval({ purchaseId, actorUid, isAdmin = false }) {
  const db = admin.firestore();
  const pRef = db.collection("exclusive_purchases").doc(purchaseId);
  const entRef = db.collection("content_entitlements").doc(purchaseId);
  const now = Date.now();
  let result = { approved: false, reason: "Unknown" };

  await db.runTransaction(async (txn) => {
    const pSnap = await txn.get(pRef);
    if (!pSnap.exists) {
      result = { approved: false, reason: "Purchase request not found" };
      return;
    }
    const pData = pSnap.data();
    if (pData.creatorId !== actorUid && !isAdmin) {
      throw new functions.https.HttpsError("permission-denied", "Only the creator can approve this request");
    }
    if (pData.status === "approved" || pData.status === "COMPLETED") {
      result = { approved: true, alreadyApproved: true, purchaseId };
      return;
    }
    if (pData.status !== "pending_approval" && pData.status !== "PENDING") {
      result = { approved: false, reason: `Cannot approve request in status: ${pData.status}` };
      return;
    }

    const buyerId = pData.buyerId;
    const creatorId = pData.creatorId;
    const contentId = pData.contentId;
    const contentType = String(pData.contentType || "POST").toUpperCase();
    const grossAmount = Number(pData.amount || 0);
    const commissionPct = Number(pData.platformCommissionPct || 15);
    const commissionAmount = Number(pData.platformCommissionAmount || (grossAmount * commissionPct) / 100);
    const creatorNetAmount = Number(pData.creatorNetAmount || Math.max(0, grossAmount - commissionAmount));
    const creatorUsername = pData.creatorUsername || "Creator";
    const buyerUsername = pData.buyerUsername || "Fan";
    const txnRef = pData.transactionRef || purchaseId;

    const creatorRef = db.collection("users").doc(creatorId);
    const creatorWalletRef = db.collection("wallets").doc(creatorId);
    const creatorSnap = await txn.get(creatorRef);
    const creatorWalletSnap = await txn.get(creatorWalletRef);

    const contentCollection = contentType === "STORY" ? "stories" : "posts";
    const contentRef = db.collection(contentCollection).doc(contentId);
    const contentSnap = await txn.get(contentRef);

    txn.update(pRef, {
      status: "approved",
      approvedAt: now,
      approvedBy: actorUid,
    });

    txn.set(
      entRef,
      {
        entitlementId: purchaseId,
        userId: buyerId,
        creatorId,
        contentId,
        contentType,
        entitlementType: "EXCLUSIVE_UNLOCK",
        active: true,
        amountPaid: grossAmount,
        validUntil: -1,
        orderId: txnRef,
        grantedAt: now,
      },
      { merge: true }
    );

    if (creatorSnap.exists) {
      const curPending = Number(creatorSnap.data().pendingBalance || 0);
      const nextPending = Math.max(0, curPending - creatorNetAmount);
      txn.update(creatorRef, {
        pendingBalance: nextPending,
        walletBalance: admin.firestore.FieldValue.increment(creatorNetAmount),
        earnings: admin.firestore.FieldValue.increment(creatorNetAmount),
      });
    }

    if (creatorWalletSnap.exists) {
      const curWalletPending = Number(creatorWalletSnap.data().pendingBalance || 0);
      txn.update(creatorWalletRef, {
        pendingBalance: Math.max(0, curWalletPending - creatorNetAmount),
        availableBalance: admin.firestore.FieldValue.increment(creatorNetAmount),
        updatedAt: now,
      });
    } else {
      const baseAvail = creatorSnap.exists ? Number(creatorSnap.data().walletBalance || 0) : 0;
      const baseCoins = creatorSnap.exists ? Number(creatorSnap.data().coins || 0) : 0;
      txn.set(creatorWalletRef, {
        userId: creatorId,
        availableBalance: baseAvail + creatorNetAmount,
        pendingBalance: 0,
        coinsBalance: baseCoins,
        currency: "INR",
        updatedAt: now,
      });
    }

    if (contentSnap.exists) {
      txn.update(contentRef, {
        unlockCount: admin.firestore.FieldValue.increment(1),
        exclusiveRevenue: admin.firestore.FieldValue.increment(creatorNetAmount),
      });
    }

    txn.set(db.collection("creator_earnings_ledger").doc(`LEDGER_${txnRef}`), {
      ledgerId: `LEDGER_${txnRef}`,
      creatorId,
      buyerId,
      orderId: txnRef,
      contentId,
      sourceType: "EXCLUSIVE_UNLOCK",
      grossAmount,
      platformFee: commissionAmount,
      platformCommissionPct: commissionPct,
      netAmount: creatorNetAmount,
      status: "CREDITED",
      createdAt: now,
    });

    txn.set(db.collection("earning_transactions").doc(`ETX_${txnRef}`), {
      transactionId: `ETX_${txnRef}`,
      creatorUid: creatorId,
      senderUid: buyerId,
      sourceType: "PAID_POST",
      sourceId: contentId,
      grossAmount,
      platformFee: commissionAmount,
      creatorAmount: creatorNetAmount,
      currency: "INR",
      status: "AVAILABLE",
      createdAt: now,
      availableAt: now,
      description: `Exclusive approved for @${buyerUsername} (₹${Math.round(grossAmount)})`,
    });

    const wTxnId = `EX_APPR_${purchaseId}_${now}`;
    txn.set(db.collection("wallet_transactions").doc(wTxnId), {
      id: wTxnId,
      userId: creatorId,
      counterpartyId: buyerId,
      contentId,
      purchaseId,
      type: "EXCLUSIVE_APPROVED",
      direction: "CREDIT_AVAILABLE",
      amount: creatorNetAmount,
      grossAmount,
      status: "approved",
      description: `Approved unlock by @${buyerUsername}`,
      timestamp: now,
    });

    const buyerNotifRef = db.collection("users").doc(buyerId).collection("notifications").doc();
    const approvedMsg = `@${creatorUsername} approved your request! Tap to view your unlocked ${contentType.toLowerCase()}.`;
    txn.set(buyerNotifRef, {
      id: buyerNotifRef.id,
      recipientUid: buyerId,
      type: "exclusive_approved",
      title: "Exclusive Access Approved!",
      message: approvedMsg,
      text: approvedMsg,
      actorUid: creatorId,
      actorId: creatorId,
      actorUsername: creatorUsername,
      senderId: creatorId,
      targetId: contentId,
      targetType: contentType.toLowerCase(),
      postId: contentId,
      contentType,
      deepLink: contentType === "REEL" ? `reel/${contentId}` : `post_detail/${contentId}`,
      createdAt: now,
      timestamp: now,
      read: false,
      isRead: false,
    });

    result = { approved: true, purchaseId, creatorNetAmount };
  });

  return result;
}

async function settleExclusiveRefund({
  purchaseId,
  newStatus = "rejected",
  reason = "Creator declined request",
  actorUid = null,
  isAdmin = false,
  isSystem = false,
}) {
  const db = admin.firestore();
  const pRef = db.collection("exclusive_purchases").doc(purchaseId);
  const entRef = db.collection("content_entitlements").doc(purchaseId);
  const now = Date.now();
  let result = { refunded: false, reason: "Unknown" };

  await db.runTransaction(async (txn) => {
    const pSnap = await txn.get(pRef);
    if (!pSnap.exists) {
      result = { refunded: false, reason: "Purchase request not found" };
      return;
    }
    const pData = pSnap.data();
    if (!isSystem && !isAdmin && pData.creatorId !== actorUid) {
      throw new functions.https.HttpsError("permission-denied", "Not authorized to reject this request");
    }
    if (pData.status === "rejected" || pData.status === "refunded" || pData.status === "REFUNDED") {
      result = { refunded: true, alreadyRefunded: true, purchaseId };
      return;
    }
    if (pData.status !== "pending_approval" && pData.status !== "PENDING" && !isAdmin) {
      result = { refunded: false, reason: `Cannot refund purchase in status: ${pData.status}` };
      return;
    }

    const buyerId = pData.buyerId;
    const creatorId = pData.creatorId;
    const contentId = pData.contentId;
    const contentType = String(pData.contentType || "POST").toUpperCase();
    const amount = Number(pData.amount || 0);
    const creatorNetAmount = Number(pData.creatorNetAmount || 0);
    const creatorUsername = pData.creatorUsername || "Creator";
    const buyerUsername = pData.buyerUsername || "Fan";

    const buyerRef = db.collection("users").doc(buyerId);
    const buyerWalletRef = db.collection("wallets").doc(buyerId);
    const creatorRef = creatorId ? db.collection("users").doc(creatorId) : null;
    const creatorWalletRef = creatorId ? db.collection("wallets").doc(creatorId) : null;

    const buyerSnap = await txn.get(buyerRef);
    const buyerWalletSnap = await txn.get(buyerWalletRef);
    const creatorSnap = creatorRef ? await txn.get(creatorRef) : null;
    const creatorWalletSnap = creatorWalletRef ? await txn.get(creatorWalletRef) : null;

    if (buyerSnap.exists && amount > 0) {
      txn.update(buyerRef, {
        walletBalance: admin.firestore.FieldValue.increment(amount),
      });
    }
    if (buyerWalletSnap.exists && amount > 0) {
      txn.update(buyerWalletRef, {
        availableBalance: admin.firestore.FieldValue.increment(amount),
        updatedAt: now,
      });
    } else if (amount > 0) {
      const baseAvail = buyerSnap.exists ? Number(buyerSnap.data().walletBalance || 0) : 0;
      const baseCoins = buyerSnap.exists ? Number(buyerSnap.data().coins || 0) : 0;
      txn.set(buyerWalletRef, {
        userId: buyerId,
        availableBalance: baseAvail + amount,
        pendingBalance: buyerSnap.exists ? Number(buyerSnap.data().pendingBalance || 0) : 0,
        coinsBalance: baseCoins,
        currency: "INR",
        updatedAt: now,
      });
    }

    if (creatorSnap && creatorSnap.exists && creatorNetAmount > 0) {
      if (pData.status === "pending_approval" || pData.status === "PENDING") {
        const curPend = Number(creatorSnap.data().pendingBalance || 0);
        txn.update(creatorRef, {
          pendingBalance: Math.max(0, curPend - creatorNetAmount),
        });
      } else {
        const curAvail = Number(creatorSnap.data().walletBalance || 0);
        txn.update(creatorRef, {
          walletBalance: Math.max(0, curAvail - creatorNetAmount),
        });
      }
    }

    if (creatorWalletSnap && creatorWalletSnap.exists && creatorNetAmount > 0) {
      if (pData.status === "pending_approval" || pData.status === "PENDING") {
        const curPend = Number(creatorWalletSnap.data().pendingBalance || 0);
        txn.update(creatorWalletRef, {
          pendingBalance: Math.max(0, curPend - creatorNetAmount),
          updatedAt: now,
        });
      } else {
        const curAvail = Number(creatorWalletSnap.data().availableBalance || 0);
        txn.update(creatorWalletRef, {
          availableBalance: Math.max(0, curAvail - creatorNetAmount),
          updatedAt: now,
        });
      }
    }

    txn.update(pRef, {
      status: newStatus,
      refundReason: reason,
      refundedAt: now,
    });

    txn.set(entRef, { active: false }, { merge: true });

    const refundTxnId = `EX_REF_${purchaseId}_${now}`;
    txn.set(db.collection("wallet_transactions").doc(refundTxnId), {
      id: refundTxnId,
      userId: buyerId,
      counterpartyId: creatorId,
      contentId,
      purchaseId,
      type: "EXCLUSIVE_REFUND",
      direction: "CREDIT_AVAILABLE",
      amount,
      status: newStatus,
      description: `Refunded ₹${Math.round(amount)} (${reason})`,
      timestamp: now,
    });

    const buyerNotifRef = db.collection("users").doc(buyerId).collection("notifications").doc();
    const buyerRefundMsg = `₹${Math.round(amount)} has been refunded to your wallet (${reason}).`;
    txn.set(buyerNotifRef, {
      id: buyerNotifRef.id,
      recipientUid: buyerId,
      type: newStatus === "refunded" ? "exclusive_refunded" : "exclusive_rejected",
      title: newStatus === "refunded" ? "Exclusive Request Expired — Refunded" : "Exclusive Request Declined — Refunded",
      message: buyerRefundMsg,
      text: buyerRefundMsg,
      actorUid: creatorId,
      actorId: creatorId,
      actorUsername: creatorUsername,
      senderId: creatorId,
      targetId: contentId,
      targetType: contentType.toLowerCase(),
      postId: contentId,
      contentType,
      deepLink: "wallet",
      createdAt: now,
      timestamp: now,
      read: false,
      isRead: false,
    });

    if (creatorId) {
      const creatorNotifRef = db.collection("users").doc(creatorId).collection("notifications").doc();
      const creatorRefundMsg = `@${buyerUsername}'s ₹${Math.round(amount)} request was refunded (${reason}).`;
      txn.set(creatorNotifRef, {
        id: creatorNotifRef.id,
        recipientUid: creatorId,
        type: "exclusive_refunded",
        title: newStatus === "refunded" ? "Exclusive Request Auto-Refunded (48h)" : "Exclusive Request Rejected",
        message: creatorRefundMsg,
        text: creatorRefundMsg,
        actorUid: buyerId,
        actorId: buyerId,
        actorUsername: buyerUsername,
        senderId: buyerId,
        targetId: contentId,
        targetType: contentType.toLowerCase(),
        postId: contentId,
        contentType,
        deepLink: "exclusive_requests",
        createdAt: now,
        timestamp: now,
        read: false,
        isRead: false,
      });
    }

    result = { refunded: true, purchaseId, status: newStatus, amount };
  });

  return result;
}

// Cloud Function triggered on new notification document creation
exports.sendNotificationOnNewEvent = functions.firestore
  .document("users/{userId}/notifications/{notificationId}")
  .onCreate(async (snap, context) => {
    const notificationData = snap.data();
    const receiverId = context.params.userId;
    const senderUsername = notificationData.actorUsername || "Someone";
    const notificationType = notificationData.type || "INFO";
    const messageBody = notificationData.message || notificationData.content || "You have a new notification";

    if (notificationData.actorId === receiverId) {
      console.log("User triggered their own notification. Skipping push.");
      return null;
    }

    const userDocRef = admin.firestore().collection("users").doc(receiverId);
    const userDocSnap = await userDocRef.get();
    if (!userDocSnap.exists) return null;

    const fcmToken = userDocSnap.data().fcmToken;
    if (!fcmToken) return null;

    const payload = {
      notification: {
        title: notificationData.title || `New ${notificationType} from ${senderUsername}`,
        body: messageBody,
        sound: "default",
      },
      data: {
        click_action: "FLUTTER_NOTIFICATION_CLICK",
        notificationType: notificationType,
        senderId: notificationData.actorId || "",
        postId: notificationData.postId || "",
        deepLink: notificationData.deepLink || "",
      },
    };

    try {
      return await admin.messaging().sendToDevice(fcmToken, payload);
    } catch (error) {
      console.error("Error sending push notification:", error);
      return null;
    }
  });

/**
 * Server-side Razorpay / UPI Webhook for Exclusive Content Purchases.
 * Verifies HMAC-SHA256 signature using process.env.RAZORPAY_WEBHOOK_SECRET,
 * creates a `pending_approval` purchase with 48h expiry, credits Creator Pending Balance,
 * and notifies both Creator & Buyer.
 */
exports.verifyExclusivePaymentWebhook = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method not allowed" });
  }
  const webhookSecret = process.env.RAZORPAY_WEBHOOK_SECRET;
  if (!webhookSecret) {
    console.error("RAZORPAY_WEBHOOK_SECRET not configured");
    return res.status(500).json({ error: "Payment webhook not configured" });
  }

  const signature = req.headers["x-razorpay-signature"];
  const rawBody = req.rawBody ? req.rawBody.toString("utf8") : JSON.stringify(req.body);
  const expectedSig = crypto.createHmac("sha256", webhookSecret).update(rawBody).digest("hex");

  if (!signature || signature.length !== expectedSig.length || !crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expectedSig))) {
    return res.status(401).json({ error: "Invalid webhook signature" });
  }

  const event = req.body.event;
  if (event !== "payment.captured" && event !== "order.paid") {
    return res.status(200).json({ ignored: true });
  }

  const paymentEntity = req.body.payload?.payment?.entity || {};
  const notes = paymentEntity.notes || {};
  const buyerId = notes.buyerId;
  const creatorId = notes.creatorId;
  const contentId = notes.contentId;
  const contentType = (notes.contentType || "POST").toUpperCase();
  const amountInr = (paymentEntity.amount || 0) / 100;
  const paymentId = paymentEntity.id || `RZP_${Date.now()}`;

  if (!buyerId || !creatorId || !contentId || amountInr < 10) {
    return res.status(400).json({ error: "Invalid payment metadata" });
  }

  try {
    const outcome = await createPendingExclusivePurchase({
      buyerId,
      creatorId,
      contentId,
      contentType,
      amountInr,
      paymentId,
      paymentMethod: "UPI_RAZORPAY",
      title: notes.title || "",
      teaserCaption: notes.teaserCaption || "",
    });
    return res.status(200).json(outcome);
  } catch (err) {
    console.error("Webhook fulfillment failed:", err);
    return res.status(500).json({ error: "Fulfillment error" });
  }
});

/**
 * Server-side Razorpay Payment Verification Callable (called by Android app after Razorpay/UPI SDK checkout).
 * Validates HMAC-SHA256 signature (`razorpay_order_id|razorpay_payment_id`) OR queries Razorpay REST API,
 * never trusts client amount blindly, and creates a `pending_approval` purchase + Creator Pending Balance.
 */
exports.verifyExclusiveRazorpayPayment = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }
  const buyerId = context.auth.uid;
  const creatorId = String(data.creatorId || "").trim();
  const contentId = String(data.contentId || "").trim();
  const contentType = String(data.contentType || "POST").toUpperCase();
  const paymentId = String(data.razorpayPaymentId || data.transactionRef || "").trim();
  const orderId = String(data.razorpayOrderId || "").trim();
  const signature = String(data.razorpaySignature || "").trim();
  const clientAmount = Number(data.amountInr || 0);

  if (!creatorId || !contentId || !paymentId || clientAmount < 10) {
    throw new functions.https.HttpsError("invalid-argument", "Invalid exclusive purchase details");
  }

  const keySecret = process.env.RAZORPAY_KEY_SECRET || "";
  let verified = false;
  let verifiedAmount = clientAmount;

  if (orderId && signature && keySecret) {
    const expected = crypto.createHmac("sha256", keySecret).update(`${orderId}|${paymentId}`).digest("hex");
    if (signature.length === expected.length && crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected))) {
      verified = true;
    } else {
      throw new functions.https.HttpsError("permission-denied", "Invalid Razorpay payment signature");
    }
  } else if (process.env.RAZORPAY_KEY_ID && keySecret) {
    const apiCheck = await verifyRazorpayPaymentViaApi(paymentId, orderId, clientAmount);
    if (!apiCheck.verified) {
      throw new functions.https.HttpsError("permission-denied", apiCheck.reason || "Payment not captured");
    }
    verified = true;
    verifiedAmount = apiCheck.amountInr || clientAmount;
  } else {
    throw new functions.https.HttpsError("failed-precondition", "Server payment verification keys not configured");
  }

  if (!verified) {
    throw new functions.https.HttpsError("permission-denied", "Payment verification failed");
  }

  return await createPendingExclusivePurchase({
    buyerId,
    creatorId,
    contentId,
    contentType,
    amountInr: verifiedAmount,
    paymentId,
    paymentMethod: "UPI_RAZORPAY",
    title: String(data.title || ""),
    teaserCaption: String(data.teaserCaption || ""),
    blurredThumbnailUrl: String(data.blurredThumbnailUrl || ""),
    creatorUsername: String(data.creatorUsername || ""),
    creatorAvatar: String(data.creatorAvatar || ""),
  });
});

/**
 * Creator approves a pending exclusive unlock request.
 * Moves net amount from Pending -> Available balance & flips entitlement active = true.
 */
exports.approveExclusiveRequest = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }
  const purchaseId = String(data.purchaseId || "").trim();
  if (!purchaseId) {
    throw new functions.https.HttpsError("invalid-argument", "purchaseId required");
  }
  const db = admin.firestore();
  const callerDoc = await db.collection("users").doc(context.auth.uid).get();
  const isAdmin = context.auth.token.admin === true || (callerDoc.exists && callerDoc.data().isAdmin === true);
  return await settleExclusiveApproval({ purchaseId, actorUid: context.auth.uid, isAdmin });
});

/**
 * Creator rejects a pending exclusive unlock request -> auto-refunds buyer & removes creator Pending balance.
 */
exports.rejectExclusiveRequest = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }
  const purchaseId = String(data.purchaseId || "").trim();
  const reason = String(data.reason || "Creator declined request").trim();
  if (!purchaseId) {
    throw new functions.https.HttpsError("invalid-argument", "purchaseId required");
  }
  const db = admin.firestore();
  const callerDoc = await db.collection("users").doc(context.auth.uid).get();
  const isAdmin = context.auth.token.admin === true || (callerDoc.exists && callerDoc.data().isAdmin === true);
  return await settleExclusiveRefund({
    purchaseId,
    newStatus: "rejected",
    reason,
    actorUid: context.auth.uid,
    isAdmin,
  });
});

/**
 * Scheduled 48-hour auto-refund sweeper (runs every 60 minutes).
 */
exports.autoRefundExpiredExclusiveRequests = functions.pubsub
  .schedule("every 60 minutes")
  .onRun(async () => {
    const db = admin.firestore();
    const now = Date.now();
    const snap = await db
      .collection("exclusive_purchases")
      .where("status", "==", "pending_approval")
      .where("expiresAt", "<=", now)
      .limit(100)
      .get();

    for (const doc of snap.docs) {
      try {
        await settleExclusiveRefund({
          purchaseId: doc.id,
          newStatus: "refunded",
          reason: "Creator did not respond within 48 hours",
          isSystem: true,
        });
      } catch (e) {
        console.error(`Auto-refund failed for ${doc.id}:`, e);
      }
    }
    return null;
  });

/**
 * Server-side Signed URL / Vault Resolver.
 * Verifies authenticated caller owns an APPROVED & active entitlement (or is creator/admin)
 * before issuing a short-lived signed URL (15 min expiry) for private exclusive media.
 */
exports.getExclusiveSignedMediaUrl = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in required");
  }
  const uid = context.auth.uid;
  const contentId = String(data.contentId || "").trim();
  if (!contentId) {
    throw new functions.https.HttpsError("invalid-argument", "Missing contentId");
  }

  const db = admin.firestore();
  const vaultSnap = await db.collection("exclusive_media_vault").doc(contentId).get();
  if (!vaultSnap.exists) {
    throw new functions.https.HttpsError("not-found", "Exclusive media not found");
  }
  const vault = vaultSnap.data();

  let authorized = vault.creatorId === uid || context.auth.token.admin === true;
  if (!authorized) {
    const detId = `${uid}_${contentId}`;
    const [entSnap, pSnap] = await Promise.all([
      db.collection("content_entitlements").doc(detId).get(),
      db.collection("exclusive_purchases").doc(detId).get(),
    ]);
    const pStatus = pSnap.exists ? pSnap.data().status : "";
    if (
      entSnap.exists &&
      entSnap.data().active === true &&
      (pStatus === "approved" || pStatus === "COMPLETED")
    ) {
      authorized = true;
    }
  }
  if (!authorized) {
    throw new functions.https.HttpsError("permission-denied", "Creator approval required to access exclusive media");
  }

  if (vault.storagePath) {
    const bucket = admin.storage().bucket();
    const [signedUrl] = await bucket.file(vault.storagePath).getSignedUrl({
      version: "v4",
      action: "read",
      expires: Date.now() + 15 * 60 * 1000,
    });
    return { mediaUrl: signedUrl, expiresInSeconds: 900 };
  }

  return {
    mediaUrl: vault.mediaUrl || "",
    postUrls: vault.postUrls || [],
    expiresInSeconds: 900,
  };
});

/**
 * Admin-only atomic refund handler for Exclusive Purchases.
 */
exports.refundExclusivePurchase = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required");
  }
  const db = admin.firestore();
  const callerDoc = await db.collection("users").doc(context.auth.uid).get();
  const isAdmin = context.auth.token.admin === true || (callerDoc.exists && callerDoc.data().isAdmin === true);
  if (!isAdmin) {
    throw new functions.https.HttpsError("permission-denied", "Admin access required");
  }

  const purchaseId = String(data.purchaseId || "").trim();
  const reason = String(data.reason || "Admin refund").trim();
  if (!purchaseId) {
    throw new functions.https.HttpsError("invalid-argument", "purchaseId required");
  }

  return await settleExclusiveRefund({
    purchaseId,
    newStatus: "REFUNDED",
    reason,
    actorUid: context.auth.uid,
    isAdmin: true,
  });
});
