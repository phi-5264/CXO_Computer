package com.casio.calc

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max

/**
 * 公式渲染树。从 Tok 列表构建，支持堆叠显示。
 *
 * 关键设计：每个节点的 x/y 是最终布局位置，必须先 measure 再 draw。
 *   measure(canvas, paint) 会计算所有子节点的尺寸和本节点尺寸，设置 width/height/ascent/descent
 *   draw(canvas, paint)     在已经 measure 好并设置好 x/y 的前提下渲染
 *
 * 这样 measure 一次，draw 一次，位置完全稳定。
 */
sealed class RNode {
    var x = 0f
    var y = 0f               // 基线 y

    open var width = 0f
    open var height = 0f      // 总高度
    open var ascent = 0f      // 基线上方
    open var descent = 0f     // 基线下方

    abstract fun measure(paint: Paint)
    abstract fun draw(canvas: Canvas, paint: Paint)

    /** 收集 bounds 映射，key = 原始 Tok 索引，value = (x_left, y_top, x_right) */
    open fun collectBounds(map: HashMap<Int, Triple<Float, Float, Float>>) {}
}

/** 原子 token（数字、运算符、函数、括号等） */
class RAtom(val tok: Tok, val tokIndex: Int) : RNode() {
    override fun measure(paint: Paint) {
        width = paint.measureText(tok.d)
        ascent = -paint.ascent()
        descent = paint.descent()
        height = ascent + descent
    }
    override fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawText(tok.d, x, y, paint)
    }
    override fun collectBounds(map: HashMap<Int, Triple<Float, Float, Float>>) {
        map[tokIndex] = Triple(x, y - ascent, x + width)
    }
}

/** 一行子节点，水平排列 */
class RRow(val children: List<RNode>) : RNode() {
    override fun measure(paint: Paint) {
        var w = 0f; var a = 0f; var d = 0f
        for (c in children) {
            c.measure(paint)
            w += c.width
            a = max(a, c.ascent)
            d = max(d, c.descent)
        }
        width = w; ascent = a; descent = d; height = a + d
    }
    /** 按给定 x 起点流式摆放子节点 */
    fun layoutChildren(xStart: Float) {
        var cur = xStart
        for (c in children) {
            c.x = cur
            c.y = y
            cur += c.width
        }
        x = xStart
    }
    override fun draw(canvas: Canvas, paint: Paint) {
        for (c in children) c.draw(canvas, paint)
    }
    override fun collectBounds(map: HashMap<Int, Triple<Float, Float, Float>>) {
        for (c in children) c.collectBounds(map)
    }
}

/** 分式：num/den，中间有横线 */
class RFrac(val num: RNode, val numIndex: Int?, val den: RNode, val denIndex: Int?) : RNode() {
    private val linePad = 4f

    override fun measure(paint: Paint) {
        // 需要更小的 paint 给分子分母
        val inner = Paint(paint)
        inner.textSize = paint.textSize * 0.9f
        num.measure(inner)
        den.measure(inner)
        width = max(num.width, den.width) + 8f  // 左右留白
        ascent = num.height + linePad + 3f
        descent = den.height + linePad + 3f
        height = ascent + descent
    }
    override fun draw(canvas: Canvas, paint: Paint) {
        val inner = Paint(paint)
        inner.textSize = paint.textSize * 0.9f
        // 居中
        val cx = x + width / 2f
        num.x = cx - num.width / 2f
        num.y = y - ascent + num.height
        num.draw(canvas, inner)
        // 横线
        val oldStroke = inner.strokeWidth
        inner.strokeWidth = paint.textSize * 0.07f
        val oldStyle = inner.style
        inner.style = Paint.Style.STROKE
        val lineY = y - ascent + num.height + linePad / 2f
        canvas.drawLine(x + 3f, lineY, x + width - 3f, lineY, inner)
        inner.style = oldStyle
        inner.strokeWidth = oldStroke
        den.x = cx - den.width / 2f
        den.y = y + descent - 3f
        den.draw(canvas, inner)
    }
    override fun collectBounds(map: HashMap<Int, Triple<Float, Float, Float>>) {
        num.collectBounds(map)
        den.collectBounds(map)
    }
}

