package com.polylingy.core

import kotlinx.serialization.json.Json

/**
 * The JSON format of course documents and journal events — the data contract shared
 * by every component that stores or exchanges them.
 */
val PolylingyJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    explicitNulls = false
}
