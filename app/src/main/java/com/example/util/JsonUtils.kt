package com.example.util

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object JsonUtils {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun <T> toJson(data: T): String {
        return try {
            val adapter = moshi.adapter(Any::class.java)
            adapter.toJson(data)
        } catch (e: Exception) {
            "{}"
        }
    }

    fun <T> fromJson(json: String, clazz: Class<T>): T? {
        return try {
            val adapter = moshi.adapter(clazz)
            adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun <T> fromJsonList(json: String, clazz: Class<T>): List<T> {
        return try {
            val type = Types.newParameterizedType(List::class.java, clazz)
            val adapter = moshi.adapter<List<T>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
