package com.sbxcloud.library.kotlin.core

import android.content.Context
import android.content.SharedPreferences
import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.sbxcloud.library.kotlin.net.http.ApiManager
import io.reactivex.*
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.experimental.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder


/**
 * Created by lgguzman on 23/11/17.
 */
class SbxCore(context: Context, sufix: String): HttpHelper() {

    val parser: Parser = Parser()

    companion object {
        var prefs: SbxPrefs? = null
        var isHttpLog = false


    }

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



    private fun validateLogin(login: String): Boolean { return """^(\w?\.?\-?)+$""".toRegex().matches(login) }
    private fun validateEmail(email: String): Boolean { return """^(\w?\.?\-?\+?)+@(\w?\.?\-?)+$""".toRegex().matches(email) }


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
            try{
            call(requestJSON.url(p(url)).post(bodyPOST(json.compile())).build(),it)
            }catch (e: Exception){
                if(!it.isDisposed){it.onError(e)}

            }
        }else{
            if(!it.isDisposed){it.onError(Exception("Empty Data or duplicate Data"))}
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
                if(!it.isDisposed){ it.onError(Exception("Login or email contains invalid characters. Letters, numbers and underscore are accepted"))}
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
                if(!it.isDisposed){  it.onError(Exception("Login or email contains invalid characters. Letters, numbers and underscore are accepted"))}
            }
        }))
    }

    /**
     * @param  login
     * @param  password
     * @return single
     */
    fun configRx(): Single<out JSONObject> {
        return sendObserver( Single.create({
                call(request.url(p(urls.config)).build(), it)
        }))
    }


    /**
     * Send email to changePassword
     * @param  userEmail
     * @param  subject
     * @param  emailTemplate
     * @return single
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
                val r = requestJSON.url(p(urls.password)).put(bodyPOST(JSONObject().apply {
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



    fun delete(model: String): Find {
        return Find(model, this, false);
    }

    /**
     * @param  model the name model in sbxcloud
     * @param keys can be a String, a Class or array of both
     * @param {Callback} callBack
     */
    fun find(model: String): Find {
        return Find(model, this, true);
    }

    /**
     * @param response the response of the server
     * @param string completefetch the array of fetch
     * @returns JSONObject the response with the union between fetch_results and results
     */
    fun fetchedResult(response: JSONObject, completefetch: Array<String>): JSONObject{
        if (response.has("fetched_results")) {
            val jsonFetches = response.getJSONObject("fetched_results")
            val jsonResult = response.getJSONArray("results")
            val fetch = ArrayList<String>();
            val secondfetch = JSONObject();
            for ( i in 0..completefetch.size-1) {
                var index = 0;
                val temp = completefetch[i].split('.');
                if (fetch.indexOf(temp[0]) < 0) {
                    fetch.add(temp[0]);
                    index = fetch.size - 1;
                } else {
                    index = fetch.indexOf(temp[0]);
                }
                if (temp.size == 2 && !secondfetch.has(fetch[index])) {
                    secondfetch.put(fetch[index],JSONArray());
                }

                if (temp.size == 2) {
                    secondfetch.getJSONArray(fetch[index]).put(temp[1]);
                }
            }
            for (i in 0..jsonResult.length()-1) {
                val jsonData = jsonResult.getJSONObject(i)
                for (j in 0..fetch.size-1) {
                    for (mod in jsonFetches.keys()) {
                        val jsonModel = jsonFetches.getJSONObject(mod)
                        if (jsonModel.has(jsonData.getString(fetch[j]))) {
                            jsonData.put(fetch[j], jsonModel.getJSONObject(jsonData.getString(fetch[j])))
                            if (secondfetch.has(fetch[j])) {
                                for ( k in 0..secondfetch.getJSONArray(fetch[j]).length()-1) {
                                    val second = secondfetch.getJSONArray(fetch[j]).getString(k)
                                    for ( mod2 in jsonFetches.keys()) {
                                        val jsonModel2 = jsonFetches.getJSONObject(mod2)
                                        if (jsonModel2.has(jsonData.getJSONObject(fetch[j]).getString(second))) {
                                            jsonData.getJSONObject(fetch[j])
                                                    .put(second,jsonModel2.getJSONObject(jsonData.getJSONObject(fetch[j]).getString(second)))
                                            break;
                                        }
                                    }
                                }
                            }

                            break;
                        }
                    }
                }

            }
        }

        return response;
    }



    ///////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////// CLOUDSCRIPTs FUNCTIONS ///////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////


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


    /**
     * @param data JSONObject
     * @return {Observable<JSONObject>}
     */
    fun  paymentCustomerRx(data: JSONObject):  Single<out JSONObject> {
        return sendObserver( Single.create( {
            val r = requestJSON.url(p(HttpHelper.urls.payment_customer)).post(bodyPOST(data.apply {
                put("domain", SbxCore.prefs!!.domain)
            }.toString())).build()
            call(r,it)
        }))
    }

    /**
     * @param data JSONObject
     * @return {Observable<JSONObject>}
     */
    fun paymentCardRx(data: JSONObject):  Single<out JSONObject> {
        return sendObserver( Single.create( {
            val r = requestJSON.url(p(HttpHelper.urls.payment_card)).post(bodyPOST(data.apply {
                put("domain", SbxCore.prefs!!.domain)
            }.toString())).build()
            call(r,it)
        }))
    }

}



