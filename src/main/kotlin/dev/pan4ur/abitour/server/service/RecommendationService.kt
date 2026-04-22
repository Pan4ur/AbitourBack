package dev.pan4ur.abitour.server.service

class RecommendationService {
    fun buildRecommendation(score: Int): String {
        return when {
            score >= 2 -> "Рекомендуется направление: программная инженерия"
            score == 1 -> "Рекомендуется направление: прикладная информатика"
            else -> "Рекомендуется направление: общетехнический трек с усилением математики"
        }
    }
}
