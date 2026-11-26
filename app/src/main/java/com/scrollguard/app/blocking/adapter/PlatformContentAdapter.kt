package com.scrollguard.app.blocking.adapter

import com.scrollguard.app.blocking.AdapterUiHealth
import com.scrollguard.app.blocking.ContentMatchResult
import com.scrollguard.app.blocking.EntryPointTarget
import com.scrollguard.app.blocking.ScreenContext
import com.scrollguard.app.blocking.ScreenNodeSnapshot
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory

interface PlatformContentAdapter {
    fun supports(rule: BlockingRule, activePackage: String): Boolean
    fun evaluate(screen: ScreenContext, rule: BlockingRule): ContentMatchResult
}

object AdapterNodeHelpers {

    fun hasImmersiveVerticalContainer(screen: ScreenContext): Boolean {
        val minWidth = (screen.screenWidthPx * 0.80f).toInt()
        val minHeight = (screen.screenHeightPx * 0.60f).toInt()
        return screen.nodes.any { node ->
            val cls = node.className?.lowercase().orEmpty()
            val isPagerOrList = cls.contains("viewpager") ||
                cls.contains("recyclerview") ||
                cls.contains("framelayout")
            val isLarge = node.boundsWidth >= minWidth && node.boundsHeight >= minHeight
            isPagerOrList && isLarge && (node.isScrollable || cls.contains("viewpager"))
        }
    }

    /**
     * Validates that an entry-point overlay target is a compact, on-screen navigation button/tab
     * (never a huge scrollable feed region or system navigation bar).
     */
    fun toSafeEntryPointTarget(
        node: ScreenNodeSnapshot,
        screen: ScreenContext,
        category: ContentCategory,
        label: String
    ): EntryPointTarget? {
        if (node.boundsWidth <= 0 || node.boundsHeight <= 0) return null
        if (node.boundsLeft < 0 || node.boundsTop < 0) return null
        if (node.boundsRight > screen.screenWidthPx || node.boundsBottom > screen.screenHeightPx) return null

        val maxAllowedWidth = (screen.screenWidthPx * 0.45f).toInt()
        val maxAllowedHeight = (screen.screenHeightPx * 0.16f).toInt()
        if (node.boundsWidth > maxAllowedWidth || node.boundsHeight > maxAllowedHeight) return null

        // Avoid tiny 1px invisible placeholder nodes
        if (node.boundsWidth < 36 || node.boundsHeight < 36) return null

        return EntryPointTarget(
            category = category,
            label = label,
            left = node.boundsLeft,
            top = node.boundsTop,
            right = node.boundsRight,
            bottom = node.boundsBottom
        )
    }

    fun inferUiHealth(screen: ScreenContext): AdapterUiHealth {
        if (screen.nodes.isEmpty()) return AdapterUiHealth.NEEDS_RULE_REVIEW
        val hasAnyIdOrDesc = screen.nodes.any {
            !it.viewIdResourceName.isNullOrBlank() || !it.contentDescription.isNullOrBlank()
        }
        return if (hasAnyIdOrDesc) AdapterUiHealth.HEALTHY else AdapterUiHealth.LIMITED_VISIBILITY
    }
}
