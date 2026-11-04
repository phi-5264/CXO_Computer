package com.casio.calc

import android.graphics.Canvas
import android.graphics.Paint

/**
 * 单行公式渲染 + 光标定位。
 *
 * 所有 token 按 display text 拼在一行里，位置由 Paint.measureText 精确计算。
 * 不做堆叠（分式上下摆、根号括体、幂上标）——显示简洁稳定，光标好算。
 */

/** 渲染单行表达式，返回每个 token 的 bounds 和总宽度 */
data class LineLayout(
    val tokenBounds: HashMap<Int, Triple<Float, Float, Float>>,  // 列表位置 index → (xLeft, yTop, xRight)
    val totalWidth: Float,
) {
    fun cursorX(cursorIndex: Int): Float {
        if (tokenBounds.isEmpty()) return 0f
        // cursorIndex 表示"在第 cursorIndex 个 token 之前插入"
        return if (cursorIndex <= 0) {
            tokenBounds[0]?.first ?: 0f
        } else if (cursorIndex >= tokenBounds.size) {
            tokenBounds[tokenBounds.size - 1]?.third ?: totalWidth
        } else {
            tokenBounds[cursorIndex]?.first ?: totalWidth
        }
    }
}

/**
 * 精确测量并摆放 token 到单行里。
 *
 * @param tokens    token 列表（Tok 接口，每个有 d: String）
 * @param paint     用于测量和绘制的 Paint（textSize/typeface 决定宽度）
 * @param originX   表达式起点 x
 * @param baselineY 表达式基线 y（仅用于 bounds 的 yTop，不影响宽度计算）
 */
fun layoutLine(
    tokens: List<Tok>,
    paint: Paint,
    originX: Float,
    baselineY: Float,
): LineLayout {
    val bounds = HashMap<Int, Triple<Float, Float, Float>>()
    var curX = originX
    val ascent = -paint.ascent()

    for (i in tokens.indices) {
        val tok = tokens[i]
        val w = paint.measureText(tok.d)
        bounds[i] = Triple(curX, baselineY - ascent, curX + w)
        curX += w
    }

    return LineLayout(bounds, curX - originX)
}

/** 在 canvas 上绘制单行表达式，返回 layout 用于光标定位 */
fun drawLine(
    canvas: Canvas,
    tokens: List<Tok>,
    paint: Paint,
    originX: Float,
    baselineY: Float,
): LineLayout {
    val layout = layoutLine(tokens, paint, originX, baselineY)
    for (i in tokens.indices) {
        canvas.drawText(tokens[i].d, layout.tokenBounds[i]!!.first, baselineY, paint)
    }
    return layout
}

/**
 * 根据触摸点 x 反推光标 index（"在哪个 token 之前插入"）。
 * 点左半段 → 光标在它前面；点右半段 → 光标在它后面（即下一个前面）。
 */
fun findCursorByX(
    tokens: List<Tok>,
    touchX: Float,
    paint: Paint,
    originX: Float,
): Int {
    if (tokens.isEmpty()) return 0
    val layout = layoutLine(tokens, paint, originX, 0f)
    val bounds = layout.tokenBounds

    var bestIdx = 0
    var bestDist = Float.MAX_VALUE
    for (i in tokens.indices) {
        val b = bounds[i] ?: continue
        val mid = (b.first + b.third) / 2f
        val d = kotlin.math.abs(touchX - mid)
        if (d < bestDist) {
            bestDist = d
            bestIdx = i
        }
    }

    val b = bounds[bestIdx]
    return if (b != null && touchX < (b.first + b.third) / 2f) bestIdx else bestIdx + 1
}

/** 根据 cursorIndex 计算光标在屏幕上的 x 坐标 */
fun computeCursorX(
    tokens: List<Tok>,
    cursorIndex: Int,
    paint: Paint,
    originX: Float,
): Float {
    if (tokens.isEmpty() || cursorIndex <= 0) return originX
    val layout = layoutLine(tokens, paint, originX, 0f)
    if (cursorIndex >= tokens.size) return originX + layout.totalWidth
    return layout.tokenBounds[cursorIndex]?.first ?: (originX + layout.totalWidth)
}
