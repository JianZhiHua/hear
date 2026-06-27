package com.qingyi.hear.core.network

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** JSON 解析的防御性辅助扩展，统一处理字段缺失 / 类型不一致 / JsonNull 的情况。 */

fun JsonElement?.asObj() = this as? kotlinx.serialization.json.JsonObject

fun JsonElement?.asArr() = this as? kotlinx.serialization.json.JsonArray

fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.content

fun JsonElement?.long(): Long? = str()?.toLongOrNull()

fun JsonElement?.int(): Int? = str()?.toIntOrNull()
