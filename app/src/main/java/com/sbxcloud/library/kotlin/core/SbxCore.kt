package com.sbxcloud.library.kotlin.core

import android.content.Context
import android.content.SharedPreferences
import com.sbxcloud.library.kotlin.net.http.ApiManager
import io.reactivex.*
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.experimental.*
import okhttp3.*
import org.json.JSONObject


/**
 * Created by lgguzman on 23/11/17.
 */
class SbxCore(context: Context, sufix: String) {


    companion object {
        var prefs: SbxPrefs? = null
        var isHttpLog = false
        private val urls = URLS()
        private val JSON
        = MediaType.parse("application/json; charset=utf-8")
    }

    val request
        get() =   Request.Builder().apply {
            header("App-Key", prefs!!.appKey)
            if(!prefs!!.token.equals("")){
                header("Authorization", "Bearer ${prefs!!.token}")
            }
        }

    val requestJSON
        get() = request.apply { header("Content-Type", "application/json") }

    var token
        get() =  prefs!!.token
        set(value) { prefs!!.token = value}

    init {
        prefs = SbxPrefs(context, sufix)
    }


    fun initialize(domain: Int, baseUrl: String, appkey: String){
        prefs!!.appKey = appkey
        prefs!!.domain = domain
        prefs!!.baseUrl = baseUrl
    }

    private fun p(path: String): String{return  prefs!!.baseUrl + path }
    private fun bodyPOST(json: String):RequestBody { return RequestBody.create(JSON, json)}


    /**
     * @param token String of token to validate
     */
    fun validateRx(token: String): Single<out JSONObject>  {
         return sendObserver( Single.create( {
                 val url = HttpUrl.parse(p(urls.validate))!!.
                         newBuilder().apply { addQueryParameter("token", token) }.build().toString()
                 call(request.url(url).build(),it)

        }))
    }

    private fun call(r: Request, it: SingleEmitter<JSONObject>) = runBlocking(CommonPool){
        val response = async(CommonPool) { ApiManager.HTTP.newCall(r).execute() }.await()
        if(response.isSuccessful){
            val jsonObject = JSONObject(response.body()!!.string())
            it.onSuccess(jsonObject)
        }else{
            it.onError(Exception(response.message()))
        }
    }

    private fun <T> sendObserver(single: Single<out T>): Single<out T>{
        return single.subscribeOn(Schedulers.newThread())
                .onErrorResumeNext({ return@onErrorResumeNext Single.error(it) })
    }

    /**
     * @param key the CloudscriptKey
     * @param params jsonString parameters to run cloudscript
     */
    fun runRx(key: String, params: String): Single<out JSONObject>{
        return sendObserver( Single.create( {
            val r = requestJSON.url(p(urls.cloudscript_run)).post(bodyPOST(JSONObject().apply {
                put("key", key)
                put("params", JSONObject(params))
            }.toString())).build()
            call(r,it)
        }))
    }


}



data class URLS (
    val update_password: String = "/user/v1/password",
    val login: String = "/user/v1/login",
    val register: String = "/user/v1/register",
    val validate: String = "/user/v1/validate",
    val row: String = "/data/v1/row",
    val find: String = "/data/v1/row/find",
    val update: String = "/data/v1/row/update",
    val delete: String = "/data/v1/row/delete",
    val downloadFile: String = "/content/v1/download",
    val uploadFile: String = "/content/v1/upload",
    val addFolder: String = "/content/v1/folder",
    val folderList: String = "/content/v1/folder",
    val send_mail: String = "/email/v1/send",
    val payment_customer: String = "/payment/v1/customer",
    val payment_card: String = "/payment/v1/card",
    val payment_token: String = "/payment/v1/token",
    val password: String = "/user/v1/password/request",
    val cloudscript_run: String = "/cloudscript/v1/run"
)
