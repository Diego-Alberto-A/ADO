package com.example.crosswalker.game

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ParsingTest {
    @Test fun parseToolReadsAllFields() {
        val json = JSONObject(
            """{"id":"look","label":"Mirar","icon":"◉","cost":1,"description":"Mira antes de cruzar.","category":"skill"}"""
        )
        assertEquals(Tool("look","Mirar","◉",1,"Mira antes de cruzar.","skill"), parseTool(json))
    }

    @Test fun parseLevelReadsNewFieldsWithDefaultsWhenMissing() {
        val json = JSONObject(
            """{"id":1,"world":0,"title":"Mi primer cruce","lesson":"Toca MIRAR.","budget":3,"night":false,
                "lanes":[{"y":0.5,"speed":0.11,"offset":0.6,"car":"car_coral"}],
                "walkers":[{"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"}],
                "tools":["look"]}"""
        )
        val level = parseLevel(json)
        assertEquals(listOf("look"), level.tools)
        assertEquals(emptyList<String>(), level.newTools)
        assertFalse(level.tutorial)
        assertEquals(emptyList<String>(), level.introLines)
        assertEquals("", level.readyMessage)
        assertEquals(1, level.lanes.size)
        assertEquals("ana", level.walkers.single().id)
    }

    @Test fun parseLevelReadsTutorialFieldsWhenPresent() {
        val json = JSONObject(
            """{"id":1,"world":0,"title":"Mi primer cruce","lesson":"Toca MIRAR.","budget":3,"night":false,
                "lanes":[{"y":0.5,"speed":0.11,"offset":0.6,"car":"car_coral"}],
                "walkers":[{"id":"ana","name":"Ana","kind":"impulsive","speed":0.12,"sprite":"walker_coral"}],
                "tools":["look"],"newTools":["look"],"tutorial":true,
                "introLines":["¡Hola! Soy Ana.","Vamos paso a paso."],"readyMessage":"¡Lanza el plan!"}"""
        )
        val level = parseLevel(json)
        assertEquals(listOf("look"), level.newTools)
        assertTrue(level.tutorial)
        assertEquals(listOf("¡Hola! Soy Ana.","Vamos paso a paso."), level.introLines)
        assertEquals("¡Lanza el plan!", level.readyMessage)
    }
}
