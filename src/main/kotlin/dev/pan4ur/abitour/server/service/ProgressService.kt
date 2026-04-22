package dev.pan4ur.abitour.server.service

import dev.pan4ur.abitour.server.dto.ApplicantHintDto
import dev.pan4ur.abitour.server.dto.ProgressDto
import dev.pan4ur.abitour.server.dto.ResultDto
import dev.pan4ur.abitour.server.repository.BackendRepository

class ProgressService(
    private val repository: BackendRepository
) {
    fun progress(userId: String, questId: String): ProgressDto {
        val completed = repository.scannedLocations(userId, questId).size
        val total = repository.totalLocations(questId)
        val score = repository.score(userId, questId)
        return ProgressDto(
            userId = userId,
            questId = questId,
            completedLocations = completed,
            totalLocations = total,
            score = score
        )
    }

    fun result(userId: String, questId: String, recommendationText: String): ResultDto {
        val score = repository.score(userId, questId)
        val recommendation = repository.latestRecommendation(userId, questId) ?: recommendationText
        return ResultDto(
            userId = userId,
            questId = questId,
            score = score,
            recommendation = recommendation
        )
    }

    fun hints(userId: String, questId: String): List<ApplicantHintDto> {
        return repository.listHints(userId, questId)
            .filter { !it.type.equals("RECOMMENDATION", ignoreCase = true) }
            .map {
            ApplicantHintDto(
                id = it.id,
                hint = it.hint,
                createdAt = it.createdAt,
                isRead = it.isRead
            )
            }
    }

    fun latestRecommendation(userId: String, questId: String): String? {
        return repository.latestRecommendation(userId, questId)
    }

    fun markHintsRead(userId: String, questId: String) {
        repository.markHintsViewed(userId, questId)
    }
}
