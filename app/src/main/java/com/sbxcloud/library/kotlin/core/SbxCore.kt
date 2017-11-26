package com.sbxcloud.library.kotlin.core

import android.content.Context
import android.content.SharedPreferences
import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.sbxcloud.library.kotlin.net.http.ApiManager
import io.reactivex.*
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.experimental.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder


/**
 * Created by lgguzman on 23/11/17.
 */
class SbxCore(context: Context, sufix: String) {

    val parser: Parser = Parser()

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

    ///////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////// GENERAL FUNCTIONS ////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////



    private fun p(path: String): String{return  prefs!!.baseUrl + path }

    private fun bodyPOST(json: String):RequestBody { return RequestBody.create(JSON, json)}

    private fun call(r: Request, it: SingleEmitter<JSONObject>) = runBlocking(CommonPool){
        with (async(CommonPool) { ApiManager.HTTP.newCall(r).execute() }.await()){
            if(isSuccessful) it.onSuccess(JSONObject(body()!!.string()))
            else it.onError(Exception(message())) } }

    private fun <T> sendObserver(single: Single<out T>): Single<out T>{
        return single.subscribeOn(Schedulers.newThread())
                .onErrorResumeNext({ return@onErrorResumeNext Single.error(it) }) }

    private fun validateLogin(login: String): Boolean { return """^(\w?\.?\-?)+$""".toRegex().matches(login) }
    private fun validateEmail(email: String): Boolean { return """^(\w?\.?\-?\+?)+@(\w?\.?\-?)+${'$'}""".toRegex().matches(email) }


    private fun encodeEmails(email: String): String {
        val spl = email.split("@");
        var response = email
        if (spl.size > 1) {
            response =  URLEncoder.encode(spl[0], "UTF-8")
                        .replace("\\+", "%20") + "@" + spl[1];
        }
        return response;
    }

    private fun JSONtoJson(json: JSONObject): JsonObject{
        return parser.parse(StringBuilder(json.toString())) as JsonObject
    }

    private fun JSONAtoJsonA(jsonA: JSONArray): JsonArray<JsonObject>  = runBlocking(CommonPool){
        val list = JsonArray<JsonObject>()
        val list2 = ArrayList<Deferred<JsonObject>>()
        for (i in 0..jsonA.length()){
            list2.add(async (CommonPool){ JSONtoJson(jsonA.getJSONObject(i)) })
        }
        for (i in 0..jsonA.length()){
            list.add( list2[i].await())
        }
        list
    }

    private fun insertOrUpdate(it: SingleEmitter<JSONObject>, url: String, model: String, data: JSONObject?, dataArray: JSONArray? ){
        var sw = false
        val json = SbxQuery().setDomain(prefs!!.domain).setModel(model).apply {
            if(data==null && dataArray!=null){
                sw = true; val array =JSONAtoJsonA(dataArray); for (item in array) addObject(item)
            }else{
                if(dataArray==null && data!=null){ sw=true; addObject(JSONtoJson(data)) }
            }
        }
        if(sw){
            call(requestJSON.url(p(url)).post(bodyPOST(json.compile())).build(),it)
        }else{
            it.onError(Exception("Empty Data or duplicate Data"))
        }
    }



    ///////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////// AUTH FUNCTIONS ///////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////


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


    /**
     * @param  login
     * @param  email
     * @param  name
     * @param  password
     * @return single
     */
    fun signUpRx(login: String, email: String, name: String, password: String): Single<out JSONObject> {
        return sendObserver( Single.create({
            if (this.validateLogin(login) && this.validateEmail(email)) {
                val url = "${p(urls.register)}?email=${this.encodeEmails(email)}&password=${URLEncoder.encode(password, "UTF-8")}" +
                        "&name=${name}&login=${login}&domain=${prefs!!.domain}"
                call(request.url(url).build(), it)
            } else {
                it.onError(Exception("Login or email contains invalid characters. Letters, numbers and underscore are accepted"))
            }
        }))
    }

    /**
     * @param  login
     * @param  password
     * @return single
     */
    fun loginRx(login: String, password: String): Single<out JSONObject> {
        return sendObserver( Single.create({
            if  ( (this.validateLogin(login) && login.indexOf("@") < 0) ||  (login.indexOf("@") >= 0 && this.validateEmail(login))) {
                val url = "${p(urls.login)}?login=${this.encodeEmails(login)}&password=${URLEncoder.encode(password, "UTF-8")}"
                call(request.url(url).build(), it)
            } else {
                it.onError(Exception("Login or email contains invalid characters. Letters, numbers and underscore are accepted"))
            }
        }))
    }


    /**
     * Send email to changePassword
     * @param  userEmail
     * @param  subject
     * @param  emailTemplate
     * @return {Observable<Object>}
     */
    fun sendPasswordRequestRx(userEmail: String, subject: String, emailTemplate: String): Single<out JSONObject> {
        return sendObserver( Single.create( {
                val r = requestJSON.url(p(urls.password)).post(bodyPOST(JSONObject().apply {
                    put("user_email", userEmail)
                    put("domain", prefs!!.domain)
                    put("subject", subject)
                    put("email_template", emailTemplate )
                }.toString())).build()
                call(r,it)
        }))
    }


    /**
     * change password with email code
     * @param  userId
     * @param  userCode
     * @param  newPassword
     * @return Single
     */
    fun requestChangePasswordRx(userId: Int, userCode: Int, newPassword: String): Single<out JSONObject> {
        return sendObserver( Single.create( {
                val r = requestJSON.url(p(urls.password)).post(bodyPOST(JSONObject().apply {
                    put("password", newPassword)
                    put("domain", prefs!!.domain)
                    put("user_id", userId)
                    put("code", userCode )
                }.toString())).build()
                call(r,it)
        }))
    }

    /**
     * change password
     * @param  newPassword
     * @return Single}
     */
    fun changePasswordRx(newPassword: String): Single<out JSONObject> {
        return sendObserver( Single.create( {
            val r = requestJSON.url(p(urls.password)).post(bodyPOST(JSONObject().apply {
                put("password", newPassword)
                put("domain", prefs!!.domain)
            }.toString())).build()
            call(r,it)
        }))
    }


    ///////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////// DATA FUNCTIONS ///////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////




    /**
     * @param  model the name model in sbxcloud
     * @param data can be a JSON,
     * @param dataArray can be a JSONArray,
     * @return single
     */
    fun insertRx(model: String, data: JSONObject? = null, dataArray: JSONArray? = null): Single<out JSONObject> {
        return sendObserver( Single.create( { insertOrUpdate(it,urls.row,model,data, dataArray ) }))
    }

    /**
     * @param  model the name model in sbxcloud
     * @param data can be a JSON,
     * @param dataArray can be a JSONArray,
     * @return single
     */
   fun updateRx(model: String, data: JSONObject? = null, dataArray: JSONArray? = null): Single<out JSONObject> {
        return sendObserver( Single.create( { insertOrUpdate(it,urls.update,model,data, dataArray ) }))
    }








    /**
     * @param key the CloudscriptKey
     * @param params jsonString parameters to run cloudscript
     */
    fun runRx(key: String, params: String): Single<out JSONObject> {
        return sendObserver( Single.create( {
            runBlocking {
                val r = requestJSON.url(p(urls.cloudscript_run)).post(bodyPOST(JSONObject().apply {
                    put("key", key)
                    put("params", async (CommonPool) { JSONObject(params) }.await() )
                }.toString())).build()
                call(r,it)
            }

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