/** 幂（上标） */
class RSup(val base: RNode, val baseIndex: Int?, val sup: RNode, val supIndex: Int?) : RNode() {
    override fun measure(paint: Paint) {
        base.measure(paint)
        val small = Paint(paint)
        small.textSize = paint.textSize * 0.6f
        sup.measure(small)
        width = base.width + sup.width * 0.7f
        ascent = base.ascent + sup.height * 0.8f
        descent = base.descent
        height = ascent + descent
    }
    override fun draw(canvas: Canvas, paint: Paint) {
        base.x = x; base.y = y
        base.draw(canvas, paint)
        val small = Paint(paint)
        small.textSize = paint.textSize * 0.6f
        sup.x = x + base.width * 0.9f
        sup.y = y - base.ascent * 0.7f
        sup.draw(canvas, small)
    }
    override fun collectBounds(map: HashMap<Int, Triple<Float, Float, Float>>) {
        base.collectBounds(map); sup.collectBounds(map)
    }
}

/** 根号 */
class RSqrt(val body: RNode, val parenIndex: Int?) : RNode() {
    override fun measure(paint: Paint) {
        val symW = paint.measureText("√")
        body.measure(paint)
        width = symW + 4f + body.width
        ascent = body.ascent + 2f
        descent = body.descent + 2f
        height = ascent + descent
    }
    override fun draw(canvas: Canvas, paint: Paint) {
        val sym = "√"
        val symPaint = Paint(paint)
        symPaint.textSize = paint.textSize * 1.15f
        symPaint.typeface = paint.typeface
        body.x = x + paint.measureText(sym)
        body.y = y
        body.draw(canvas, paint)
        canvas.drawText(sym, x, y, symPaint)
        // 上方横线（括在 body 上方）
        val oldStroke = paint.strokeWidth
        paint.strokeWidth = paint.textSize * 0.06f
        val oldStyle = paint.style
        paint.style = Paint.Style.STROKE
        canvas.drawLine(body.x - 1f, y - ascent + 1f, x + width - 1f, y - ascent + 1f, paint)
        paint.style = oldStyle; paint.strokeWidth = oldStroke
    }
    override fun collectBounds(map: HashMap<Int, Triple<Float, Float, Float>>) {
        body.collectBounds(map)
    }
}

/** 括号（视觉包含子表达式） */
class RParen(val inner: RNode, val lpIndex: Int, val rpIndex: Int) : RNode() {
    private val pad = 2f
    override fun measure(paint: Paint) {
        val symPaint = Paint(paint); symPaint.textSize = paint.textSize
        val lpW = symPaint.measureText("(")
        val rpW = symPaint.measureText(")")
        inner.measure(paint)
        width = lpW + pad + inner.width + pad + rpW
        ascent = max(inner.ascent, paint.measureText("(") * 0.6f)
        descent = max(inner.descent, paint.measureText("(") * 0.3f)
        height = ascent + descent
    }
    override fun draw(canvas: Canvas, paint: Paint) {
        val symPaint = Paint(paint); symPaint.textSize = paint.textSize
        val lpW = symPaint.measureText("(")
        val rpW = symPaint.measureText(")")
        inner.x = x + lpW + pad
        inner.y = y
        inner.draw(canvas, paint)
        canvas.drawText("(", x + 1f, y, symPaint)
        canvas.drawText(")", x + width - rpW - 1f, y, symPaint)
    }
    override fun collectBounds(map: HashMap<Int, Triple<Float, Float, Float>>) {
        // 括号 token 的 bounds 也映射出来
        val lpW = width - 4f - inner.width
        val rpW = width - lpW - 4f - inner.width
        map[lpIndex] = Triple(x, y - ascent, x + lpW + 2f)
        map[rpIndex] = Triple(x + width - rpW - 2f, y - ascent, x + width)
        inner.collectBounds(map)
    }
}

// ====================================================
// 构建渲染树
// ====================================================

fun buildRenderTree(tokens: List<Tok>, start: Int = 0, end: Int = tokens.size): RNode {
    val children = mutableListOf<RNode>()
    var i = start
    while (i < end) {
        val t = tokens[i]

        when {
            t is LpTok -> {
                val rp = findMatchingRp(tokens, i + 1, end)
                val inner = buildRenderTree(tokens, i + 1, rp)
                children.add(RParen(inner, i, rp))
                i = rp + 1
            }
            t is FnTok && t.fn in listOf("sqrt", "cbrt") && i + 1 < end && tokens[i + 1] is LpTok -> {
                val rp = findMatchingRp(tokens, i + 2, end)
                val inner = buildRenderTree(tokens, i + 2, rp)
                children.add(RSqrt(inner, i + 1))
                i = rp + 1
            }
            t is OpTok && t.op == "^" -> {
                val base = children.removeLastOrNull() ?: RAtom(t, i)
                val expEnd = findPowExpEnd(tokens, i + 1, end)
                val exp = buildRenderTree(tokens, i + 1, expEnd)
                children.add(RSup(base, -1, exp, -1))
                i = expEnd
            }
            t is OpTok && t.op == "÷" && children.isNotEmpty() -> {
                // 除法左结合：分子 = 最后一个子树；分母到下一个同级运算符前停止
                val num = children.removeLast()
                val denEnd = findDivDenEnd(tokens, i + 1, end)
                val den = buildRenderTree(tokens, i + 1, denEnd)
                children.add(RFrac(num, null, den, null))
                i = denEnd
            }
            else -> {
                children.add(RAtom(t, i))
                i++
            }
        }
    }
    return RRow(children)
}

