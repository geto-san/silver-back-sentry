package com.sbs.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;

/**
 * AccountSlotManager
 *
 * Manages persistent storage for exactly 2 ranger account profiles on a single
 * device, mirroring the WhatsApp multi-account model.
 *
 * Storage backend: SharedPreferences ("sbs_account_slots").
 * Each slot stores: uid, displayName, email, photoUrl.
 * One additional key tracks which slot is currently the active (signed-in) one.
 *
 * ── Slot assignment rules ────────────────────────────────────────────────────
 *
 *   registerCurrentUser(FirebaseUser) is the single entry point called after
 *   every successful Firebase Auth login.  It applies the following logic:
 *
 *     1. If the user's UID already exists in slot 1 → update slot 1, mark active.
 *     2. If the user's UID already exists in slot 2 → update slot 2, mark active.
 *     3. If the UID is new AND slot 1 is empty      → assign to slot 1.
 *     4. If the UID is new AND slot 1 is taken
 *                          AND slot 2 is empty      → assign to slot 2.
 *     5. Both slots occupied by different UIDs      → return -1 (capacity full).
 *        This case is prevented in the UI by hiding the "Add" chip when full.
 *
 * ── Strict 2-account limit ────────────────────────────────────────────────────
 *
 *   isAtMaxCapacity() → true when both slots are populated.
 *   The "Add account" chip in the drawer is hidden when this returns true.
 *   registerCurrentUser() returns -1 and makes no changes when called with a
 *   third, unknown UID while both slots are already occupied.
 *
 * ── Removal ──────────────────────────────────────────────────────────────────
 *
 *   clearSlot(int slot) wipes a single slot's data.  The active slot pointer is
 *   automatically adjusted to point to the remaining slot when the cleared slot
 *   was the active one.  The caller (DashboardActivity) must ensure the user is
 *   not trying to clear the currently signed-in slot (enforced in the UI by
 *   requiring the inactive slot to be cleared first).
 *
 *   clearAll() wipes both slots and resets the active slot pointer to 0
 *   (undefined).  Called on a full "Log Out" to restore a clean state.
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 *
 *   The singleton is initialised with double-checked locking.
 *   All SharedPreferences writes use apply() (asynchronous) which is safe from
 *   any thread.  Individual reads are atomic for the types used (String, int).
 *   No additional synchronisation is required for this use-case because slot
 *   mutations always originate from the main thread (UI or post-login callbacks).
 *
 * Usage example
 * ─────────────
 *   // After a successful Firebase login:
 *   int slot = AccountSlotManager.getInstance(context)
 *                  .registerCurrentUser(FirebaseAuth.getInstance().getCurrentUser());
 *
 *   // In the drawer header:
 *   AccountProfile active   = manager.getActiveProfile();
 *   AccountProfile inactive = manager.getInactiveProfile();
 *   boolean showAddChip     = !manager.isAtMaxCapacity();
 */
public final class AccountSlotManager {

    // ── SharedPreferences name ────────────────────────────────────────────────

    private static final String PREFS_NAME = "sbs_account_slots";

    // ── Slot 1 preference keys ────────────────────────────────────────────────

    private static final String KEY_S1_UID   = "s1_uid";
    private static final String KEY_S1_NAME  = "s1_name";
    private static final String KEY_S1_EMAIL = "s1_email";
    private static final String KEY_S1_PHOTO = "s1_photo";

    // ── Slot 2 preference keys ────────────────────────────────────────────────

    private static final String KEY_S2_UID   = "s2_uid";
    private static final String KEY_S2_NAME  = "s2_name";
    private static final String KEY_S2_EMAIL = "s2_email";
    private static final String KEY_S2_PHOTO = "s2_photo";

    // ── Provider type keys (google / email) ──────────────────────────────────
    private static final String KEY_S1_PROVIDER = "s1_provider";
    private static final String KEY_S2_PROVIDER = "s2_provider";

    public static final String PROVIDER_GOOGLE = "google";
    public static final String PROVIDER_EMAIL  = "email";

    // ── Active slot pointer ───────────────────────────────────────────────────

    /**
     * Stores the currently active slot number (1 or 2).
     * 0 means neither slot is active (freshly cleared state).
     */
    private static final String KEY_ACTIVE_SLOT = "active_slot";

    // ── Maximum number of accounts allowed on one device ─────────────────────

    public static final int MAX_ACCOUNTS = 2;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile AccountSlotManager instance;

    private final SharedPreferences prefs;

