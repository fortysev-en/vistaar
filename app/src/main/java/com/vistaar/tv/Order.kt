package com.vistaar.tv

import android.view.KeyEvent
import android.widget.Toast

/** The viewer's own order for the header tabs and the category lists, kept in preferences under "ord.<key>". Hold OK on an item to move it. */
object Order {
    private fun saved(key: String): List<String> = Store.prefs.getString("ord.$key", "")!!.split("|").filter { it.isNotEmpty() }
    fun save(key: String, ids: List<String>) = Store.prefs.edit().putString("ord.$key", ids.joinToString("|")).apply()
    fun resetAll() { val e = Store.prefs.edit(); Store.prefs.all.keys.filter { it.startsWith("ord.") }.forEach { e.remove(it) }; e.apply() }

    /** [items] in the saved order; anything the saved order doesn't know (a new category) keeps its default place after the known ones. */
    fun <T> arrange(items: List<T>, key: String, id: (T) -> String): List<T> = arrange(items, saved(key), id)

    fun <T> arrange(items: List<T>, order: List<String>, id: (T) -> String): List<T> {
        if (order.isEmpty()) return items
        val rank = order.withIndex().associate { it.value to it.index }
        val known = items.filter { id(it) in rank }.sortedBy { rank.getValue(id(it)) }
        return known + items.filter { id(it) !in rank }
    }

    fun <T> moved(list: List<T>, from: Int, to: Int): List<T> = list.toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Hold-OK reorder. [begin] starts it for the item at a position; while it is on the arrow keys move the item ([prev]/[next] keys), and any other key (OK, Back, the
 * opposite axis) drops it. [pinned] leading items ("All channels", the search button) cannot be moved or passed.
 */
internal class Mover(private val prev: Int, private val next: Int, private val pinned: Int, private val size: () -> Int,
                     private val move: (Int, Int) -> Unit, private val changed: (Int) -> Unit,
                     private val radius: Int, private val view: (Int) -> android.view.View?) {
    var on = false; private set
    var cur = -1; private set
    private var armed = false // the OK press that started it is still down: its repeats and release must not drop the item

    fun begin(ctx: android.content.Context, pos: Int): Boolean {
        if (pos < pinned) return false
        on = true; armed = true; cur = pos
        Toast.makeText(ctx, "Move it, then press OK", Toast.LENGTH_SHORT).show()
        changed(pos); lift(); return true
    }

    /** The item being moved is blue with a white ring, so it can't be mistaken for the plain focus. Call again after its row is rebound. */
    fun lift() { if (on) view(cur)?.lifted(radius, true) }

    /** True when the key was used here. */
    fun key(pos: Int, code: Int, e: KeyEvent): Boolean {
        if (!on) return false
        val center = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
        if (center && armed) { if (e.action == KeyEvent.ACTION_UP) armed = false; return true }
        if (e.action != KeyEvent.ACTION_DOWN) return true
        val to = when (code) { prev -> pos - 1; next -> pos + 1; else -> -1 }
        if (to == -1) { on = false; view(pos)?.lifted(radius, false); changed(pos); return true }
        if (to in pinned until size()) { cur = to; move(pos, to); lift() }
        return true
    }
}
