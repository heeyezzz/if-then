package com.ifthen.app

object Constants {
    const val HOME_FREQUENT_LIMIT = 6
    const val FREQUENT_WINDOW_DAYS = 14
    const val ZOMBIE_DAYS = 14
    const val HEATMAP_DAYS = 30
    const val RULE_STATS_WINDOW_DAYS = 30
    /** 链条长度软上限：超过只提示，不阻断 */
    const val CHAIN_MAX_STEPS = 8
    const val CHAIN_HEALTH_DAYS = 14
}
