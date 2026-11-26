package com.example.studentprojecttracker.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "TeamSyncSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_EMAIL = "email";

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private Context context;

    public SessionManager(Context context) {
        this.context = context;
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void createLoginSession(String email) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        // Lower-case so it matches the member lists and request.auth.token.email
        editor.putString(KEY_EMAIL, DataContract.normalizeEmail(email));
        editor.apply();
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getUserEmail() {
        return DataContract.normalizeEmail(pref.getString(KEY_EMAIL, null));
    }

    /**
     * Role of the signed-in user in one project (Section 4.4.3): a user's role
     * applies per project, not per account.
     */
    public String getProjectRole(com.example.studentprojecttracker.models.Project project) {
        String email = getUserEmail();
        if (project == null || email == null) return DataContract.ROLE_NONE;
        if (containsEmail(project.getLeaderIds(), email)) return DataContract.ROLE_PROJECT_MANAGER;
        if (containsEmail(project.getMentorIds(), email)) return DataContract.ROLE_TEACHER;
        if (containsEmail(project.getMemberIds(), email)) return DataContract.ROLE_PROJECT_MEMBER;
        return DataContract.ROLE_NONE;
    }

    public boolean isProjectManager(com.example.studentprojecttracker.models.Project project) {
        return DataContract.ROLE_PROJECT_MANAGER.equals(getProjectRole(project));
    }

    private static boolean containsEmail(java.util.List<String> emails, String email) {
        if (emails == null) return false;
        for (String e : emails) {
            if (email.equals(DataContract.normalizeEmail(e))) return true;
        }
        return false;
    }

    public void logout() {
        editor.clear();
        editor.apply();

        // Clear profile picture cache on logout
        ProfilePictureManager.getInstance().clearAllCache();
    }

    private static final String KEY_USER_ROLE = "userRole";
    private static final String KEY_USER_ID = "userId";

    public void saveUserRole(String role) {
        editor.putString(KEY_USER_ROLE, role);
        editor.apply();
    }

    public String getUserRole() {
        return pref.getString(KEY_USER_ROLE, "member");
    }

    public void saveUserId(String userId) {
        editor.putString(KEY_USER_ID, userId);
        editor.apply();
    }

    public String getUserId() {
        return pref.getString(KEY_USER_ID, null);
    }
}
