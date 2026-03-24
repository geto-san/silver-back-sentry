package com.sbs.notifications;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public final class FcmTokenManager {

    private static final String TAG = "SbsFCM";
    private static final String PREFS = "sbs_fcm";
    private static final String KEY_TOKEN = "cached_token";

    private FcmTokenManager() {}

    public static void syncCurrentToken(Context context) {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> registerTokenIfPossible(context, token))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to fetch FCM token", e));
    }

    public static void registerTokenIfPossible(Context context, String token) {
        cacheToken(context, token);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || token == null || token.isEmpty()) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("updatedAt", FieldValue.serverTimestamp());
        data.put("platform", "android");

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .collection("fcmTokens")
                .document(token)
                .set(data, SetOptions.merge());
    }

    private static void cacheToken(Context context, String token) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }
}
