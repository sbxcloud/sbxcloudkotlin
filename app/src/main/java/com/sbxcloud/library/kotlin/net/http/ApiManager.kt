package com.sbxcloud.library.kotlin.net.http

import com.sbxcloud.library.kotlin.core.SbxCore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Created by lgguzman on 23/11/17.
 */
class ApiManager {


    companion object {
        val HTTP: OkHttpClient
            get() = getInstance().mOkHttpClient

        var ourInstance: ApiManager? = null


        @Throws(Exception::class)
        fun getInstance(): ApiManager {
            var temp = ourInstance
            if (ourInstance == null) {
                synchronized(ApiManager::class.java) {
                    temp = ourInstance

                    if (ourInstance == null) {
                        temp = ApiManager()
                        ourInstance = temp
                    }
                }
            }

            return temp!!
        }
    }



    var mOkHttpClient: OkHttpClient
    init {
        val httpLoggingInterceptor = SbxHttpLoggingInterceptor()
        if (SbxCore.isHttpLog) {
            httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        }


        mOkHttpClient = OkHttpClient.Builder()
                .connectTimeout(260, TimeUnit.SECONDS)
                .readTimeout(260, TimeUnit.SECONDS)
                .writeTimeout(260, TimeUnit.SECONDS)
                .addInterceptor(httpLoggingInterceptor)
                .build()
    }


}