package com.home.library.di

import android.util.Log
import com.home.library.BuildConfig
import com.home.library.data.remote.KakaoBookApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://dapi.kakao.com/"
    private const val TAG = "HomeLib"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS) // 연결 3s
            .readTimeout(5, TimeUnit.SECONDS)    // 읽기 5s
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")
                    .build()
                chain.proceed(request)
            }

        // 요청/응답 로깅은 debug 빌드에만. Authorization 헤더는 마스킹(키 노출 방지).
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor { message -> Log.d(TAG, message) }
                .apply { level = HttpLoggingInterceptor.Level.BODY }
                .apply { redactHeader("Authorization") }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideKakaoBookApi(retrofit: Retrofit): KakaoBookApi =
        retrofit.create(KakaoBookApi::class.java)
}