///////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////// CLASS HELPERS ////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////

class Find (var model: String,  var core: SbxCore,val isFind: Boolean ): HttpHelper(){

    var query = SbxQuery().setModel(model).setDomain(SbxCore.prefs!!.domain)
    var lastANDOR: String? = null
    var totalpages: Int=0;
    var fecth: Array<String>?=null

    fun newGroupWithAnd(): Find{
        this.query.newGroup("AND");
        this.lastANDOR = null;
        return this;
    }

    fun  newGroupWithOr(): Find{
        this.query.newGroup("OR");
        this.lastANDOR = null;
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun andWhereIsEqual(field: String, value: Any?): Find{
        this.lastANDOR = "AND";
        this.query.addCondition(this.lastANDOR!!, field, "=", value);
        return this;
    }

    /**
     * @param  field
     * @return Find
     */
    fun andWhereIsNotNull(field: String): Find{
        this.lastANDOR = "AND";
        this.query.addCondition(this.lastANDOR!!, field, "IS NOT", null);
        return this;
    }

    /**
     * @param  field
     * @return Find
     */
    fun andWhereIsNull(field: String): Find{
        this.lastANDOR = "AND";
        this.query.addCondition(this.lastANDOR!!, field, "IS", null);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun andWhereGreaterThan(field: String, value: Any?): Find{
        this.lastANDOR = "AND";
        this.query.addCondition(this.lastANDOR!!, field, ">", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun andWhereLessThan(field: String, value: Any?): Find{
        this.lastANDOR = "AND";
        this.query.addCondition(this.lastANDOR!!, field, "<", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun andWhereGreaterOrEqualThan(field: String, value: Any?): Find{
        this.lastANDOR = "AND";
        this.query.addCondition(this.lastANDOR!!, field, ">=", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun andWhereLessOrEqualThan(field: String, value: Any?): Find{
        this.lastANDOR = "AND";
        this.query.addCondition(this.lastANDOR!!, field, "<=", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun andWhereIsNotEqual(field: String, value: Any?): Find{
        this.lastANDOR = "AND";
        this.query.addCondition(this.lastANDOR!!, field, "!=", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun andWhereStartsWith(field: String, value: String): Find{
        this.lastANDOR = "AND";
        var value2 = if (value.length > 0)  "%${value}" else value;
        this.query.addCondition(this.lastANDOR!!, field, "LIKE", value2);
        return this;
    }


    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun andWhereEndsWith(field: String, value: String): Find{
        this.lastANDOR = "AND";
        var value2 = if (value.length > 0)  "${value}%" else value;
        this.query.addCondition(this.lastANDOR!!, field, "LIKE", value2);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun andWhereContains(field: String, value: String): Find{
        this.lastANDOR = "AND";
        var value2 = if (value.length > 0)  "%${value.split(" ").joinToString("%")}%" else value;
        this.query.addCondition(this.lastANDOR!!, field, "LIKE", value2);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun andWhereIn(field: String, value: Any?): Find{
        this.lastANDOR = "AND";
        this.query.addCondition(this.lastANDOR!!, field, "IN", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun andWhereNotIn(field: String, value: Any?): Find{
        this.lastANDOR = "AND";
        this.query.addCondition(this.lastANDOR!!, field, "NOT IN", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun orWhereIsEqual(field: String, value: Any?): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        this.query.addCondition(this.lastANDOR!!, field, "=", value);
        return this;
    }

    /**
     * @param  field
     * @return Find
     */
    fun orWhereIsNotNull(field: String): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        this.query.addCondition(this.lastANDOR!!, field, "IS NOT", null);
        return this;
    }

    /**
     * @param  field
     * @return Find
     */
    fun orWhereIsNull(field: String): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        this.query.addCondition(this.lastANDOR!!, field, "IS", null);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun orWhereGreaterThan(field: String, value: Any?): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        this.query.addCondition(this.lastANDOR!!, field, ">", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun orWhereLessThan(field: String, value: Any?): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        this.query.addCondition(this.lastANDOR!!, field, "<", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun orWhereGreaterOrEqualThan(field: String, value: Any?): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        this.query.addCondition(this.lastANDOR!!, field, ">=", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun orWhereLessOrEqualThan(field: String, value: Any?): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        this.query.addCondition(this.lastANDOR!!, field, "<=", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun orWhereIsNotEqual(field: String, value: Any?): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        this.query.addCondition(this.lastANDOR!!, field, "!=", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun orWhereStartsWith(field: String, value: String): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        var value2 = if (value!=null && value.length > 0)  "%${value}" else value;
        this.query.addCondition(this.lastANDOR!!, field, "LIKE", value2);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun orWhereEndsWith(field: String, value: String): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        var value2 = if (value.length > 0)  "${value}%" else value;
        this.query.addCondition(this.lastANDOR!!, field, "LIKE", value2);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun orWhereContains(field: String, value: String): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        // if the user sends null or empty, there will be no wildcar placed.
        var value2 = if(value.length > 0) "%${value.split(" ").joinToString("%")}%" else value;
        this.query.addCondition(this.lastANDOR!!, field, "LIKE", value2);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun orWhereIn(field: String, value: Any?): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        this.query.addCondition(this.lastANDOR!!, field, "IN", value);
        return this;
    }

    /**
     * @param  field
     * @param value
     * @return Find
     */
    fun orWhereNotIn(field: String, value: Any?): Find{
        this.lastANDOR = if (this.lastANDOR == null)  "AND" else "OR";
        this.query.addCondition(this.lastANDOR!!, field, "NOT IN", value);
        return this;
    }

    fun  whereWithKeys(keys: Array<String>): Find {
        this.query.whereWithKeys(keys);
        return this;
    }

    /**
     * @param {string} field
     * @param asc
     * @return {Find}
     */
    fun  orderBy(field: String, asc: Boolean= false): Find {
        this.query.orderBy(field, asc);
        return this;
    }

    fun  fetchModels(array: Array<String>): Find {
        if (this.isFind) {
            this.query.fetchModels(array);
            this.fecth = array;
        }
        return this;
    }


    fun thenRx(query: String?=null): Single<out JSONObject> {
        return sendObserver( Single.create( {
            try {

                call(requestJSON.url(p(if (isFind) urls.find else urls.delete)).post(
                        bodyPOST(if (query == null) this@Find.query.compile() else query)).build(), it)
            }catch (e: Exception){
                if(!it.isDisposed){ it.onError(e)}
            }
        }))
    }

    fun thenAllRx(): Single<out JSONObject> {
        val list = ArrayList<Single<out JSONObject>>()
        for (i in 0..10){
            val q = this@Find.query
            q.setPage(i+1)
            list.add(thenRx(q.compile()))
        }
        return  Single.zip(list, object: Function<Array<Any>, JSONObject>{
            override fun apply(t: Array<Any>): JSONObject {

               val result= t[0] as JSONObject
               val firstResults=  result.optJSONArray("results")
               val firstFResults=  result.optJSONArray("fetched_results")
                if(t.size>1)
                   for (i in 1..t.size-1){
                       val ar = t[i] as JSONObject
                       val results = ar.optJSONArray("results")
                       val fetch = ar.optJSONArray("fetched_results")
                       if(results!=null){
                           for (j in 0..results.length()-1) {
                               firstResults.put(results[j])
                           }
                       }
                       if(fetch!=null){
                           for (j in 0..fetch.length()-1) {
                               firstFResults.put(fetch[j])
                           }
                       }

                   }
                result.put("results",firstResults)
                result.put("fetched_results",firstFResults)
                return result
            }

        })

    }



    fun setPage(page: Int): Find {
        this.query.setPage(page);
        return this;
    }

    fun setPageSize(limit: Int):Find {
        this.query.setPageSize(limit);
        return this;
    }

    private fun find(query: String?=null): Single<out JSONObject> {
        return sendObserver( Single.create( {
            try{
            call(requestJSON.url(urls.find).post(
                    bodyPOST(if(query==null) this@Find.query.compile()else query)).build(),it)
            }catch (e: Exception){
                if(!it.isDisposed){  it.onError(e)}
            }
        }))

    }

}

open class HttpHelper  {
    companion object {
        protected val JSON
                = MediaType.parse("application/json; charset=utf-8")
        val urls = URLS()
    }

    protected fun p(path: String): String{return  SbxCore.prefs!!.baseUrl + path }

    protected fun bodyPOST(json: String):RequestBody { return RequestBody.create(JSON, json)}

    protected fun call(r: Request, it: SingleEmitter<JSONObject>) = runBlocking(CommonPool){
        try{
            with (async(CommonPool) {
                ApiManager.HTTP.newCall(r).execute() }.await()){
                if(isSuccessful){
                    if(!it.isDisposed){ try{it.onSuccess(JSONObject(body()!!.string()))}catch (e:Exception){e.printStackTrace()}}
                }
                else {
                    if(!it.isDisposed){ try{it.onError(Exception(message()))}catch (e:Exception){e.printStackTrace()}}
                }
            }
        }catch (e: Exception){
            if(!it.isDisposed){ try{ it.onError(e)}catch (e:Exception){e.printStackTrace()}}
        }
    }

    protected fun <T> sendObserver(single: Single<out T>): Single<out T>{
        return single.subscribeOn(Schedulers.newThread())
                .onErrorResumeNext({ return@onErrorResumeNext Single.error(it) }) }
    val request
        get() =   Request.Builder().apply {
            header("App-Key", SbxCore.prefs!!.appKey)
            if(!SbxCore.prefs!!.token.equals("")){
                header("Authorization", "Bearer ${SbxCore.prefs!!.token}")
            }
        }

    val requestJSON
        get() = request.apply { header("Content-Type", "application/json") }
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
    val config: String = "/domain/v1/list/app",
    val cloudscript_run: String = "/cloudscript/v1/run"
)