private fun findMatchingRp(tokens: List<Tok>, from: Int, end: Int): Int {
    var depth = 1
    for (j in from until end) {
        if (tokens[j] is LpTok) depth++
        if (tokens[j] is RpTok) {
            depth--
            if (depth == 0) return j
        }
    }
    return end - 1
}

private fun findPowExpEnd(tokens: List<Tok>, from: Int, end: Int): Int {
    // 幂的指数范围 = 直到下一个 +/- (depth==0)
    var depth = 0
    for (i in from until end) {
        val t = tokens[i]
        if (t is LpTok) depth++
        if (t is RpTok) { depth--; if (depth == 0) return i }
        if (depth == 0 && t is OpTok && t.op in listOf("+", "-")) return i
    }
    return end
}

/**
 * 分式分母结束位置 —— 左结合关键：
 *   只在括号内完整内容；遇到 depth==0 时的 + - × ÷ ^ 都停止。
 *   这样 `1÷2÷3` → (1/2)/3，分子是 1，分母是 2，后面的 ÷3 继续下一轮。
 *   `1÷2+3`   → 1/2+3，分母是 2。
 *   `1÷(2+3)÷4` → (1/(2+3))/4，正确。
 */
private fun findDivDenEnd(tokens: List<Tok>, from: Int, end: Int): Int {
    var depth = 0
    for (i in from until end) {
        val t = tokens[i]
        if (t is LpTok) depth++
        if (t is RpTok) {
            depth--
            if (depth == 0) return i + 1  // 让右括号也包含在分母里
        }
        if (depth == 0 && t is OpTok && t.op in listOf("+", "-", "×", "÷", "^", "C", "P")) {
            return i
        }
    }
    return end
}

// ====================================================
// 光标位置计算
// ====================================================

/**
 * 完整流程：build → measure → layout → draw → collectBounds
 * 返回的 tree 上每个 x/y/width 都设置好。
 */
fun layoutAndRender(
    tokens: List<Tok>,
    paint: Paint,
    originX: Float,
    baselineY: Float,
    canvas: Canvas? = null,
): Pair<RNode, HashMap<Int, Triple<Float, Float, Float>>> {
    val tree = buildRenderTree(tokens)
    tree.measure(paint)
    tree.x = originX; tree.y = baselineY
    if (tree is RRow) tree.layoutChildren(originX)
    val bounds = HashMap<Int, Triple<Float, Float, Float>>()
    tree.collectBounds(bounds)
    canvas?.let { tree.draw(it, paint) }
    return tree to bounds
}

/** 根据 touchX 反推 cursor index */
fun findCursorByX(
    tokens: List<Tok>,
    touchX: Float,
    paint: Paint,
    originX: Float,
    baselineY: Float,
): Int {
    if (tokens.isEmpty()) return 0
    val (_, bounds) = layoutAndRender(tokens, paint, originX, baselineY)
    if (bounds.isEmpty()) return 0

    var bestIdx = 0
    var bestDist = Float.MAX_VALUE
    for ((idx, b) in bounds) {
        val mid = (b.first + b.third) / 2f
        val d = kotlin.math.abs(touchX - mid)
        if (d < bestDist) {
            bestDist = d; bestIdx = idx
        }
    }
    val b = bounds[bestIdx] ?: return 0
    return if (touchX < (b.first + b.third) / 2f) bestIdx else bestIdx + 1
}

/** 根据 cursorIndex 返回光标 x 坐标 */
fun computeCursorX(
    tokens: List<Tok>,
    cursorIndex: Int,
    paint: Paint,
    originX: Float,
    baselineY: Float,
): Float {
    if (tokens.isEmpty() || cursorIndex <= 0) return originX
    val (tree, bounds) = layoutAndRender(tokens, paint, originX, baselineY)
    if (cursorIndex >= tokens.size) return originX + tree.width
    val b = bounds[cursorIndex]
    return b?.first ?: (originX + tree.width)
}
