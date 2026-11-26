package com.scrollguard.app.monitoring

import com.scrollguard.app.domain.model.DiagnosticEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory ring buffer for developer diagnostics.
 * Intentionally never persisted to disk and never stores unrelated accessibility text.
 */
@Singleton
class DiagnosticsLogBuffer @Inject constructor() {

    private val idGenerator = AtomicLong(1L)
    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events: StateFlow<List<DiagnosticEvent>> = _events.asStateFlow()

    fun record(
        packageName: String,
        eventType: String,
        matchedRule: String,
        confidence: Float,
        signals: List<String>,
        decision: String
    ) {
        val entry = DiagnosticEvent(
            id = idGenerator.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            packageName = packageName,
            eventType = eventType,
            matchedRule = matchedRule,
            confidence = confidence,
            signals = signals,
            decision = decision
        )
        _events.update { current ->
            (listOf(entry) + current).take(MAX_EVENTS)
        }
    }

    fun clear() {
        _events.value = emptyList()
    }

    companion object {
        private const val MAX_EVENTS = 150
    }
}
