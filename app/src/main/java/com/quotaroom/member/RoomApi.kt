package com.quotaroom.member

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RoomStatus(val token: String, val quota: Long, val used: Long, val blocked: Boolean)

class RoomException(message: String) : Exception(message)

/** Talks to the admin program (admin_room.py) on the same Wi-Fi. */
object RoomApi {
    private fun post(base: String, path: String, body: JSONObject): JSONObject {
        val conn = URL("$base$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val json = JSONObject(stream?.bufferedReader()?.readText() ?: "{}")
            if (code !in 200..299) throw RoomException(json.optString("error", "error $code"))
            return json
        } finally {
            conn.disconnect()
        }
    }

    fun join(base: String, code: String, name: String): RoomStatus {
        val j = post(base, "/api/join", JSONObject().put("code", code).put("name", name))
        return RoomStatus(j.getString("token"), j.getLong("quota"), j.getLong("used"), j.getBoolean("blocked"))
    }

    fun report(base: String, token: String, delta: Long): RoomStatus {
        val j = post(base, "/api/report", JSONObject().put("token", token).put("delta", delta))
        return RoomStatus(token, j.getLong("quota"), j.getLong("used"), j.getBoolean("blocked"))
    }
}
