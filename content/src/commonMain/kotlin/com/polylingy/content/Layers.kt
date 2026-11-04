package com.polylingy.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The frame around an exercise's body, with a header that says what the text is: an email, an article. A look draws
 * only the frame and its header, never items, so it fits every exercise type. A look of a newer format is [Unknown]
 * and shown as no frame.
 */
@Serializable
sealed interface Look {
    fun validate(at: Path): List<Problem>

    /** An email: [subject] on top, then the sender (with an initial) and the recipient. */
    @Serializable
    @SerialName("email")
    data class Email(val from: String, val to: String? = null, val subject: Inline) : Look {
        override fun validate(at: Path) = checks {
            notBlank(from, at / "from")
            notBlank(to, at / "to")
            text(subject, at / "subject")
        }
    }

    /** A page of a magazine, a newspaper or a blog: a small [source] line over the [title]. */
    @Serializable
    @SerialName("article")
    data class Article(val title: Inline, val source: String? = null) : Look {
        override fun validate(at: Path) = checks {
            text(title, at / "title")
            notBlank(source, at / "source")
        }
    }

    @Serializable(with = UnknownLookSerializer::class)
    data class Unknown(val raw: JsonObject) : Look {
        val type: String get() = raw.typeName()
        override fun validate(at: Path) = checks { unknownType(at, "look", type) }
    }
}

/**
 * One picture next to an exercise's body: inside the frame when there is a [Look]. [side] and [size] are words, not
 * pixels: the app turns the size into a share of the width — left or right ¼, ⅓, ½; top or bottom ⅓, ½, all of it —
 * and keeps the picture's proportions. On a narrow screen left and right become top.
 */
@Serializable
data class Aside(val image: Image, val side: Edge = Edge.RIGHT, val size: Size = Size.MEDIUM) {
    fun validate(at: Path): List<Problem> = image.validate(at / "image")

    @Serializable
    enum class Edge {
        @SerialName("left") LEFT,
        @SerialName("right") RIGHT,
        @SerialName("top") TOP,
        @SerialName("bottom") BOTTOM,
    }

    @Serializable
    enum class Size {
        @SerialName("small") SMALL,
        @SerialName("medium") MEDIUM,
        @SerialName("large") LARGE,
    }
}
