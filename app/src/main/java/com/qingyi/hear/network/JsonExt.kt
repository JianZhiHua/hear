package com.qingyi.hear.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

val HearJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

fun JsonElement?.obj(): JsonObject? = this as? JsonObject

fun JsonElement?.arr(): JsonArray? = this as? JsonArray

fun JsonObject.obj(key: String): JsonObject? = this[key].obj()

fun JsonObject.arr(key: String): JsonArray? = this[key].arr()

fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

fun JsonObject.requiredString(key: String): String =
    string(key) ?: throw IllegalArgumentException("Missing string field: $key")

fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.longOrNull

fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull

fun JsonElement?.asObjectOrEmpty(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

fun JsonElement?.isNullish(): Boolean = this == null || this is JsonNull

fun JsonElement?.jsonObjectOrNull(): JsonObject? = runCatching { this?.jsonObject }.getOrNull()

fun JsonElement?.jsonArrayOrEmpty(): JsonArray = runCatching { this?.jsonArray }.getOrNull() ?: JsonArray(emptyList())

fun JsonElement?.jsonPrimitiveOrNull(): JsonPrimitive? = runCatching { this?.jsonPrimitive }.getOrNull()
