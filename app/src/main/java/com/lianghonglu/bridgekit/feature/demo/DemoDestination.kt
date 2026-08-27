package com.lianghonglu.bridgekit.feature.demo

/** 首页与作品集页的最小导航状态，避免首页嵌套 WebView 后发生滚动手势竞争。 */
enum class DemoDestination {
    Home,
    About;

    fun openAbout(): DemoDestination = About

    fun backToHome(): DemoDestination = Home
}
