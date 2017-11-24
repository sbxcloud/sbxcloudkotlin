package com.sbxcloud.library.kotlin.net.http

import okhttp3.*
import okhttp3.internal.http.HttpHeaders
import okhttp3.internal.platform.Platform
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException
import java.util.concurrent.TimeUnit

/**
 * Created by lgguzman on 23/11/17.
 */
class SbxHttpLoggingInterceptor : Interceptor {
    private val UTF8 = Charset.forName("UTF-8")

    enum class Level {
        /** No logs.  */
        NONE,
        /**
         * Logs request and response lines.
         *
         *
         * Example:
         * <pre>`--> POST /greeting http/1.1 (3-byte body)
         *
         * <-- 200 OK (22ms, 6-byte body)
        `</pre> *
         */
        BASIC,
        /**
         * Logs request and response lines and their respective headers.
         *
         *
         * Example:
         * <pre>`--> POST /greeting http/1.1
         * Host: example.com
         * Content-Type: plain/text
         * Content-Length: 3
         * --> END POST
         *
         * <-- 200 OK (22ms)
         * Content-Type: plain/text
         * Content-Length: 6
         * <-- END HTTP
        `</pre> *
         */
        HEADERS,
        /**
         * Logs request and response lines and their respective headers and bodies (if present).
         *
         *
         * Example:
         * <pre>`--> POST /greeting http/1.1
         * Host: example.com
         * Content-Type: plain/text
         * Content-Length: 3
         *
         * Hi?
         * --> END GET
         *
         * <-- 200 OK (22ms)
         * Content-Type: plain/text
         * Content-Length: 6
         *
         * Hello!
         * <-- END HTTP
        `</pre> *
         */
        BODY
    }

    interface Logger {
        fun log(message: String)

        companion object {

            /** A [okhttp3.logging.HttpLoggingInterceptor.Logger] defaults output appropriate for the current platform.  */
            val DEFAULT: okhttp3.logging.HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger { message -> Platform.get().log(0, message, Throwable("error retrojeje")) }
        }
    }

    constructor() : this(okhttp3.logging.HttpLoggingInterceptor.Logger.DEFAULT)

    constructor(logger: okhttp3.logging.HttpLoggingInterceptor.Logger) {
        this.logger = logger
    }

    lateinit var logger: okhttp3.logging.HttpLoggingInterceptor.Logger

    @Volatile private var level: okhttp3.logging.HttpLoggingInterceptor.Level = okhttp3.logging.HttpLoggingInterceptor.Level.NONE

    /** Change the level at which this interceptor logs.  */
    fun setLevel(level: okhttp3.logging.HttpLoggingInterceptor.Level?): SbxHttpLoggingInterceptor {
        if (level == null) throw NullPointerException("level == null. Use Level.NONE instead.")
        this.level = level
        return this
    }

    fun getLevel(): okhttp3.logging.HttpLoggingInterceptor.Level {
        return level
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val level = this.level
        val original = chain.request()
        var requestBuilder: Request.Builder? = null

        requestBuilder = original.newBuilder()

        val request = requestBuilder!!.build()

        if (level == okhttp3.logging.HttpLoggingInterceptor.Level.NONE) {
            return chain.proceed(request)
        }

        val logBody = level == okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        val logHeaders = logBody || level == okhttp3.logging.HttpLoggingInterceptor.Level.HEADERS

        val requestBody = request.body()
        val hasRequestBody = requestBody != null

        val connection = chain.connection()
        val protocol = if (connection != null) connection!!.protocol() else Protocol.HTTP_1_1
        var requestStartMessage = "--> " + request.method() + ' ' + request.url() + ' ' + protocol
        if (!logHeaders && hasRequestBody) {
            requestStartMessage += " (" + requestBody!!.contentLength() + "-byte body)"
        }
        logger.log(requestStartMessage)

        if (logHeaders) {
            if (hasRequestBody) {
                // Request body headers are only present when installed as a network interceptor. Force
                // them to be included (when available) so there values are known.
                if (requestBody!!.contentType() != null) {
                    logger.log("Content-Type: " + requestBody!!.contentType()!!)
                }
                if (requestBody!!.contentLength() != -1L) {
                    logger.log("Content-Length: " + requestBody!!.contentLength())
                }
            }

            val headers = request.headers()
            var i = 0
            val count = headers.size()
            while (i < count) {
                val name = headers.name(i)
                // Skip headers from the request body as they are explicitly logged above.
                if (!"Content-Type".equals(name, ignoreCase = true) && !"Content-Length".equals(name, ignoreCase = true)) {
                    logger.log(name + ": " + headers.value(i))
                }
                i++
            }

            if (!logBody || !hasRequestBody) {
                logger.log("--> END " + request.method())
            } else if (bodyEncoded(request.headers())) {
                logger.log("--> END " + request.method() + " (encoded body omitted)")
            } else {
                val buffer = Buffer()
                requestBody!!.writeTo(buffer)

                var charset: Charset? = UTF8
                val contentType = requestBody!!.contentType()
                if (contentType != null) {
                    charset = contentType!!.charset(UTF8)
                }

                logger.log("")
                logger.log(buffer.readString(charset!!))

                logger.log("--> END " + request.method()
                        + " (" + requestBody!!.contentLength() + "-byte body)")
            }
        }

        val startNs = System.nanoTime()
        val response = chain.proceed(request)
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

        val responseBody = response.body()
        val contentLength = responseBody!!.contentLength()
        val bodySize = if (contentLength != -1L) (contentLength).toString() + "-byte" else "unknown-length"
        logger.log(("<-- " + response.code() + ' ' + response.message() + ' '
                + response.request().url() + " (" + tookMs + "ms" + (if (!logHeaders)
            ", "
                    + bodySize + " body"
        else
            "") + ')'))

        if (logHeaders) {
            val headers = response.headers()
            var i = 0
            val count = headers.size()
            while (i < count) {
                logger.log(headers.name(i) + ": " + headers.value(i))
                i++
            }

            if (!logBody || !HttpHeaders.hasBody(response)) {
                logger.log("<-- END HTTP")
            } else if (bodyEncoded(response.headers())) {
                logger.log("<-- END HTTP (encoded body omitted)")
            } else {
                val source = responseBody!!.source()
                source.request(java.lang.Long.MAX_VALUE) // Buffer the entire body.
                val buffer = source.buffer()

                var charset: Charset? = UTF8
                val contentType = responseBody!!.contentType()
                if (contentType != null) {
                    try {
                        charset = contentType!!.charset(UTF8)
                    } catch (e: UnsupportedCharsetException) {
                        logger.log("")
                        logger.log("Couldn't decode the response body; charset is likely malformed.")
                        logger.log("<-- END HTTP")

                        return response
                    }

                }

                if (contentLength != 0L) {
                    logger.log("")
                    logger.log(buffer.clone().readString(charset!!))
                }

                logger.log("<-- END HTTP (" + buffer.size() + "-byte body)")
            }
        }

        return response
    }

    private fun bodyEncoded(headers: Headers): Boolean {
        val contentEncoding = headers.get("Content-Encoding")
        return contentEncoding != null && !contentEncoding!!.equals("identity", ignoreCase = true)
    }
}