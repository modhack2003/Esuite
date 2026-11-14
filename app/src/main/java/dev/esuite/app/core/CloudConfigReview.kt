package dev.esuite.app.core

import kotlinx.serialization.json.*

/** Offline structural review of supplied AWS JSON, never an account scan or effective-policy decision. */
object CloudConfigReview {
    fun review(input: String): String {
        require(input.toByteArray().size <= 2 * 1024 * 1024) { "Configuration exceeds 2 MiB" }
        val root = Json.parseToJsonElement(input).jsonObject; val observations = mutableListOf<String>()
        fun values(e: JsonElement?): List<String> = when (e) { is JsonArray -> e.flatMap(::values); is JsonObject -> e.values.flatMap(::values); is JsonPrimitive -> listOf(e.content); else -> emptyList() }
        val statements = when (val e = root["Statement"]) { is JsonArray -> e.toList(); is JsonObject -> listOf(e); else -> emptyList() }
        statements.forEachIndexed { i, e ->
            val statement = e.jsonObject
            if (statement["Effect"]?.jsonPrimitive?.content == "Allow") {
                if (values(statement["Action"]).any { it.contains('*') }) observations += "Statement $i allows wildcard actions; assess intended breadth and conditions."
                if (values(statement["Resource"]).contains("*")) observations += "Statement $i applies to all resources."
                if (values(statement["Principal"]).contains("*")) observations += "Statement $i has a wildcard principal."
            }
        }
        root["Grants"]?.jsonArray?.forEach { e ->
            val uri = e.jsonObject["Grantee"]?.jsonObject?.get("URI")?.jsonPrimitive?.content.orEmpty()
            if (uri.endsWith("/AllUsers") || uri.endsWith("/AuthenticatedUsers")) observations += "An ACL grant references a public group. Review permissions and effective public-access blocking."
        }
        root["IpPermissions"]?.jsonArray?.forEach { permission ->
            val p = permission.jsonObject
            val ipv4 = p["IpRanges"]?.jsonArray?.any { it.jsonObject["CidrIp"]?.jsonPrimitive?.content == "0.0.0.0/0" } == true
            val ipv6 = p["Ipv6Ranges"]?.jsonArray?.any { it.jsonObject["CidrIpv6"]?.jsonPrimitive?.content == "::/0" } == true
            if (ipv4 || ipv6) observations += "World-reachable ingress appears in supplied rules; this may be intentional for public services."
        }
        return "OFFLINE CONFIGURATION OBSERVATIONS\n" + observations.ifEmpty { listOf("No supported structural observations. This does not establish that the account or resource is secure.") }.joinToString("\n\n") +
            "\n\nNo provider account was contacted. Conditions, SCPs, permission boundaries, resource state and effective privileges are not fully evaluated."
    }
}
