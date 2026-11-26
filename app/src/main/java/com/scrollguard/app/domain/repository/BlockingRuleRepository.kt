package com.scrollguard.app.domain.repository

import com.scrollguard.app.domain.model.BlockingRule
import kotlinx.coroutines.flow.Flow

interface BlockingRuleRepository {
    fun observeAllRules(): Flow<List<BlockingRule>>
    suspend fun getAllRules(): List<BlockingRule>
    suspend fun getEnabledRules(): List<BlockingRule>
    suspend fun getRulesForPackage(packageName: String): List<BlockingRule>
    suspend fun upsertRule(rule: BlockingRule): Long
    suspend fun setRuleEnabled(ruleId: Long, enabled: Boolean)
    suspend fun deleteRule(ruleId: Long)
    suspend fun seedBuiltInRulesIfMissing()
    suspend fun replaceAllRules(rules: List<BlockingRule>)
}
