package com.sbs.data;

/**
 * AccountProfile
 *
 * Immutable snapshot of one ranger account stored in an AccountSlotManager slot.
 *
 * Fields
 * ──────
 *  slot         – 1 or 2 (the position in the two-slot store)
 *  uid          – Firebase Auth UID; used as the stable identity key
 *  displayName  – Firebase display name (may be empty; fall back to email prefix)
 *  email        – Firebase account email
 *  photoUrl     – nullable Firebase photo URL; null when no photo is set
 *
 * Helpers
 * ───────
 *  initials()        – returns 1-2 upper-case initials for avatar placeholder rendering
 *  shortLabel()      – ≤10-char name suitable for the chip label below the avatar
 *  resolvedName()    – displayName if non-empty, otherwise the email prefix before '@'
 */
public final class AccountProfile {

    // ── Identity ──────────────────────────────────────────────────────────────

    /** Slot index in AccountSlotManager: 1 or 2. */
    public final int slot;

    /** Firebase Auth UID — the stable, unique identifier for this account. */
    public final String uid;

    // ── Display data ──────────────────────────────────────────────────────────

    /**
     * Firebase display name. May be an empty string when the user has never
     * set a display name (common for email/password accounts created without a
     * name).  Never null — normalised to "" in the constructor.
     */
    public final String displayName;

    /**
     * Firebase account email. Never null — normalised to "" in the constructor.
     */
    public final String email;

    /**
     * Firebase photo URL as a String, or null when no profile photo is set.
     * Pass directly to Glide; Glide handles null gracefully.
     */
    public final String photoUrl;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates an AccountProfile.
     *
     * @param slot        1 or 2
     * @param uid         Firebase UID (must not be null or empty)
     * @param displayName Firebase display name (null is normalised to "")
     * @param email       Firebase email (null is normalised to "")
     * @param photoUrl    Firebase photo URL, or null
     */
    public AccountProfile(int slot,
                          String uid,
                          String displayName,
                          String email,
                          String photoUrl) {
        this.slot        = slot;
        this.uid         = uid  != null ? uid         : "";
        this.displayName = displayName != null ? displayName.trim() : "";
        this.email       = email != null ? email.trim() : "";
        this.photoUrl    = photoUrl;
    }

    // ── Display helpers ───────────────────────────────────────────────────────

    /**
     * Returns the best human-readable name for this account.
     *
     * Priority:
     *   1. displayName (if non-empty)
     *   2. The part of the email address before the '@' sign
     *   3. The full email (fallback when there is no '@')
     *   4. "Ranger" (last resort)
     */
    public String resolvedName() {
        if (!displayName.isEmpty()) {
            return displayName;
        }
        if (!email.isEmpty()) {
            int atIndex = email.indexOf('@');
            return atIndex > 0 ? email.substring(0, atIndex) : email;
        }
        return "Ranger";
    }

    /**
     * Returns 1 or 2 upper-case initials derived from the resolved name.
     *
     * Examples:
     *   "Jean-Pierre Habimana"  → "JH"
     *   "ranger42"              → "R"
     *   ""                      → "?"
     *
     * Used to render a text placeholder inside the circular avatar when no
     * photo URL is available.
     */
    public String initials() {
        String name = resolvedName();
        if (name.isEmpty() || name.equals("Ranger")) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            char first  = Character.toUpperCase(parts[0].charAt(0));
            char second = Character.toUpperCase(parts[parts.length - 1].charAt(0));
            return "" + first + second;
        }
        return String.valueOf(Character.toUpperCase(name.charAt(0)));
    }

    /**
     * Returns a label of at most {@code maxLength} characters suitable for
     * display beneath the small circular avatar chip in the drawer header.
     *
     * If the resolved name is longer than {@code maxLength} characters it is
     * truncated and an ellipsis character (…) is appended, keeping the total
     * length at {@code maxLength}.
     *
     * @param maxLength maximum number of characters including the ellipsis
     */
    public String shortLabel(int maxLength) {
        String name = resolvedName();
        if (name.length() <= maxLength) {
            return name;
        }
        // Truncate so the ellipsis fits within maxLength
        return name.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    /**
     * Convenience overload of {@link #shortLabel(int)} with a default limit
     * of 10 characters — the width that fits comfortably beneath a 60 dp chip.
     */
    public String shortLabel() {
        return shortLabel(10);
    }

    // ── Object overrides ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountProfile)) return false;
        AccountProfile other = (AccountProfile) o;
        return slot == other.slot && uid.equals(other.uid);
    }

    @Override
    public int hashCode() {
        int result = slot;
        result = 31 * result + uid.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "AccountProfile{"
                + "slot=" + slot
                + ", uid='" + uid + '\''
                + ", displayName='" + displayName + '\''
                + ", email='" + email + '\''
                + ", hasPhoto=" + (photoUrl != null)
                + '}';
    }
}
