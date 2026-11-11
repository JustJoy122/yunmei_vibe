package com.yunmei.vibe.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val success: Boolean = false,
    val msg: String? = null,
    val o: LoginUser? = null,
)

@Serializable
data class LoginUser(
    val token: String? = null,
    val userId: String? = null,
    val userTel: String? = null,
    val realName: String? = null,
)

@Serializable
data class SchoolEntry(
    val schoolNo: String? = null,
    val token: String? = null,
    val school: School? = null,
)

@Serializable
data class School(
    val schoolName: String? = null,
    val serverUrl: String? = null,
)

@Serializable
data class ApiMessage(
    val success: Boolean = false,
    val msg: String? = null,
)
