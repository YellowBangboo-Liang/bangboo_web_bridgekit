package com.lianghonglu.bridgekit.feature.demo

/** 首页与作品集页的最小导航状态，避免首页嵌套 WebView 后发生滚动手势竞争。 */
enum class DemoDestination {
    Home,
    About;

    fun openAbout(): DemoDestination = About

    fun backToHome(): DemoDestination = Home

    /** 每个页面独立加载自己的本地 H5，首页 Demo 不与“关于我”作品集共用文档。 */
    fun assetPath(): String = when (this) {
        Home -> "demo.html"
        About -> "index.html"
    }
}
