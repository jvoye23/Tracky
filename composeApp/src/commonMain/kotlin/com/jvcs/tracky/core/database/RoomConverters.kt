package com.jvcs.tracky.core.database

import androidx.room.TypeConverter
import com.jvcs.tracky.core.domain.model.ProjectSession
import kotlinx.serialization.json.Json

class RoomConverters {
    private val jsonHandler = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromProjectRecordList(projectSessions: List<ProjectSession>?): String? {
        if(projectSessions == null) {
            return null
        }
        return jsonHandler.encodeToString(projectSessions)
    }

    @TypeConverter
    fun fromJsonStringToProjectRecordList(jsonString: String?): List<ProjectSession>? {
        if(jsonString == null) {
            return null
        }
        return try {
            jsonHandler.decodeFromString(jsonString)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}