    private AccountSlotManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Returns the application-scoped singleton instance.
     * Safe to call from any thread.
     */
    public static AccountSlotManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AccountSlotManager.class) {
                if (instance == null) {
                    instance = new AccountSlotManager(context);
                }
            }
        }
        return instance;
    }

    // ── Core registration ─────────────────────────────────────────────────────

    /**
     * Registers (or updates) a Firebase user in the appropriate slot.
     *
     * This is the ONLY method that assigns users to slots.  Call it
     * immediately after every successful Firebase Auth sign-in so that the
     * stored display name, email, and photo URL stay in sync with Firebase.
     *
     * @param user the currently signed-in Firebase user (must not be null)
     * @return the slot number (1 or 2) that was assigned or updated,
     *         or -1 if both slots are occupied by different users (capacity full)
     */
    public int registerCurrentUser(FirebaseUser user) {
        if (user == null) return -1;

        String uid         = user.getUid();
        String displayName = user.getDisplayName()  != null ? user.getDisplayName()  : "";
        String email       = user.getEmail()         != null ? user.getEmail()         : "";
        String photoUrl    = user.getPhotoUrl()      != null ? user.getPhotoUrl().toString() : null;

        // Detect sign-in provider from FirebaseUser.getProviderData()
        String provider = PROVIDER_EMAIL; // default
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if (com.google.firebase.auth.GoogleAuthProvider.PROVIDER_ID.equals(info.getProviderId())) {
                provider = PROVIDER_GOOGLE;
                break;
            }
        }
        final String detectedProvider = provider;

        // ── Case 1: UID already in slot 1 ─────────────────────────────────────
        if (uid.equals(prefs.getString(KEY_S1_UID, null))) {
            persistSlot(1, uid, displayName, email, photoUrl, detectedProvider);
            prefs.edit().putInt(KEY_ACTIVE_SLOT, 1).apply();
            return 1;
        }

        // ── Case 2: UID already in slot 2 ─────────────────────────────────────
        if (uid.equals(prefs.getString(KEY_S2_UID, null))) {
            persistSlot(2, uid, displayName, email, photoUrl, detectedProvider);
            prefs.edit().putInt(KEY_ACTIVE_SLOT, 2).apply();
            return 2;
        }

        // ── Case 3: new UID — assign to first empty slot ──────────────────────
        if (TextUtils.isEmpty(prefs.getString(KEY_S1_UID, null))) {
            persistSlot(1, uid, displayName, email, photoUrl, detectedProvider);
            prefs.edit().putInt(KEY_ACTIVE_SLOT, 1).apply();
            return 1;
        }

        if (TextUtils.isEmpty(prefs.getString(KEY_S2_UID, null))) {
            persistSlot(2, uid, displayName, email, photoUrl, detectedProvider);
            prefs.edit().putInt(KEY_ACTIVE_SLOT, 2).apply();
            return 2;
        }

        // ── Case 4: both slots occupied by different UIDs — reject ─────────────
        // The UI must prevent reaching this path by hiding the "Add" chip
        // when isAtMaxCapacity() returns true.
        return -1;
    }

    // ── Slot readers ──────────────────────────────────────────────────────────

    /**
     * Returns the {@link AccountProfile} stored in the given slot,
     * or {@code null} when the slot is empty.
     *
     * @param slot 1 or 2
     */
    @Nullable
    public AccountProfile getSlot(int slot) {
        if (slot != 1 && slot != 2) return null;

        String uidKey   = (slot == 1) ? KEY_S1_UID   : KEY_S2_UID;
        String nameKey  = (slot == 1) ? KEY_S1_NAME  : KEY_S2_NAME;
        String emailKey = (slot == 1) ? KEY_S1_EMAIL : KEY_S2_EMAIL;
        String photoKey = (slot == 1) ? KEY_S1_PHOTO : KEY_S2_PHOTO;

        String uid = prefs.getString(uidKey, null);
        if (TextUtils.isEmpty(uid)) return null;

        return new AccountProfile(
                slot,
                uid,
                prefs.getString(nameKey,  ""),
                prefs.getString(emailKey, ""),
                prefs.getString(photoKey, null)
        );
    }

    /**
     * Returns the currently active slot number (1 or 2).
     * Returns 1 as a safe default when the value has not yet been persisted
     * (e.g. on first install before any login).
     */
    public int getActiveSlot() {
        return prefs.getInt(KEY_ACTIVE_SLOT, 1);
    }

    /**
     * Returns the {@link AccountProfile} for the currently active (signed-in) slot,
     * or {@code null} when no account is registered yet.
     */
    @Nullable
    public AccountProfile getActiveProfile() {
        return getSlot(getActiveSlot());
    }

    /**
     * Returns the {@link AccountProfile} for the slot that is NOT currently active,
     * or {@code null} when only one (or zero) accounts are registered.
     *
     * This is the profile shown as the "switch-to" chip in the drawer header.
     */
    @Nullable
    public AccountProfile getInactiveProfile() {
        int inactiveSlot = (getActiveSlot() == 1) ? 2 : 1;
        return getSlot(inactiveSlot);
    }

    // ── Provider helpers ──────────────────────────────────────────────────────

    /**
     * Returns the sign-in provider for the given slot ("google" or "email").
     * Returns PROVIDER_EMAIL as a safe default if no provider was stored.
     */
    public String getProvider(int slot) {
        String key = (slot == 1) ? KEY_S1_PROVIDER : KEY_S2_PROVIDER;
        String provider = prefs.getString(key, PROVIDER_EMAIL);
        return provider != null ? provider : PROVIDER_EMAIL;
    }

    /** Convenience: returns the provider for the currently active slot. */
    public String getActiveProvider() {
        return getProvider(getActiveSlot());
    }

    /** Convenience: returns the provider for the inactive slot. */
    public String getInactiveProvider() {
        int inactiveSlot = (getActiveSlot() == 1) ? 2 : 1;
        return getProvider(inactiveSlot);
    }

    // ── Capacity helpers ──────────────────────────────────────────────────────

    /**
     * Returns the total number of registered accounts (0, 1, or 2).
     */
    public int getAccountCount() {
        int count = 0;
        if (!TextUtils.isEmpty(prefs.getString(KEY_S1_UID, null))) count++;
        if (!TextUtils.isEmpty(prefs.getString(KEY_S2_UID, null))) count++;
        return count;
    }

    /**
     * Returns {@code true} when both slots are occupied.
     *
     * The "Add account" chip in the drawer must be hidden (GONE) whenever
     * this method returns true to enforce the strict 2-account limit.
     */
    public boolean isAtMaxCapacity() {
        return getAccountCount() >= MAX_ACCOUNTS;
    }

    // ── Slot removal ─────────────────────────────────────────────────────────

    /**
     * Clears all data for the given slot and adjusts the active-slot pointer
     * if necessary.
     *
     * Rules:
     *   - If the cleared slot WAS the active slot, the active pointer shifts to
     *     the remaining slot (or to 0 if the other slot is also empty).
     *   - If the cleared slot was NOT the active slot, the active pointer is
     *     left unchanged.
     *
     * The caller (DashboardActivity) is responsible for ensuring the user does
     * not clear the slot that is currently signed-in to Firebase Auth.  The UI
     * enforces this by showing "Cannot remove the active account" and blocking
     * the action before this method is reached.
     *
     * @param slot 1 or 2
     */
    public void clearSlot(int slot) {
        if (slot != 1 && slot != 2) return;

        SharedPreferences.Editor editor = prefs.edit();

        if (slot == 1) {
            editor.remove(KEY_S1_UID);
            editor.remove(KEY_S1_NAME);
            editor.remove(KEY_S1_EMAIL);
            editor.remove(KEY_S1_PHOTO);
        } else {
            editor.remove(KEY_S2_UID);
            editor.remove(KEY_S2_NAME);
            editor.remove(KEY_S2_EMAIL);
            editor.remove(KEY_S2_PHOTO);
        }

        // Adjust the active pointer if we just cleared the active slot
        if (getActiveSlot() == slot) {
            int otherSlot = (slot == 1) ? 2 : 1;
            boolean otherOccupied = !TextUtils.isEmpty(
                    prefs.getString((otherSlot == 1) ? KEY_S1_UID : KEY_S2_UID, null));
            editor.putInt(KEY_ACTIVE_SLOT, otherOccupied ? otherSlot : 0);
        }

        editor.apply();
    }

    /**
     * Clears ALL slot data and resets the active pointer to 0.
     *
     * Call this on a complete "Log Out" to return to the initial state so that
     * the next ranger who logs in starts with a clean 2-slot store.
     */
    public void clearAll() {
        prefs.edit().clear().apply();
    }

    // ── Explicit active-slot mutation ─────────────────────────────────────────

    /**
     * Manually updates the active-slot pointer without changing any profile data.
     *
     * Normally this pointer is set automatically by {@link #registerCurrentUser}.
     * This method is exposed for cases where the app needs to reflect a Firebase
     * Auth session change that was not triggered through this manager
     * (e.g. the system silently re-authenticated a token).
     *
     * @param slot 1 or 2; passing 0 marks "no active slot" (transitional state)
     */
    public void setActiveSlot(int slot) {
        prefs.edit().putInt(KEY_ACTIVE_SLOT, slot).apply();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Writes all four fields for the given slot atomically via a single
     * SharedPreferences transaction.  Skips the photo URL if it is null
     * so that an existing photo is not overwritten by a null value (e.g.
     * when a user's photo URL temporarily cannot be resolved).
     */
    private void persistSlot(int slot,
                             String uid,
                             String displayName,
                             String email,
                             @Nullable String photoUrl,
                             String provider) {
        SharedPreferences.Editor editor = prefs.edit();

        if (slot == 1) {
            editor.putString(KEY_S1_UID,      uid);
            editor.putString(KEY_S1_NAME,     displayName);
            editor.putString(KEY_S1_EMAIL,    email);
            editor.putString(KEY_S1_PROVIDER, provider);
            if (photoUrl != null) editor.putString(KEY_S1_PHOTO, photoUrl);
        } else {
            editor.putString(KEY_S2_UID,      uid);
            editor.putString(KEY_S2_NAME,     displayName);
            editor.putString(KEY_S2_EMAIL,    email);
            editor.putString(KEY_S2_PROVIDER, provider);
            if (photoUrl != null) editor.putString(KEY_S2_PHOTO, photoUrl);
        }

        editor.apply();
    }
}
