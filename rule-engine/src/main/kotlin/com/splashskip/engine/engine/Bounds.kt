package com.splashskip.engine.engine

import com.splashskip.engine.model.RectI
import kotlin.math.abs
import kotlin.math.max

object Bounds {
    /**
     * allowed 为归一化矩形 [left, top, right, bottom]；x/y 为屏幕像素坐标。
     */
    fun contains(
        allowed: List<Float>,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Boolean {
        if (width <= 0 || height <= 0 || allowed.size != 4) return false
        val nx = x.toFloat() / width
        val ny = y.toFloat() / height
        return nx >= allowed[0] && nx <= allowed[2] &&
            ny >= allowed[1] && ny <= allowed[3]
    }

    fun normalizedCenter(bounds: RectI, width: Int, height: Int): Pair<Float, Float> {
        require(width > 0 && height > 0) { "屏幕尺寸必须大于 0" }
        return bounds.centerX.toFloat() / width to bounds.centerY.toFloat() / height
    }

    /**
     * 判断两次快照的 bounds 是否只发生了小幅漂移。
     * 倒计时文字宽度变化、视图重建都可能让控件移动几像素到几十像素；
     * 位置大幅变化则视为界面已切换。容差取“固定像素”和“快照尺寸比例”的较大值。
     */
    fun near(
        current: RectI,
        snapshot: RectI,
        minSlackPx: Int = 24,
        ratio: Float = 0.25f,
    ): Boolean {
        if (snapshot.width <= 0 || snapshot.height <= 0) return false
        val slackX = max(minSlackPx, (snapshot.width * ratio).toInt())
        val slackY = max(minSlackPx, (snapshot.height * ratio).toInt())
        return abs(current.left - snapshot.left) <= slackX &&
            abs(current.top - snapshot.top) <= slackY &&
            abs(current.right - snapshot.right) <= slackX &&
            abs(current.bottom - snapshot.bottom) <= slackY
    }
}
