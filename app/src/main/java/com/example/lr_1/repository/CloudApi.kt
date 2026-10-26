package com.example.lr_1.repository

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface CloudApi {
    @POST
    suspend fun upload(@Url endpoint: String, @Body payload: SensorPayload): Response<JsonObject>

    @GET
    suspend fun readCommand(@Url endpoint: String): RemoteCommandPayload
}

object CloudServiceFactory {
    fun create(baseUrl: String): CloudApi {
        val normalized = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudApi::class.java)
    }
}
