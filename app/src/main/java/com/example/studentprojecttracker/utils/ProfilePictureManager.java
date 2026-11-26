package com.example.studentprojecttracker.utils;

import android.content.Context;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.example.studentprojecttracker.R;
import com.example.studentprojecttracker.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton class to manage profile picture loading and caching
 */
public class ProfilePictureManager {
    private static final String TAG = "ProfilePictureManager";
    private static ProfilePictureManager instance;

    private FirestoreHelper firestoreHelper;
    private Map<String, String> photoUrlCache; // userId -> photoUrl

    private ProfilePictureManager() {
        this.firestoreHelper = new FirestoreHelper();
        this.photoUrlCache = new HashMap<>();
    }

    public static synchronized ProfilePictureManager getInstance() {
        if (instance == null) {
            instance = new ProfilePictureManager();
        }
        return instance;
    }

    /**
     * Load profile picture from Firebase Auth or Firestore
     * @param context Context for Glide
     * @param userId User ID or email
     * @param imageView Target ImageView
     */
    /**
     * Load profile picture from Firebase Auth or Firestore
     * @param context Context for Glide
     * @param userId User ID or email
     * @param imageView Target ImageView
     */
    /**
     * Loads the signed-in user's own photo from users/{uid} into the ImageView.
     * Used by the dashboard, profile and edit-profile screens.
     */
    public void loadMyPhoto(Context context, ImageView imageView) {
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (context == null || imageView == null || me == null) return;
        firestoreHelper.getUserProfile(me.getUid(), new FirestoreHelper.UserCallback() {
            @Override
            public void onSuccess(User user) {
                if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                    loadImageWithGlide(context, user.getPhotoUrl(), imageView);
                } else {
                    imageView.setImageResource(R.drawable.ic_avatar_placeholder);
                }
            }

            @Override
            public void onError(String error) {
                imageView.setImageResource(R.drawable.ic_avatar_placeholder);
            }
        });
    }

    /** Largest side of a stored profile picture, in pixels. */
    public static final int AVATAR_MAX_PX = 256;

    /**
     * Profile pictures are stored in Firestore instead of Cloud Storage (which
     * needs the Blaze plan): the picked image is scaled down to 256 px, saved
     * as JPEG and turned into a "data:image/jpeg;base64,..." string of a few
     * dozen KB, which Glide can display directly.
     */
    public static String encodeAvatar(Context context, android.net.Uri uri) throws java.io.IOException {
        android.content.ContentResolver resolver = context.getContentResolver();

        // 1) Read only the size, then decode a smaller copy to save memory
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (java.io.InputStream in = resolver.openInputStream(uri)) {
            android.graphics.BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= AVATAR_MAX_PX) sample *= 2;

        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = sample;
        android.graphics.Bitmap bitmap;
        try (java.io.InputStream in = resolver.openInputStream(uri)) {
            bitmap = android.graphics.BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) throw new java.io.IOException("Not an image");

        // 2) Scale so the longest side is at most 256 px
        float scale = Math.min(1f, (float) AVATAR_MAX_PX / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        if (scale < 1f) {
            bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap,
                    Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
        }

        // 3) JPEG + Base64 data URI
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out);
        return "data:image/jpeg;base64,"
                + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
    }

    public void loadProfilePicture(Context context, String userId, ImageView imageView) {
        if (context == null || userId == null || imageView == null) {
            return;
        }

        // Check if we have cached URL
        if (photoUrlCache.containsKey(userId)) {
            String photoUrl = photoUrlCache.get(userId);
            if (photoUrl != null && !photoUrl.isEmpty()) {
                loadImageWithGlide(context, photoUrl, imageView);
                return;
            }
        }

        // Photos are stored in users/{uid}.photoUrl in Firestore (as a small
        // data:image/jpeg;base64 image), so always read the profile from there.
        // Load from Firestore - use email query if it looks like an email
        if (userId.contains("@")) {
            loadProfilePictureByEmail(context, userId, imageView);
        } else {
            loadProfilePictureFromFirestore(context, userId, imageView);
        }
    }

    /**
     * Load profile picture from Firestore by email
     */
    private void loadProfilePictureByEmail(Context context, String email, ImageView imageView) {
        firestoreHelper.getUserProfileByEmail(email, new FirestoreHelper.UserCallback() {
            @Override
            public void onSuccess(User user) {
                if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                    photoUrlCache.put(email, user.getPhotoUrl());
                    loadImageWithGlide(context, user.getPhotoUrl(), imageView);
                } else {
                    imageView.setImageResource(R.drawable.ic_avatar_placeholder);
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading profile picture for " + email + ": " + error);
                imageView.setImageResource(R.drawable.ic_avatar_placeholder);
            }
        });
    }

    /**
     * Load profile picture directly from URL
     * @param context Context for Glide
     * @param photoUrl Photo URL
     * @param imageView Target ImageView
     */
    public void loadProfilePictureFromUrl(Context context, String photoUrl, ImageView imageView) {
        if (context == null || photoUrl == null || imageView == null) {
            return;
        }

        if (photoUrl.isEmpty()) {
            imageView.setImageResource(R.drawable.ic_avatar_placeholder);
            return;
        }

        loadImageWithGlide(context, photoUrl, imageView);
    }

    /**
     * Load profile picture from Firestore
     */
    private void loadProfilePictureFromFirestore(Context context, String userId, ImageView imageView) {
        firestoreHelper.getUserProfile(userId, new FirestoreHelper.UserCallback() {
            @Override
            public void onSuccess(User user) {
                if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                    photoUrlCache.put(userId, user.getPhotoUrl());
                    loadImageWithGlide(context, user.getPhotoUrl(), imageView);
                } else {
                    imageView.setImageResource(R.drawable.ic_avatar_placeholder);
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading profile picture for " + userId + ": " + error);
                imageView.setImageResource(R.drawable.ic_avatar_placeholder);
            }
        });
    }

    /**
     * Load image using Glide with circular crop and placeholder
     */
    private void loadImageWithGlide(Context context, String photoUrl, ImageView imageView) {
        Glide.with(context)
                .load(photoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .into(imageView);
    }

    /**
     * Preload profile pictures for a list of user IDs
     * This improves performance when showing lists of users
     * @param context Context
     * @param userIds List of user IDs to preload
     */
    public void preloadProfilePictures(Context context, List<String> userIds) {
        if (context == null || userIds == null || userIds.isEmpty()) {
            return;
        }

        for (String userId : userIds) {
            if (!photoUrlCache.containsKey(userId)) {
                firestoreHelper.getUserProfile(userId, new FirestoreHelper.UserCallback() {
                    @Override
                    public void onSuccess(User user) {
                        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                            photoUrlCache.put(userId, user.getPhotoUrl());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        // Silently fail for preloading
                    }
                });
            }
        }
    }

    /**
     * Clear cache for a specific user
     * Call this after a user updates their profile picture
     * @param userId User ID to clear
     */
    public void clearCache(String userId) {
        if (userId != null) {
            photoUrlCache.remove(userId);
        }
    }

    /**
     * Clear entire cache
     * Call this on logout
     */
    public void clearAllCache() {
        photoUrlCache.clear();
    }

    /**
     * Manually cache a photo URL
     * @param userId User ID
     * @param photoUrl Photo URL
     */
    public void cachePhotoUrl(String userId, String photoUrl) {
        if (userId != null && photoUrl != null) {
            photoUrlCache.put(userId, photoUrl);
        }
    }
}