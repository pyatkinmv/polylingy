package com.polylingy.content

/** Valid content of every kind, as a course author (or Claude) would write it — the examples of content-format.md. */
object Samples {
    const val ID = "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d"
    const val SECTION_A = "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2e"

    val gaps = """
        { "id": "$ID", "type": "gaps",
          "instruction": "Complete the sentences. Use the present continuous.",
          "example": "Please be quiet. I **’m working**. (work)",
          "wordBank": ["sing", "wear", "get"],
          "items": [
            { "text": "Listen! Somebody {{is singing}} next door.", "hint": "sing" },
            { "text": "Why {{are}} you {{wearing}} a coat?", "hint": "wear" },
            { "text": "I {{’m not reading|am not reading}} it now.", "hint": "not / read",
              "note": "Negative: am / is / are + **not** + -ing." } ],
          "explanation": { "format": "blocks", "blocks": [
            { "type": "text", "text": "Negatives and questions keep **-ing**:" },
            { "type": "examples", "items": [ { "text": "I **’m not reading** it now.", "not": "I don’t read it now" } ] },
            { "type": "seeAlso", "links": [ { "section": "$SECTION_A" } ] } ] } }
    """.trimIndent()

    val choice = """
        { "id": "$ID", "type": "choice", "instruction": "Choose the right form.",
          "items": [
            { "text": "Look, it {{’s raining}} again.", "options": ["rains", "’s raining"] },
            { "text": "I {{don’t understand}} this word.", "options": ["don’t understand", "’m not understanding"],
              "note": "*Understand* is a state verb: no -ing." } ] }
    """.trimIndent()

    val pictures = """
        { "id": "$ID", "type": "pictures", "instruction": "What are they doing?", "wordBank": ["cook", "swim"],
          "items": [
            { "image": { "src": "images/1-cook.png", "alt": "A man at a stove" }, "text": "He {{’s cooking|is cooking}}." },
            { "image": { "src": "https://img.polylingy.app/en/swim.png", "alt": "Two girls in a pool" },
              "text": "They {{’re swimming|are swimming}}." } ] }
    """.trimIndent()

    val table = """
        { "id": "$ID", "type": "table", "instruction": "Complete the table.",
          "head": ["", "positive", "question"],
          "rows": [
            ["I", "I {{am}} working", "{{Am}} I working?"],
            ["she", "she {{is}} working", "{{Is}} she working?"] ] }
    """.trimIndent()

    val match = """
        { "id": "$ID", "type": "match", "instruction": "Match the beginnings and the endings.",
          "pairs": [
            ["I’m tired,", "so I’m going to bed."],
            ["Be quiet!", "The baby is sleeping."],
            [{ "image": { "src": "images/dan.png", "alt": "Dan in the shower" } }, "He’s having a shower."] ],
          "extra": ["It rains a lot here."] }
    """.trimIndent()

    val order = """
        { "id": "$ID", "type": "order", "instruction": "Put the words in order.",
          "items": [
            { "answer": "What are you doing tonight?" },
            { "answer": "Is it raining now?", "accept": ["Is it now raining?"] } ] }
    """.trimIndent()

    /** Choice with its places in the text: each blank lists its options, the right one first. */
    val choiceInText = """
        { "id": "$ID", "type": "choice", "instruction": "Choose the more likely form in each place.",
          "items": [
            { "text": "Thanks for your message, and I {{apologise|’m apologising}} for not writing sooner.\nI {{admit|’m admitting}} the first month was hard." } ] }
    """.trimIndent()

    val expand = """
        { "id": "$ID", "type": "expand", "instruction": "Expand one set of notes to finish each reply.",
          "wordBank": ["constantly / check / phone", "always / borrow / charger"],
          "example": "**A** Where are my keys again?\n**B** You’re **forever losing your keys**.",
          "items": [
            { "text": "**A** Sorry — one quick message.\n**B** You’re {{constantly checking your phone}}.",
              "keys": [["constantly", "checking", "phone"]] },
            { "text": "**A** Can I take yours? Mine’s flat.\n**B** You’re {{always borrowing my charger|always borrowing my phone charger}}.",
              "keys": [["always", "borrowing", "charger"]], "note": "After *always* the verb takes **-ing**." } ] }
    """.trimIndent()

