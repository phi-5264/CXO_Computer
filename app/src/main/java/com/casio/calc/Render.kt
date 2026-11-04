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
        // 子节点位置已由 layoutInner 设置好，直接画
        num.draw(canvas, inner)
        // 横线
        val strokePaint = Paint(paint)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = paint.textSize * 0.07f
        val lineY = num.y + num.height / 2f + linePad / 2f
        canvas.drawLine(x + 3f, lineY, x + width - 3f, lineY, strokePaint)
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
        // base 和 sup 的位置已由 layoutInner 设置好
        base.draw(canvas, paint)
        val small = Paint(paint)
        small.textSize = paint.textSize * 0.6f
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
        body.draw(canvas, paint)  // 位置已由 layoutInner 设置好
        canvas.drawText(sym, x, y, symPaint)
        // 上方横线（括在 body 上方）
        val strokePaint = Paint(paint)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = paint.textSize * 0.06f
        canvas.drawLine(body.x - 1f, y - ascent + 1f, x + width - 1f, y - ascent + 1f, strokePaint)
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
        inner.draw(canvas, paint)  // 位置已由 layoutInner 设置好
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
 * 完整流程：build → measure → layout（递归设置所有内部节点坐标） → collectBounds → draw
 *
 * 关键：layoutInner 在 draw 之前递归设置所有嵌套节点的 x/y，
 * 这样 collectBounds 才能拿到正确的位置信息给光标使用。
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
    layoutInnerRecursive(tree, paint)
    val bounds = HashMap<Int, Triple<Float, Float, Float>>()
    tree.collectBounds(bounds)
    canvas?.let { tree.draw(it, paint) }
    return tree to bounds
}

/** 递归设置所有节点的 x/y（不画），确保 collectBounds 时位置已就绪 */
private fun layoutInnerRecursive(node: RNode, paint: Paint) {
    when (node) {
        is RRow -> {
            node.layoutChildren(node.x)
            for (c in node.children) layoutInnerRecursive(c, paint)
        }
        is RFrac -> {
            val cx = node.x + node.width / 2f
            val innerPaint = Paint(paint).also { it.textSize = paint.textSize * 0.9f }
            layoutInnerRecursive(node.num, innerPaint)
            layoutInnerRecursive(node.den, innerPaint)
            node.num.x = cx - node.num.width / 2f
            node.num.y = node.y - node.ascent + node.num.height
            node.den.x = cx - node.den.width / 2f
            node.den.y = node.y + node.descent - 3f
        }
        is RSup -> {
            layoutInnerRecursive(node.base, paint)
            val small = Paint(paint).also { it.textSize = paint.textSize * 0.6f }
            layoutInnerRecursive(node.sup, small)
            node.base.x = node.x
            node.base.y = node.y
            node.sup.x = node.x + node.base.width * 0.9f
            node.sup.y = node.y - node.base.ascent * 0.7f
        }
        is RSqrt -> {
            layoutInnerRecursive(node.body, paint)
            val symW = paint.measureText("√")
            node.body.x = node.x + symW
            node.body.y = node.y
        }
        is RParen -> {
            val pad = 2f
            val lpW = paint.measureText("(")
            layoutInnerRecursive(node.inner, paint)
            node.inner.x = node.x + lpW + pad
            node.inner.y = node.y
        }
        is RAtom -> { /* 叶子，坐标已由父节点 layout */ }
    }
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
