package com.polylingy.client

import com.polylingy.core.content.CourseService
import com.polylingy.core.ports.Clock

/** Imports the bundled sample course on first launch, when there are no courses at all. */
fun seedSampleCourse(storage: LocalStorage, clock: Clock, sampleJson: String) {
    if (storage.courses.all().isNotEmpty()) return
    CourseService(storage.courses, clock).import(CourseFile.parseCourse(sampleJson, clock.now().instant, clock.now().zone))
}
