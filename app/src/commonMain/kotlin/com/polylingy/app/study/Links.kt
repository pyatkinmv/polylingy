package com.polylingy.app.study

import com.polylingy.content.Block
import com.polylingy.content.Content
import com.polylingy.content.compose.LinkNames
import com.polylingy.content.walk
import com.polylingy.content.Path
import com.polylingy.core.content.Course
import kotlin.uuid.Uuid

/** Where a `seeAlso` link leads inside a course: a unit's theory, scrolled to [section] if there is one. */
data class LinkTarget(val unitId: Uuid, val section: Uuid?)

/** Resolves a course's `seeAlso` links: what each one is called on screen and where it leads. */
class CourseLinks(private val course: Course) {
    private class Found(val unitId: Uuid, val name: String)

    private val sections: Map<Uuid, Found> = buildMap {
        course.units.forEachIndexed { u, unit ->
            val theory = unit.theory as? Content.Blocks ?: return@forEachIndexed
            for ((_, block) in theory.walk(Path.ROOT)) {
                if (block !is Block.Section || block.id == null) continue
                val title = block.title?.plainText()
                val name = listOfNotNull(block.label, title).joinToString(" · ").ifEmpty { "Theory" }
                put(block.id!!, Found(unit.id, name))
            }
        }
    }

    val names = LinkNames { link ->
        link.section?.let { sections[it]?.name }
            ?: link.unit?.let { id ->
                val index = course.units.indexOfFirst { it.id == id }
                if (index < 0) null else "Unit ${index + 1} · ${course.units[index].title}"
            }
    }

    fun target(link: Block.Link): LinkTarget? =
        link.section?.let { id -> sections[id]?.let { LinkTarget(it.unitId, id) } }
            ?: link.unit?.takeIf { id -> course.units.any { it.id == id } }?.let { LinkTarget(it, null) }
}
