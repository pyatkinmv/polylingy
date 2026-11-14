package com.polylingy.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The style a course is drawn in: how its details are set off, apart from its colours (the palette). For now the styles
 * differ in how a highlight `==text==` is drawn; more (sharper or rounder corners, and so on) can follow.
 */
@Serializable
enum class CourseStyle {
    /** Marks sit on a coloured background. */
    @SerialName("soft") SOFT,

    /** Marks are underlined, with no background. */
    @SerialName("strict") STRICT,
}
