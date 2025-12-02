package com.example.roleaf.util

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * A custom Gson adapter that safely handles null or non-string values in JSON.
 * It ensures that malformed string fields don't crash your app.
 */
class SafeStringAdapter : TypeAdapter<String>() {

    override fun write(out: JsonWriter, value: String?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value)
        }
    }

    override fun read(reader: JsonReader): String {
        return try {
            when (reader.peek()) {
                JsonToken.STRING -> reader.nextString()
                JsonToken.NULL -> {
                    reader.nextNull()
                    ""
                }
                else -> {
                    reader.skipValue()
                    ""
                }
            }
        } catch (e: Exception) {
            reader.skipValue()
            ""
        }
    }
}

