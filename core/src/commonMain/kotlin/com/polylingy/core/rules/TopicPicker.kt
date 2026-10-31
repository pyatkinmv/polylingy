package com.polylingy.core.rules

import kotlin.random.Random
import kotlin.uuid.Uuid

/** Chooses which eligible topic to show next. [eligible] is never empty. */
interface TopicPicker {
    fun pick(eligible: List<Uuid>, current: Uuid?, random: Random): Uuid
}

/** Picks a uniformly random topic from the eligible pool each time. */
object RandomTopicPicker : TopicPicker {
    override fun pick(eligible: List<Uuid>, current: Uuid?, random: Random): Uuid = eligible.random(random)
}

/** Repeats the current topic until it graduates; picks randomly when there is none. */
object FixedTopicPicker : TopicPicker {
    override fun pick(eligible: List<Uuid>, current: Uuid?, random: Random): Uuid =
        current?.takeIf { it in eligible } ?: eligible.random(random)
}
