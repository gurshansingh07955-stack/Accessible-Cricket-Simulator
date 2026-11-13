package com.cricketsim.persistence

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Makes a match saved by an older build readable by a newer one. Gson builds the game's classes without running their constructors, so a list or a map that the saved JSON does not have
 * (because it was added to a class after the match was saved) would come out null in a Kotlin property that is never null, and the game would crash when it reads it. Before the JSON is read,
 * [fill] walks it against the classes as they are now and adds an empty list or map wherever one is missing (numbers and booleans already read as 0 and false). Fields the new code no longer has are
 * ignored by Gson. This covers the usual change (a new list or map in a saved class); a changed meaning or a new object field cannot be repaired, and the crash guard (CrashGuard) catches what is left.
 */
object SnapshotRepair {

    private const val MAX_DEPTH = 14

    fun fill(json: JsonElement, type: Type, depth: Int = 0) {
        if (depth > MAX_DEPTH) return
        when (type) {
            is Class<*> -> if (json is JsonObject) fillObject(json, type, depth)
            is ParameterizedType -> {
                val raw = type.rawType as? Class<*> ?: return
                val args = type.actualTypeArguments
                if (Collection::class.java.isAssignableFrom(raw) && json is JsonArray && args.isNotEmpty()) {
                    json.forEach { fill(it, args[0], depth + 1) }
                } else if (Map::class.java.isAssignableFrom(raw) && json is JsonObject && args.size == 2) {
                    json.entrySet().forEach { fill(it.value, args[1], depth + 1) }
                }
            }
            else -> Unit
        }
    }

    private fun fieldsOf(clazz: Class<*>): List<Field> =
        clazz.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) && !it.isSynthetic }

    private fun fillObject(json: JsonObject, clazz: Class<*>, depth: Int) {
        if (clazz.isPrimitive || clazz.isEnum || clazz.isInterface || clazz.isArray) return
        if (!clazz.name.startsWith("com.cricketsim.")) return
        for (field in fieldsOf(clazz)) {
            val name = field.name
            val child = json.get(name)
            if (child == null) {
                val raw = field.type
                if (List::class.java.isAssignableFrom(raw) || Set::class.java.isAssignableFrom(raw)) {
                    json.add(name, JsonArray())
                } else if (Map::class.java.isAssignableFrom(raw)) {
                    json.add(name, JsonObject())
                }
            } else {
                fill(child, field.genericType, depth + 1)
            }
        }
    }
}
