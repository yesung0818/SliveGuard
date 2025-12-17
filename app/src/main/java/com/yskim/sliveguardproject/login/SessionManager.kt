package com.yskim.sliveguardproject.login

import android.content.Context

object SessionManager {
    private const val PREF_NAME = "sliveguard_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_PROFILE_IMAGE_URI = "profile_image_uri"

    private const val KEY_LOGIN_ID = "login_id"

    private const val KEY_LOGIN_TYPE = "login_type"
    private const val KEY_KAKAO_ACCESS_TOKEN = "kakao_access_token"

    enum class LoginType {
        NORMAL,
        KAKAO
    }

    // loginType 저장/조회
    fun saveLoginType(context: Context, loginType: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LOGIN_TYPE, loginType).apply()
    }

    fun getLoginType(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOGIN_TYPE, null)
    }

    // 카카오 SDK 전용
    fun saveKakaoAccessToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_KAKAO_ACCESS_TOKEN, token).apply()
    }

    fun getKakaoAccessToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_KAKAO_ACCESS_TOKEN, null)
    }

    fun saveLoginId(context: Context, loginId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LOGIN_ID, loginId)
            .apply()
    }

    fun getLoginId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOGIN_ID, null)
    }

    //서버용
    fun saveAccessToken( context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREF_NAME,Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .apply()
    }

    fun getAccessToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME,Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun clearSession(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun saveUserName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_USER_NAME, name)
            .apply()
    }

    fun getUserName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_NAME, null)
    }

    fun saveProfileImageUri(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PROFILE_IMAGE_URI, uri)
            .apply()
    }

    fun getProfileImageUri(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PROFILE_IMAGE_URI, null)
    }
}