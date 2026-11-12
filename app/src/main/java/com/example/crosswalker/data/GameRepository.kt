package com.example.crosswalker.data

import android.content.Context
import com.example.crosswalker.game.*
import org.json.JSONObject

class GameRepository(context: Context) {
    private val prefs = context.getSharedPreferences("crosswalker", Context.MODE_PRIVATE)
    private val root = JSONObject(context.assets.open("campaign.json").bufferedReader().use { it.readText() })
    val worlds = root.getJSONArray("worlds").let { a -> List(a.length()) { a.getString(it) } }
    val tools = root.getJSONArray("tools").let { a -> List(a.length()) { parseTool(a.getJSONObject(it)) } }
    val levels = root.getJSONArray("levels").let { a -> List(a.length()) { parseLevel(a.getJSONObject(it)) } }
    fun stars(id: Int) = prefs.getInt("stars_$id",0)
    fun unlocked(id: Int) = id == 1 || stars(id-1)>0
    fun save(id: Int, stars: Int) { prefs.edit().putInt("stars_$id", maxOf(stars,stars(id))).apply() }
    fun resetProgress() {
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith("stars_") }.forEach { remove(it) }
            putBoolean("tutorial_done", false)
        }.apply()
    }
    var sound: Boolean
        get() = prefs.getBoolean("sound",true)
        set(value) { prefs.edit().putBoolean("sound",value).apply() }
    var guidance: Boolean
        get() = prefs.getBoolean("guidance",true)
        set(value) { prefs.edit().putBoolean("guidance",value).apply() }
    var tutorialDone: Boolean
        get() = prefs.getBoolean("tutorial_done",false)
        set(value) { prefs.edit().putBoolean("tutorial_done",value).apply() }
}
