package com.example.myapplication.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.CacheControl
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "http://77.222.32.209:8080/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)

        // === ОТКЛЮЧЕНИЕ КЭШИРОВАНИЯ HTTP ===
        // 1. Отключаем дисковый кэш OkHttp
        .cache(null)

        // 2. Добавляем Network Interceptor — срабатывает ВСЕГДА,
        //    даже если ответ берётся из кэша
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            response.newBuilder()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .removeHeader("ETag")           // Убираем ETag
                .removeHeader("Last-Modified")   // Убираем Last-Modified
                .build()
        }

        // 3. Добавляем Application Interceptor — срабатывает ПЕРЕД кэшем,
        //    принудительно запрещает кэширование на запрос
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("Connection", "close")
                .header("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .cacheControl(CacheControl.Builder().noCache().noStore().build())
                .build()
            chain.proceed(newRequest)
        }
        .build()

    val instance: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}