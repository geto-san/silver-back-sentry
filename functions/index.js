const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const INVALID_TOKEN_ERRORS = new Set([
  "messaging/invalid-registration-token",
  "messaging/registration-token-not-registered",
]);

exports.onSightingCreated = functions.firestore
  .document("sightings/{sightingId}")
  .onCreate(async (snap, context) => {
    const data = snap.data() || {};
    const authorId = data.authorId || null;
    const authorName = data.authorName || "Someone";
    const title = "New Sighting";
    const body = data.title
      ? `${authorName} reported: ${data.title}`
      : `${authorName} reported a new sighting.`;

    await notifyUsersExcludingAuthor(authorId, {
      type: "new_sighting",
      entityId: context.params.sightingId,
      authorId: authorId || "",
      title,
      body,
      timestamp: String(data.timestamp || Date.now()),
    });
  });

exports.onHealthObservationCreated = functions.firestore
  .document("health_observations/{observationId}")
  .onCreate(async (snap, context) => {
    const data = snap.data() || {};
    const authorId = data.authorId || null;
    const authorName = data.authorName || "Someone";
    const title = "Health Observation";
    const body = data.subject
      ? `${authorName} logged: ${data.subject}`
      : `${authorName} logged a health observation.`;

    await notifyUsersExcludingAuthor(authorId, {
      type: "health_observation",
      entityId: context.params.observationId,
      authorId: authorId || "",
      title,
      body,
      timestamp: String(data.timestamp || Date.now()),
    });
  });

async function notifyUsersExcludingAuthor(authorId, dataPayload) {
  const tokenEntries = await collectTokensExcluding(authorId);
  if (tokenEntries.length === 0) {
    return;
  }

  const message = {
    notification: {
      title: dataPayload.title,
      body: dataPayload.body,
    },
    data: {
      type: dataPayload.type,
      entityId: dataPayload.entityId,
      authorId: dataPayload.authorId,
      title: dataPayload.title,
      body: dataPayload.body,
      timestamp: dataPayload.timestamp,
    },
  };

  await sendToTokens(tokenEntries, message);
}

async function collectTokensExcluding(authorId) {
  const usersSnap = await admin.firestore().collection("users").get();
  const tokenEntries = [];

  const tasks = usersSnap.docs.map(async (doc) => {
    if (authorId && doc.id === authorId) {
      return;
    }
    const tokensSnap = await doc.ref.collection("fcmTokens").get();
    tokensSnap.forEach((tokenDoc) => {
      tokenEntries.push({ token: tokenDoc.id, userId: doc.id });
    });
  });

  await Promise.all(tasks);
  return tokenEntries;
}

async function sendToTokens(tokenEntries, message) {
  const chunks = chunk(tokenEntries, 500);
  for (const chunkEntries of chunks) {
    const tokens = chunkEntries.map((entry) => entry.token);
    const response = await admin.messaging().sendEachForMulticast({
      tokens,
      ...message,
    });

    if (response.failureCount > 0) {
      await pruneInvalidTokens(chunkEntries, response.responses);
    }
  }
}

async function pruneInvalidTokens(entries, responses) {
  const deletions = [];
  responses.forEach((resp, idx) => {
    if (resp.success) {
      return;
    }
    const errorCode = resp.error && resp.error.code;
    if (INVALID_TOKEN_ERRORS.has(errorCode)) {
      const entry = entries[idx];
      deletions.push(
        admin
          .firestore()
          .collection("users")
          .doc(entry.userId)
          .collection("fcmTokens")
          .doc(entry.token)
          .delete()
      );
    }
  });
  if (deletions.length > 0) {
    await Promise.all(deletions);
  }
}

function chunk(items, size) {
  const result = [];
  for (let i = 0; i < items.length; i += size) {
    result.push(items.slice(i, i + size));
  }
  return result;
}
