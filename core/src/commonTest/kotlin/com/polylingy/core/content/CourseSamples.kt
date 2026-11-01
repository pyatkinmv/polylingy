package com.polylingy.core.content

/** Course files as an author would write them, plus small helpers to break them for error tests. */
object CourseSamples {
    const val COURSE_ID = "0a1b2c3d-0000-4000-8000-000000000001"
    const val UNIT_ID = "0a1b2c3d-0000-4000-8000-000000000002"
    const val TOPIC_ID = "0a1b2c3d-0000-4000-8000-000000000003"
    const val EXERCISE_ID = "0a1b2c3d-0000-4000-8000-000000000004"
    const val SECTION_A = "0a1b2c3d-0000-4000-8000-0000000000a1"
    const val SECTION_B = "0a1b2c3d-0000-4000-8000-0000000000b1"

    /** A course with one unit: theory, two topics, and links between them. */
    val units = """
        { "formatVersion": 1, "id": "$COURSE_ID", "name": "English Grammar in Use",
          "description": "Units on *present* tenses.",
          "theme": { "accent": "#2F6B5E" },
          "settings": { "allowTypos": false },
          "rules": { "preset": "sm2", "grading": { "type": "threshold", "min": 0.75 } },
          "units": [
            { "id": "$UNIT_ID", "title": "Present continuous", "subtitle": "I am doing",
              "theory": { "format": "blocks", "blocks": [
                { "type": "section", "id": "$SECTION_A", "label": "A", "blocks": [ { "type": "text", "text": "He**’s cycling**." } ] },
                { "type": "section", "id": "$SECTION_B", "label": "B", "blocks": [
                  { "type": "seeAlso", "links": [ { "section": "$SECTION_A" }, { "unit": "$UNIT_ID" } ] } ] } ] },
              "topics": [
                { "id": "$TOPIC_ID", "title": "Things happening now",
                  "explanation": { "format": "blocks", "blocks": [ { "type": "seeAlso", "links": [ { "section": "$SECTION_B" } ] } ] },
                  "exercises": [
                    { "id": "$EXERCISE_ID", "type": "gaps", "items": [ { "text": "Listen! Somebody {{is singing}}." } ] },
                    { "type": "choice", "items": [ { "text": "It {{’s raining}}.", "options": ["rains", "’s raining"] } ] } ] },
                { "title": "Questions",
                  "exercises": [ { "type": "order", "items": [ { "answer": "What are you doing?" } ] } ] } ] } ] }
    """.trimIndent()

    /** A course without units and without any ids, as the first draft of a file often is. */
    val flat = """
        { "formatVersion": 1, "name": "Spanish Basics",
          "topics": [
            { "title": "Greetings", "exercises": [ { "type": "gaps", "items": [ { "text": "{{Hola}}!" } ] } ] },
            { "title": "Family", "exercises": [
              { "type": "match", "pairs": [ ["madre", "mother"], ["padre", "father"] ] } ] } ] }
    """.trimIndent()

    /** The flat course with [topic] JSON as its only topic. */
    fun flatWith(topic: String) = """{ "formatVersion": 1, "name": "X", "topics": [ $topic ] }"""

    fun flatWithExercise(exercise: String) = flatWith("""{ "title": "T", "exercises": [ $exercise ] }""")
}
