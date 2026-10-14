package com.ifthen.app

object Constants {
    const val HOME_FREQUENT_LIMIT = 6
    const val FREQUENT_WINDOW_DAYS = 14
    const val ZOMBIE_DAYS = 14
    /** 链条长度软上限：超过只提示，不阻断 */
    const val CHAIN_MAX_STEPS = 8
    const val CHAIN_HEALTH_DAYS = 14

    /** 链条健康在「全部」档下的回溯上限 */
    const val CHAIN_HEALTH_ALL_MAX_DAYS = 90

    /** 窗口内活跃天数不足这个数就不下「断在第几步」的结论 */
    const val CHAIN_HEALTH_MIN_ACTIVE_DAYS = 3
    /** 链条步数超过这个数就不做归组，退化成单条流水 */
    const val LOG_CHAIN_MIN_SIZE = 2
    const val HEATMAP_CELLS_PER_ROW = 10
    const val HEATMAP_MAX_WEEKS = 52
    const val AI_REVIEW_MAX_LOGS = 200
}

/**
 * 复盘页时间范围。整页统计口径都跟着它走，[minSample] 之下不显示执行率
 * ——触发 5 次算出的 100% 和 27 次算出的 96% 不该长一个样。
 */
enum class ReviewWindow(val label: String, val days: Int, val minSample: Int) {
    WEEK("7天", 7, 2),
    MONTH("30天", 30, 5),
    ALL("全部", 0, 10);

    /** 数字前面的口径词 */
    val noun: String
        get() = when (this) {
            WEEK -> "近 7 天"
            MONTH -> "30 天"
            ALL -> "全部记录"
        }
}
