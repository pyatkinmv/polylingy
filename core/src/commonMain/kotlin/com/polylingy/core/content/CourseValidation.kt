package com.polylingy.core.content

import com.polylingy.content.Block
import com.polylingy.content.Content
import com.polylingy.content.Exercise
import com.polylingy.content.ItemResult
import com.polylingy.content.Image
import com.polylingy.content.audios
import com.polylingy.content.images
import com.polylingy.content.Path
import com.polylingy.content.Problem
import com.polylingy.content.validate
import com.polylingy.content.walk
import com.polylingy.core.rules.TextMatcher
import kotlin.uuid.Uuid

private val Accent = Regex("#[0-9A-Fa-f]{6}")

/**
 * Everything wrong with this course, each with its path: the course structure here, the content of units,
 * topics and exercises by their own validate(). A `seeAlso` link must point at a theory section of the course
 * (by the section's id, unique within the course) or at a unit of the course.
 */
fun Course.validate(): List<Problem> = buildList {
    fun error(at: Path, message: String) = add(Problem(at, message))
    val root = Path.ROOT

    when {
        name.isBlank() -> error(root / "name", "must not be empty")
        name.trim().length > CourseService.MAX_NAME_LENGTH -> error(root / "name", "is longer than ${CourseService.MAX_NAME_LENGTH} characters")
    }
    description?.let { addAll(it.validate(root / "description")) }
    theme?.accent?.let { if (!Accent.matches(it)) error(root / "theme" / "accent", "must be a colour like #2F6B5E") }
    if (extraKeys.size > MAX_EXTRA_KEYS) error(root / "extraKeys", "has more than $MAX_EXTRA_KEYS keys")
    extraKeys.forEachIndexed { i, key ->
        if (key.isBlank() || key.length > MAX_EXTRA_KEY_LENGTH) error((root / "extraKeys")[i], "must be 1 to $MAX_EXTRA_KEY_LENGTH characters")
    }
    if (extraKeys.toSet().size != extraKeys.size) error(root / "extraKeys", "has the same key twice")
    rules?.let { addAll(it.validate(root / "rules")) }
    when {
        units.isNotEmpty() && topics.isNotEmpty() -> error(root, "has both units and topics; a course has one or the other")
        units.isEmpty() && topics.isEmpty() -> error(root, "needs units or topics")
    }

    /** Every page of the course with its path, theory pages first; theory sections are what links point at. */
    val theories = mutableListOf<Pair<Path, Content>>()
    val pages = mutableListOf<Pair<Path, Content>>()

    /** Every model answer of an expand item must itself pass its key words, or the item can't be answered right. */
    fun modelAnswers(exercise: Exercise.Expand, at: Path) = exercise.items.forEachIndexed { i, item ->
        if (item.keys.isEmpty() || item.keys.any { it.isEmpty() }) return@forEachIndexed
        for (variant in item.text.blanks.singleOrNull()?.variants.orEmpty()) {
            val match = TextMatcher.keys(variant, item.keys)
            if (match.result != ItemResult.CORRECT) error((at / "items")[i] / "keys", "the model answer \"$variant\" doesn't pass them: ${match.reason}")
        }
    }

    fun topic(topic: Topic, at: Path) {
        if (topic.title.isBlank()) error(at / "title", "must not be empty")
        topic.explanation?.let {
            addAll(it.validate(at / "explanation"))
            pages += at / "explanation" to it
        }
        if (topic.exercises.isEmpty()) error(at / "exercises", "needs at least one exercise")
        topic.exercises.forEachIndexed { e, exercise ->
            val exerciseAt = (at / "exercises")[e]
            addAll(exercise.validate(exerciseAt))
            if (exercise is Exercise.Expand) modelAnswers(exercise, exerciseAt)
            exercise.explanation?.let { pages += exerciseAt / "explanation" to it }
        }
    }

    units.forEachIndexed { u, unit ->
        val at = (root / "units")[u]
        if (unit.title.isBlank()) error(at / "title", "must not be empty")
        unit.subtitle?.let { addAll(it.validate(at / "subtitle")) }
        unit.theory?.let {
            addAll(it.validate(at / "theory"))
            theories += at / "theory" to it
        }
        if (unit.topics.isEmpty()) error(at / "topics", "needs at least one topic")
        unit.topics.forEachIndexed { t, topic -> topic(topic, (at / "topics")[t]) }
    }
    topics.forEachIndexed { t, topic -> topic(topic, (root / "topics")[t]) }

    val sectionIds = mutableSetOf<Uuid>()
    val targets = mutableSetOf<Uuid>()
    for ((pageAt, page) in theories + pages) for ((at, block) in page.walk(pageAt)) {
        val id = (block as? Block.Section)?.id ?: continue
        if (!sectionIds.add(id)) error(at / "id", "another section of this course has the id \"$id\"")
        if (pageAt to page in theories) targets += id
    }

    val unitIds = units.map { it.id }.toSet()
    for ((pageAt, page) in theories + pages) for ((at, block) in page.walk(pageAt)) {
        if (block !is Block.SeeAlso) continue
        block.links.forEachIndexed { i, link ->
            val linkAt = (at / "links")[i]
            link.section?.let { if (it !in targets) error(linkAt / "section", "this course has no theory section with id \"$it\"") }
            link.unit?.let { if (it !in unitIds) error(linkAt / "unit", "this course has no unit with id \"$it\"") }
        }
    }
}

/** Every picture of the course — theory, explanations, exercises — with the path of its `src`. */
fun Course.images(): List<Pair<Path, Image>> = buildList {
    fun topic(topic: Topic, at: Path) {
        topic.explanation?.let { addAll(it.images(at / "explanation")) }
        topic.exercises.forEachIndexed { e, exercise -> addAll(exercise.images((at / "exercises")[e])) }
    }
    units.forEachIndexed { u, unit ->
        val at = (Path.ROOT / "units")[u]
        unit.theory?.let { addAll(it.images(at / "theory")) }
        unit.topics.forEachIndexed { t, it -> topic(it, (at / "topics")[t]) }
    }
    topics.forEachIndexed { t, it -> topic(it, (Path.ROOT / "topics")[t]) }
}

/** Every sound of the course — theory, explanations, exercises — with its path. */
fun Course.audios(): List<Pair<Path, String>> = buildList {
    fun topic(topic: Topic, at: Path) {
        topic.explanation?.let { addAll(it.audios(at / "explanation")) }
        topic.exercises.forEachIndexed { e, exercise -> addAll(exercise.audios((at / "exercises")[e])) }
    }
    units.forEachIndexed { u, unit ->
        val at = (Path.ROOT / "units")[u]
        unit.theory?.let { addAll(it.audios(at / "theory")) }
        unit.topics.forEachIndexed { t, it -> topic(it, (at / "topics")[t]) }
    }
    topics.forEachIndexed { t, it -> topic(it, (Path.ROOT / "topics")[t]) }
}

/** Every file the course refers to — pictures and sounds — with the path of its `src`: what import stores and export packs. */
fun Course.media(): List<Pair<Path, String>> = images().map { (at, image) -> at to image.src } + audios()
