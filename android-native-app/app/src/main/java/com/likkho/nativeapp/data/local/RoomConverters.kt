package com.likkho.nativeapp.data.local

import androidx.room.TypeConverter
import com.likkho.nativeapp.data.model.CanvasAudioAttachment
import com.likkho.nativeapp.data.model.CanvasDraggableImage
import org.json.JSONArray
import org.json.JSONObject

class RoomConverters {

    @TypeConverter
    fun fromCanvasImages(images: List<CanvasDraggableImage>?): String {
        if (images.isNullOrEmpty()) return "[]"
        val arr = JSONArray()
        for (img in images) {
            val obj = JSONObject().apply {
                put("id", img.id)
                put("filePath", img.filePath)
                put("dataUrl", img.dataUrl ?: "")
                put("x", img.x.toDouble())
                put("y", img.y.toDouble())
                put("width", img.width.toDouble())
                put("height", img.height.toDouble())
                put("opacity", img.opacity.toDouble())
                put("rotation", img.rotation.toDouble())
                put("layer", img.layer)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    @TypeConverter
    fun toCanvasImages(raw: String?): List<CanvasDraggableImage> {
        if (raw.isNullOrBlank() || raw == "[]") return emptyList()
        val list = mutableListOf<CanvasDraggableImage>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CanvasDraggableImage(
                        id = obj.optString("id", ""),
                        filePath = obj.optString("filePath", ""),
                        dataUrl = obj.optString("dataUrl", null),
                        x = obj.optDouble("x", 0.0).toFloat(),
                        y = obj.optDouble("y", 0.0).toFloat(),
                        width = obj.optDouble("width", 200.0).toFloat(),
                        height = obj.optDouble("height", 200.0).toFloat(),
                        opacity = obj.optDouble("opacity", 1.0).toFloat(),
                        rotation = obj.optDouble("rotation", 0.0).toFloat(),
                        layer = obj.optString("layer", "foreground")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @TypeConverter
    fun fromAudioAttachments(audios: List<CanvasAudioAttachment>?): String {
        if (audios.isNullOrEmpty()) return "[]"
        val arr = JSONArray()
        for (audio in audios) {
            val obj = JSONObject().apply {
                put("id", audio.id)
                put("name", audio.name)
                put("format", audio.format)
                put("filePath", audio.filePath)
                put("durationSec", audio.durationSec.toDouble())
                put("createdAt", audio.createdAt)
                put("x", audio.x.toDouble())
                put("y", audio.y.toDouble())
                put("width", audio.width.toDouble())
                put("height", audio.height.toDouble())
                put("rotation", audio.rotation.toDouble())
                put("playbackRate", audio.playbackRate.toDouble())
                put("layer", audio.layer)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    @TypeConverter
    fun toAudioAttachments(raw: String?): List<CanvasAudioAttachment> {
        if (raw.isNullOrBlank() || raw == "[]") return emptyList()
        val list = mutableListOf<CanvasAudioAttachment>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CanvasAudioAttachment(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", "Voice Note"),
                        format = obj.optString("format", "wav"),
                        filePath = obj.optString("filePath", ""),
                        durationSec = obj.optDouble("durationSec", 0.0).toFloat(),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        x = obj.optDouble("x", 0.0).toFloat(),
                        y = obj.optDouble("y", 0.0).toFloat(),
                        width = obj.optDouble("width", 260.0).toFloat(),
                        height = obj.optDouble("height", 60.0).toFloat(),
                        rotation = obj.optDouble("rotation", 0.0).toFloat(),
                        playbackRate = obj.optDouble("playbackRate", 1.0).toFloat(),
                        layer = obj.optString("layer", "foreground")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
