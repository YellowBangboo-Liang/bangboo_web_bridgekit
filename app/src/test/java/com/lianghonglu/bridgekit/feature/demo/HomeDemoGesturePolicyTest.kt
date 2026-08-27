package com.lianghonglu.bridgekit.feature.demo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDemoGesturePolicyTest {
    @Test
    fun `超过阈值的纵向拖动交给首页滚动容器`() {
        assertTrue(shouldHandOffHomeScroll(deltaX = 4f, deltaY = 20f, touchSlop = 8))
    }

    @Test
    fun `横向或轻微手势保留给 H5 按钮`() {
        assertFalse(shouldHandOffHomeScroll(deltaX = 20f, deltaY = 4f, touchSlop = 8))
        assertFalse(shouldHandOffHomeScroll(deltaX = 2f, deltaY = 6f, touchSlop = 8))
    }
}
