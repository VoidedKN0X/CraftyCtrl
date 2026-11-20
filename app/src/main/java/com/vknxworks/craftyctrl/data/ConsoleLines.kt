package com.vknxworks.craftyctrl.data

/** A console line mentions an error. */
fun consoleIsError(line: String): Boolean = "ERROR" in line || "SEVERE" in line || "Exception" in line

/** A console line mentions a warning. */
fun consoleIsWarn(line: String): Boolean = "WARN" in line

/**
 * One line of the console. [id] is unique within its [ConsoleBuffer] and never reused, so the list on screen
 * can tell lines apart while new ones come in and old ones drop off. What kind of line it is (for its colour
 * and the level filter) is worked out once, here, instead of every time the console is drawn or filtered.
 */
class ConsoleLine(val id: Long, val text: String) {
    val isError: Boolean = consoleIsError(text)
    val isWarn: Boolean = consoleIsWarn(text)

    /** Printed by Crafty itself, not by the server. */
    val fromCrafty: Boolean = text.startsWith("[Crafty]") || text.startsWith("Crafty")
}

/**
 * The console's lines at one moment. It never changes: [ConsoleBuffer] hands out a new one whenever lines
 * are added. Two of them are only equal when they are the same object, so comparing them costs nothing.
 */
class ConsoleLines internal constructor(
    private val backing: ArrayList<ConsoleLine>,
    private val from: Int,
    val size: Int,
) {
    /** The lines, oldest first. */
    val list: List<ConsoleLine> = object : AbstractList<ConsoleLine>(), RandomAccess {
        override val size: Int get() = this@ConsoleLines.size

        override fun get(index: Int): ConsoleLine {
            if (index < 0 || index >= this@ConsoleLines.size) throw IndexOutOfBoundsException("index $index, size ${this@ConsoleLines.size}")
            return backing[from + index]
        }
    }

    fun isEmpty(): Boolean = size == 0

    fun isNotEmpty(): Boolean = size != 0

    /** Whether these are exactly the lines in [texts], in the same order. */
    fun sameText(texts: List<String>): Boolean {
        if (texts.size != size) return false
        var i = 0
        for (text in texts) {
            if (backing[from + i].text != text) return false
            i++
        }
        return true
    }
}

/**
 * Keeps the console's lines without copying all of them every time some are added.
 *
 * New lines go on the end of one growing list, and each [ConsoleLines] is a window on it: the part of the list
 * a window covers is never changed afterwards, so windows handed out earlier stay valid. Once the lines that
 * dropped off the front add up to [max], the list is replaced by a fresh one holding only the lines still in
 * view. Use from one thread only.
 */
class ConsoleBuffer(private val max: Int) {
    private var backing = ArrayList<ConsoleLine>()
    private var from = 0
    private var nextId = 0L

    var lines: ConsoleLines = ConsoleLines(backing, 0, 0)
        private set

    /** Starts again from [texts] (all of them, however many there are). */
    fun replace(texts: List<String>): ConsoleLines {
        val fresh = ArrayList<ConsoleLine>(texts.size + 16)
        for (text in texts) fresh.add(ConsoleLine(nextId++, text))
        backing = fresh
        from = 0
        lines = ConsoleLines(fresh, 0, fresh.size)
        return lines
    }

    /** Adds [texts] at the end and keeps only the last [max] lines. */
    fun append(texts: List<String>): ConsoleLines {
        for (text in texts) backing.add(ConsoleLine(nextId++, text))
        from = maxOf(from, backing.size - max)
        if (from >= max) {
            backing = ArrayList(backing.subList(from, backing.size))
            from = 0
        }
        lines = ConsoleLines(backing, from, backing.size - from)
        return lines
    }
}