    val fix = """
        { "id": "$ID", "type": "fix", "instruction": "Correct the verbs in italics where they are wrong.",
          "items": [
            { "text": "We {{had been driving}} for six hours when the car {{had been breaking => broke}} down." },
            { "text": "a  Once you {{signed => ’ve signed|have signed}} the form, you can’t cancel.\nb  Once she {{signed}}, the deal was done." } ] }
    """.trimIndent()

    /** Every layer at once: a look and an aside around gaps. */
    val layers = """
        { "id": "$ID", "type": "gaps", "instruction": "Complete the email.",
          "look": { "type": "email", "from": "Maya", "to": "Theo", "subject": "The strangest commute ever" },
          "aside": { "image": { "src": "images/tram.png", "alt": "A tram stop", "caption": "My tram stop" }, "side": "left", "size": "small" },
          "wordBank": ["wait", "drop"],
          "items": [ { "text": "I {{was waiting}} for the tram when a man {{dropped}} his phone." } ] }
    """.trimIndent()

    val article = """
        { "id": "$ID", "type": "fix",
          "look": { "type": "article", "title": "Five minutes with our new head of design", "source": "Staff magazine" },
          "aside": { "image": { "src": "https://img.polylingy.app/en/ana.png", "alt": "Ana" } },
          "items": [ { "text": "I {{have worked}} here since May and {{have been finishing => have finished}} two projects." } ] }
    """.trimIndent()

    val exercises = mapOf(
        "gaps" to gaps, "choice" to choice, "pictures" to pictures, "table" to table, "match" to match, "order" to order,
        "choiceInText" to choiceInText, "expand" to expand, "fix" to fix, "layers" to layers, "article" to article,
    )

    /** One sample of each type, to put any look or aside on. */
    val ofEachType = listOf(gaps, choice, pictures, table, match, order, expand, fix)

    /** Section A of a textbook unit, using every block type. */
    val theory = """
        { "format": "blocks", "blocks": [
          { "type": "section", "id": "$SECTION_A", "label": "A", "title": "Happening now", "blocks": [
            { "type": "text", "text": "Look at this situation:" },
            { "type": "situation",
              "aside": { "type": "image", "src": "images/u1-cycling.png", "alt": "A man cycling to work", "caption": "Tom on his way to work" },
              "blocks": [
                { "type": "text", "text": "Tom is on his bike. He**’s cycling** to work.\nHe is cycling *now*." },
                { "type": "table", "rows": [
                  ["I", "**am**", "(= I’m)", { "text": "**cycling**\n**working**\n**doing** etc.", "rowSpan": 3 }],
                  ["he/she/it", "**is**", "(= he’s etc.)"],
                  ["we/you/they", "**are**", "(= we’re etc.)"] ] } ] } ] },
          { "type": "section", "label": "B", "blocks": [
            { "type": "examples", "items": [
              { "text": "Turn the music down. I**’m trying** to sleep.", "not": "I try" },
              { "text": "What **are** you **reading**?" } ] },
            { "type": "quote", "text": "I’m writing a short story this month.", "who": "Maria",
              "aside": { "type": "image", "src": "images/maria.png", "alt": "Maria on the phone" } } ] },
          { "type": "section", "label": "C", "title": "Things that are changing", "blocks": [
            { "type": "words", "words": ["getting", "becoming", "changing"] },
            { "type": "table", "head": ["", "positive"], "rows": [["I", "I’m getting"]] },
            { "type": "tip", "blocks": [ { "type": "text", "text": "Use the present continuous for changes." } ] },
            { "type": "seeAlso", "links": [ { "section": "$SECTION_A" }, { "unit": "$ID" } ] } ] } ] }
    """.trimIndent()
}
