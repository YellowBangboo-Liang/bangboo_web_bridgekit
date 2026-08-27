package com.lianghonglu.bridgekit.feature.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoDestinationTest {
    @Test
    fun `首页默认路由是 Home 并可切换到 About`() {
        val destination = DemoDestination.Home

        assertEquals(DemoDestination.About, destination.openAbout())
    }

    @Test
    fun `About 返回后回到 Home`() {
        assertEquals(DemoDestination.Home, DemoDestination.About.backToHome())
    }
}
