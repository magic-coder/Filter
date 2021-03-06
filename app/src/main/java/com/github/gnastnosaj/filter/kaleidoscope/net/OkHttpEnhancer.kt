package com.github.gnastnosaj.filter.kaleidoscope.net

import android.text.TextUtils
import com.github.gnastnosaj.boilerplate.Boilerplate
import com.github.gnastnosaj.filter.magneto.util.NetworkUtil
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import java.io.File

object OkHttpEnhancer {
    private const val MAX_STALE = 30
    private const val MAX_AGE = 60 * 60

    fun OkHttpClient.Builder.enhance() {
        if (Boilerplate.DEBUG) {
            val logging = HttpLoggingInterceptor()
            logging.level = Level.BASIC
            addNetworkInterceptor(logging)
        }
        cache(Cache(File(Boilerplate.getInstance().externalCacheDir, "okHttpCache"), 100 * 1024 * 1024))
        addNetworkInterceptor { chain ->
            var response = chain.proceed(chain.request())
            val cacheControl = response.cacheControl().toString()
            if (NetworkUtil.isAvailable(Boilerplate.getInstance()) && (cacheControl.contains("no-store") || cacheControl.contains("must-revalidate") || cacheControl.contains("no-cache") || cacheControl.contains("max-age=0"))) {
                response = response.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, max-age=$MAX_AGE")
                        .build()
            }
            response
        }
        addInterceptor { chain ->
            var request = chain.request()
            if (!NetworkUtil.isAvailable(Boilerplate.getInstance()) && TextUtils.isEmpty(request.cacheControl().toString())) {
                request = request.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, only-if-cached, max-stale=$MAX_STALE")
                        .build()
            }
            chain.proceed(request)
        }
    }
}