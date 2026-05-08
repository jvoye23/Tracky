package com.jvcs.tracky.core.database

import androidx.room.TypeConverter
import com.jvcs.tracky.core.domain.model.ProjectTask
import kotlinx.serialization.json.Json

class RoomConverters {
    private val jsonHandler = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromProjectRecordList(projectTasks: List<ProjectTask>?): String? {
        if(projectTasks == null) {
            return null
        }
        return jsonHandler.encodeToString(projectTasks)
    }

    @TypeConverter
    fun fromJsonStringToProjectRecordList(jsonString: String?): List<ProjectTask>? {
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