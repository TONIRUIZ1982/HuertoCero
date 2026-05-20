const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { setGlobalOptions } = require("firebase-functions/v2");
const admin = require("firebase-admin");

admin.initializeApp();
setGlobalOptions({ region: "europe-southwest1", maxInstances: 10 });

const db = admin.firestore();

function geoCell(lat, lng) {
  return `${Math.floor(lat * 10)}_${Math.floor(lng * 10)}`;
}

function distanceKm(aLat, aLng, bLat, bLng) {
  const radius = 6371;
  const dLat = ((bLat - aLat) * Math.PI) / 180;
  const dLng = ((bLng - aLng) * Math.PI) / 180;
  const lat1 = (aLat * Math.PI) / 180;
  const lat2 = (bLat * Math.PI) / 180;
  const h =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
  return 2 * radius * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

function matchesSavedInterest(profile, product) {
  const categoryAlerts = profile.categoryAlerts || [];
  const sellerAlerts = profile.sellerAlerts || [];
  const terms = profile.savedSearchTerms || [];
  const category = product.category || "";
  const sellerId = product.sellerId || "";
  const searchable = `${product.name || ""} ${product.description || ""} ${category}`.toLowerCase();

  if (sellerId && sellerAlerts.includes(sellerId)) return true;
  if (category && categoryAlerts.includes(category)) return true;
  if (terms.some((term) => term && searchable.includes(String(term).toLowerCase()))) return true;

  return !profile.onlySavedAlerts;
}

exports.notifyNearbyProduct = onDocumentCreated("products/{productId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) return null;

  const product = snapshot.data() || {};
  const lat = Number(product.lat);
  const lng = Number(product.lng);
  const sellerId = product.sellerId || "";

  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;

  const cell = product.geoCell || geoCell(lat, lng);
  const profiles = await db
    .collection("notificationProfiles")
    .where("geoCells", "array-contains", cell)
    .limit(500)
    .get();

  const messages = [];
  const messageProfileRefs = [];
  profiles.forEach((doc) => {
    const profile = doc.data() || {};
    const token = profile.fcmToken;
    const userLat = Number(profile.lat);
    const userLng = Number(profile.lng);
    const radiusKm = Number(profile.alertRadiusKm || 8);

    if (!token || doc.id === sellerId) return;
    if (!Number.isFinite(userLat) || !Number.isFinite(userLng)) return;

    const km = distanceKm(userLat, userLng, lat, lng);
    if (km > radiusKm) return;
    if (!matchesSavedInterest(profile, product)) return;

    const productName = product.name || "Producto nuevo";
    messages.push({
      token,
      notification: {
        title: "Nuevo producto cerca",
        body: `${productName} a ${km.toFixed(1)} km de ti`
      },
      data: {
        type: "nearby_product",
        productId: event.params.productId
      },
      android: {
        priority: "high",
        notification: {
          channelId: "nearby_products",
          clickAction: "OPEN_PRODUCT"
        }
      }
    });
    messageProfileRefs.push(doc.ref);
  });

  if (messages.length === 0) return null;

  const response = await admin.messaging().sendEach(messages);
  const cleanup = [];
  response.responses.forEach((result, index) => {
    if (!result.success) {
      const code = result.error && result.error.code;
      if (code === "messaging/registration-token-not-registered") {
        cleanup.push(messageProfileRefs[index].update({ fcmToken: admin.firestore.FieldValue.delete() }));
      }
    }
  });
  await Promise.all(cleanup);
  return null;
});

async function releaseExpiredReservation(reservationRef, nowMillis) {
  return db.runTransaction(async (transaction) => {
    const reservationSnapshot = await transaction.get(reservationRef);
    if (!reservationSnapshot.exists) return;

    const reservation = reservationSnapshot.data() || {};
    const expiresAt = reservation.expiresAt;
    const expiresAtMillis = expiresAt && typeof expiresAt.toMillis === "function"
      ? expiresAt.toMillis()
      : 0;

    if (reservation.status && reservation.status !== "held") return;
    if (expiresAtMillis > nowMillis) return;

    const productId = reservation.productId || "";
    const quantity = Number(reservation.quantity || 0);

    if (productId && quantity > 0) {
      const productRef = db.collection("products").doc(productId);
      const productSnapshot = await transaction.get(productRef);

      if (productSnapshot.exists) {
        const currentReserved = Number(productSnapshot.get("stockReserved") || 0);
        transaction.update(productRef, {
          stockReserved: Math.max(0, currentReserved - quantity)
        });
      }
    }

    transaction.delete(reservationRef);
  });
}

exports.releaseExpiredReservations = onSchedule(
  {
    region: "europe-west1",
    schedule: "every 5 minutes",
    timeZone: "Europe/Madrid"
  },
  async () => {
    const now = admin.firestore.Timestamp.now();
    const expiredReservations = await db
      .collection("reservas")
      .where("expiresAt", "<=", now)
      .limit(100)
      .get();

    await Promise.all(
      expiredReservations.docs.map((doc) =>
        releaseExpiredReservation(doc.ref, now.toMillis())
      )
    );

    return null;
  }
);
