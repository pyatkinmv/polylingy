package com.polylingy.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

/**
 * A course that ships inside the installer: `bundled/manifest.json` says which course it is (its id and [version]) so that
 * nothing big has to be read to find out, and `bundled/<file>` is the course as a zip. [source] reads and unpacks it.
 */
class BundledCourse(val id: Uuid, val version: Int, private val file: String) {
    fun source(): CourseSource = requireNotNull(BundledCourse::class.java.getResource("/bundled/$file")) { "bundled/$file is not in the app" }
        .readBytes().let { CourseArchive.read(file, it) }

    companion object {
        /** The bundled course, or null when this build has none. */
        fun find(): BundledCourse? {
            val manifest = BundledCourse::class.java.getResource("/bundled/manifest.json")?.readText() ?: return null
            val json = Json.parseToJsonElement(manifest).jsonObject
            return BundledCourse(
                Uuid.parse(json.getValue("id").jsonPrimitive.content),
                json.getValue("version").jsonPrimitive.int,
                json.getValue("file").jsonPrimitive.content,
            )
        }
    }
}
