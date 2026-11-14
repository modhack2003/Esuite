package dev.esuite.app.automation

import dev.esuite.app.core.*
import kotlinx.serialization.json.*

enum class PayloadMode { ONE_POSITION, BROADCAST, PAIRED, COMBINATIONS }

object RequestPlans {
    private val marker = Regex("\\{\\{P([1-4])}}")
    fun payloads(template: RequestDraft, sets: List<List<String>>, mode: PayloadMode): List<RequestDraft> {
        require(sets.size in 1..4 && sets.all { it.isNotEmpty() && it.size <= 50 && it.all { value -> value.length <= 4096 } }) { "Use 1–4 non-empty lists with up to 50 bounded values" }
        val text = template.url + template.body + template.headers.joinToString { it.value }
        val positions = marker.findAll(text).map { it.groupValues[1].toInt() - 1 }.toSet()
        require(positions.isNotEmpty() && positions.all { it < sets.size }) { "Use {{P1}}…{{P4}} markers with corresponding lists" }
        val active = positions.sorted()
        val count: Long = when (mode) {
            PayloadMode.ONE_POSITION -> active.sumOf { sets[it].size.toLong() }
            PayloadMode.BROADCAST -> sets[0].size.toLong()
            PayloadMode.PAIRED -> active.minOf { sets[it].size }.toLong()
            PayloadMode.COMBINATIONS -> active.fold(1L) { a, i -> a * sets[i].size }
        }
        require(count <= 50) { "Plan exceeds 50 requests ($count). Reduce list sizes." }
        val baseline = sets.map { it.first() }
        val rows = when (mode) {
            PayloadMode.ONE_POSITION -> active.flatMap { i -> sets[i].map { value -> baseline.toMutableList().apply { this[i] = value } } }
            PayloadMode.BROADCAST -> sets[0].map { value -> List(sets.size) { value } }
            PayloadMode.PAIRED -> (0 until count.toInt()).map { n -> sets.map { values -> values.getOrElse(n) { values.first() } } }
            PayloadMode.COMBINATIONS -> active.fold(listOf(baseline)) { rows, i -> rows.flatMap { row -> sets[i].map { value -> row.toMutableList().apply { this[i] = value } } } }
        }
        fun replace(text: String, row: List<String>) = marker.replace(text) { row[it.groupValues[1].toInt() - 1] }
        return rows.map { row -> template.copy(url = replace(template.url, row), body = replace(template.body, row), headers = template.headers.map { it.copy(value = replace(it.value, row)) }) }
    }
    fun applyToken(draft: RequestDraft, token: String): RequestDraft = draft.copy(url = draft.url.replace("{{token}}", Codec.formEncode(token).replace("+", "%20")),
        headers = draft.headers.map { it.copy(value = it.value.replace("{{token}}", token)) }, body = draft.body.replace("{{token}}", token))
    fun extractToken(body: String, path: String): String {
        require(path.startsWith('/')) { "Token path must be a JSON Pointer such as /access_token" }
        val result = path.removePrefix("/").split('/').fold(Json.parseToJsonElement(body)) { element, raw ->
            val key = raw.replace("~1", "/").replace("~0", "~")
            when (element) { is JsonObject -> element[key] ?: error("Missing JSON token path"); is JsonArray -> element[key.toInt()]; else -> error("Token path does not resolve") }
        }
        val value = (result as? JsonPrimitive)?.contentOrNull ?: error("Token must be a JSON scalar")
        require(value.length <= 8192 && !value.contains('\r') && !value.contains('\n')) { "Invalid extracted token" }
        return value
    }
}
