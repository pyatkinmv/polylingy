package com.polylingy.core.content

import com.polylingy.content.Block
import com.polylingy.content.Content
import com.polylingy.content.Image
import com.polylingy.content.images
import com.polylingy.content.Path
import com.polylingy.content.Problem
import com.polylingy.content.validate
import com.polylingy.content.walk

private val Accent = Regex("#[0-9A-Fa-f]{6}")

/**
 * Everything wrong with this course, each with its path: the course structure here, the content of units,
 * topics and exercises by their own validate(). A `seeAlso` link must point at a theory section of its unit
 * or at a unit of the course.
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
    rules?.let { addAll(it.validate(root / "rules")) }
    when {
        units.isNotEmpty() && topics.isNotEmpty() -> error(root, "has both units and topics; a course has one or the other")
        units.isEmpty() && topics.isEmpty() -> error(root, "needs units or topics")
    }

    val unitIds = units.map { it.id.toString() }.toSet()

    /** Checks a topic and collects its pages (explanations) for the link check. */
    fun topic(topic: Topic, at: Path, pages: MutableList<Pair<Path, Content>>) {
        if (topic.title.isBlank()) error(at / "title", "must not be empty")
        topic.explanation?.let {
            addAll(it.validate(at / "explanation"))
            pages += at / "explanation" to it
        }
        if (topic.exercises.isEmpty()) error(at / "exercises", "needs at least one exercise")
        topic.exercises.forEachIndexed { e, exercise ->
            val exerciseAt = (at / "exercises")[e]
            addAll(exercise.validate(exerciseAt))
            exercise.explanation?.let { pages += exerciseAt / "explanation" to it }
        }
    }

    /** [sections] are the labels of the unit's theory, or null where there is no unit. */
    fun links(pages: List<Pair<Path, Content>>, sections: Set<String>?) {
        for ((pageAt, page) in pages) for ((at, block) in page.walk(pageAt)) {
            if (block !is Block.SeeAlso) continue
            block.links.forEachIndexed { i, link ->
                val linkAt = (at / "links")[i]
                link.theory?.let {
                    when {
                        sections == null -> error(linkAt / "theory", "a course without units has no theory to link to")
                        it !in sections -> error(linkAt / "theory", "this unit's theory has no section \"$it\"")
                    }
                }
                link.unit?.let { if (it !in unitIds) error(linkAt / "unit", "this course has no unit with id \"$it\"") }
            }
        }
    }

    units.forEachIndexed { u, unit ->
        val at = (root / "units")[u]
        if (unit.title.isBlank()) error(at / "title", "must not be empty")
        unit.subtitle?.let { addAll(it.validate(at / "subtitle")) }
        val pages = mutableListOf<Pair<Path, Content>>()
        unit.theory?.let {
            addAll(it.validate(at / "theory"))
            pages += at / "theory" to it
        }
        if (unit.topics.isEmpty()) error(at / "topics", "needs at least one topic")
        unit.topics.forEachIndexed { t, topic -> topic(topic, (at / "topics")[t], pages) }
        links(pages, unit.theory.sectionLabels())
    }

    val pages = mutableListOf<Pair<Path, Content>>()
    topics.forEachIndexed { t, topic -> topic(topic, (root / "topics")[t], pages) }
    links(pages, null)
}

/** Labels of the theory's top-level sections ("A", "B", …). */
private fun Content?.sectionLabels(): Set<String> =
    (this as? Content.Blocks)?.blocks.orEmpty().filterIsInstance<Block.Section>().map { it.label }.toSet()

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
