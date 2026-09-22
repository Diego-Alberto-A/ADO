package com.example.crosswalker.data

import android.content.Context
import com.example.crosswalker.game.*
import org.json.JSONObject

class GameRepository(context: Context) {
    private val prefs = context.getSharedPreferences("crosswalker", Context.MODE_PRIVATE)
    private val root = JSONObject(context.assets.open("campaign.json").bufferedReader().use { it.readText() })
    val worlds = root.getJSONArray("worlds").let { a -> List(a.length()) { a.getString(it) } }
    val tools = root.getJSONArray("tools").let { a -> List(a.length()) { i -> a.getJSONObject(i).let { Tool(it.getString("id"),it.getString("label"),it.getString("icon"),it.getInt("cost"),it.getString("description"),it.getString("category")) } } }
    val levels = root.getJSONArray("levels").let { a -> List(a.length()) { i -> a.getJSONObject(i).let { l ->
        Level(l.getInt("id"),l.getInt("world"),l.getString("title"),l.getString("lesson"),l.getInt("budget"),l.getBoolean("night"),
            l.getJSONArray("lanes").let { b -> List(b.length()) { j -> b.getJSONObject(j).let { Lane(it.getDouble("y").toFloat(),it.getDouble("speed").toFloat(),it.getDouble("offset").toFloat(),it.getString("car")) } } },
            l.getJSONArray("walkers").let { b -> List(b.length()) { j -> b.getJSONObject(j).let { Walker(it.getString("id"),it.getString("name"),it.getString("kind"),it.getDouble("speed").toFloat(),it.getString("sprite")) } } })
    } } }
    fun stars(id: Int) = prefs.getInt("stars_$id",0)
    fun unlocked(id: Int) = id == 1 || stars(id-1)>0
    fun save(id: Int, stars: Int) { prefs.edit().putInt("stars_$id", maxOf(stars,stars(id))).apply() }
    var sound: Boolean
        get() = prefs.getBoolean("sound",true)
        set(value) { prefs.edit().putBoolean("sound",value).apply() }
    var guidance: Boolean
        get() = prefs.getBoolean("guidance",true)
        set(value) { prefs.edit().putBoolean("guidance",value).apply() }
}
