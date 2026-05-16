package com.qingyi.hear.storage

interface CredentialStore {
    fun getCookie(source: String): String?

    fun setCookie(source: String, cookie: String)

    fun clearCookie(source: String)
}
