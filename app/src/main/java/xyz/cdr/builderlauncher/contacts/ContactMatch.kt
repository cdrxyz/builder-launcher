package xyz.cdr.builderlauncher.contacts

data class PhoneContact(
    val name: String,
    val number: String,
)

object ContactMatch {
    fun filter(contacts: List<PhoneContact>, query: String, limit: Int = 8): List<PhoneContact> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val digits = q.filter { it.isDigit() }
        val letterQuery = q.any { it.isLetter() }
        val ranked = contacts
            .distinctBy { "${it.name.lowercase()}|${it.number}" }
            .mapNotNull { contact ->
                val name = contact.name.lowercase()
                val numberDigits = contact.number.filter { it.isDigit() }
                val score = when {
                    !letterQuery && digits.isNotEmpty() && numberDigits.contains(digits) -> 1
                    name == q -> 0
                    name.startsWith(q) -> 1
                    name.split(Regex("\\s+")).any { it.startsWith(q) } -> 2
                    name.contains(q) -> 3
                    else -> null
                } ?: return@mapNotNull null
                score to contact
            }
            .sortedWith(compareBy({ it.first }, { it.second.name.lowercase() }))
        return ranked.map { it.second }.take(limit)
    }
}
