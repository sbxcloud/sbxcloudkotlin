package com.sbxcloud.library.kotlin.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Created by lgguzman on 23/11/17.
 */
class SbxPrefs(context: Context, sufix: String) {
    private var PREFS_FILENAME = "com.sbxcloud.library.kotlin."+sufix
    private val DOMAIN = "DOMAIN"
    private val APPKEY = "APPKEY"
    private val BASEURL = "BASEURL"
    private val TOKEN = "TOKEN"
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)

    var appKey: String
        get() = prefs.getString(APPKEY, "")
        set(value) = prefs.edit().putString(APPKEY, value).apply()

    var baseUrl: String
        get() = prefs.getString(BASEURL, "")
        set(value) = prefs.edit().putString(BASEURL, value).apply()

    var token: String
        get() = prefs.getString(TOKEN, "")
        set(value) = prefs.edit().putString(TOKEN, value).apply()

    var domain: Int
        get() = prefs.getInt(DOMAIN, 0)
        set(value) = prefs.edit().putInt(DOMAIN, value).apply()
}