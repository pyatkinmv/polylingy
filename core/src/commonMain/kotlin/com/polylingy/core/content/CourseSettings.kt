package com.polylingy.core.content

import com.polylingy.core.ports.CourseSettingsRepository

/** The settings of [course]: the stored ones, or — for a course stored before settings existed — the rules of its document. */
fun CourseSettingsRepository.of(course: Course): RulesSpec = get(course.id) ?: (course.rules ?: RulesSpec()).resolved()
