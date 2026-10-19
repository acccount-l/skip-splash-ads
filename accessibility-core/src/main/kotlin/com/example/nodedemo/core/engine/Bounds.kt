package com.example.nodedemo.core.engine

import com.example.nodedemo.core.model.RectI
import kotlin.math.abs
import kotlin.math.max

/** 与平台无关的几何校验工具。 */
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
     * 容差取“固定像素”和“快照尺寸比例”的较大值。
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
