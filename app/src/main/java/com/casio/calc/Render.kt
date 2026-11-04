package com.casio.calc

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max

/**
 * 公式渲染树。从 Tok 列表构建，支持：
 *   - 分式  a ÷ b  → RFrac(分子, 分母)
 *   - 上标  a ^ b   → RSup(底, 上标)
 *   - 下标  a _ b   → RSub(底, 下标)
 *   - 根号  √(a)    → RSqrt(body)
 * 光标位置通过 cursorIndex 表示，渲染树提供 nodeBounds 映射。
 */
sealed class RNode {
    open var x = 0f
    open var y = 0f
    open var width = 0f
    open var height = 0f      // 节点占据的高度
    open var ascent = 0f      // 基线上方
    open var descent = 0f     // 基线下方

    /** 绘制，基线在 canvasY */
    abstract fun draw(canvas: Canvas, paint: Paint)

    /**
     * 把每个原始 Tok 索引映射到它的 (x, y_top, x_right)，
     * 光标需要用来判断用户手指点击对应哪个 token。
     */
    open fun collectTokenBounds(map: HashMap<Int, Triple<Float, Float, Float>>) {}
}

class RAtom(val tok: Tok, val tokIndex: Int) : RNode() {
    override fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawText(tok.d, x, y, paint)
        // 原子高度 = paint 的文字高度
        width = paint.measureText(tok.d)
        ascent = -paint.ascent()
        descent = paint.descent()
        height = ascent + descent
    }
    override fun collectTokenBounds(map: HashMap<Int, Triple<Float, Float, Float>>) {
        map[tokIndex] = Triple(x, y - ascent, x + width)
    }
}

class RRow(val children: List<RNode>, val startIndex: Int) : RNode() {
    override fun draw(canvas: Canvas, paint: Paint) {
        width = 0f
        var maxAsc = 0f
        var maxDesc = 0f
        // 第一遍：画并计算每个子节点宽度
        for (c in children) {
            c.x = x + width
            c.y = y
            c.draw(canvas, paint)
            width += c.width
            maxAsc = max(maxAsc, c.ascent)
            maxDesc = max(maxDesc, c.descent)
        }
        ascent = maxAsc
        descent = maxDesc
        height = ascent + descent
    }
}

class RFrac(val num: RNode, val den: RNode, val opIndex: Int) : RNode() {
    private var lineY = 0f
    override fun draw(canvas: Canvas, paint: Paint) {
        // 分子在基线上方，分母在基线下方
        // 总高度 = num.height + 横线 + den.height
        val lineThick = dp(2f)
        num.x = x
        den.x = x
        // 横线在基线上
        val numCenterY = y - dp(4f) - num.descent
        num.y = numCenterY + num.descent
        val denCenterY = y + dp(4f) + den.ascent
        den.y = denCenterY + den.descent
        num.draw(canvas, paint)
        den.draw(canvas, paint)
        width = max(num.width, den.width)
        num.x = x + (width - num.width) / 2f
        den.x = x + (width - den.width) / 2f
        num.draw(canvas, paint)
        den.draw(canvas, paint)

        lineY = y + lineThick / 2f
        val stroke = paint.textSize * 0.08f
        paint.strokeWidth = stroke
        val originStroke = paint.strokeWidth
        paint.style = Paint.Style.STROKE
        canvas.drawLine(x, lineY, x + width, lineY, paint)
        paint.strokeWidth = originStroke
        paint.style = Paint.Style.FILL

        ascent = num.height + dp(2f)
        descent = den.height + dp(2f)
        height = ascent + descent
    }
}

class RSup(val base: RNode, val sup: RNode, val opIndex: Int) : RNode() {
    override fun draw(canvas: Canvas, paint: Paint) {
        val supPaint = Paint(paint)
        supPaint.textSize = paint.textSize * 0.65f
        sup.x = x
        base.x = x
        base.y = y
        base.draw(canvas, paint)
        sup.y = base.y - base.ascent * 0.8f
        sup.draw(canvas, supPaint)
        width = base.width + sup.width * 0.8f
        ascent = base.ascent + sup.height * 0.7f
        descent = base.descent
        height = ascent + descent
    }
}

class RSub(val base: RNode, val sub: RNode, val opIndex: Int) : RNode() {
    override fun draw(canvas: Canvas, paint: Paint) {
        val subPaint = Paint(paint)
        subPaint.textSize = paint.textSize * 0.65f
        sub.x = x
        base.x = x
        base.y = y
        base.draw(canvas, paint)
        sub.y = base.y + base.descent * 0.3f + sub.ascent * 0.2f
        sub.draw(canvas, subPaint)
        width = base.width + sub.width * 0.8f
        ascent = base.ascent
        descent = base.descent + sub.height * 0.7f
        height = ascent + descent
    }
}

class RSqrt(val body: RNode, val parenOpenIndex: Int) : RNode() {
    override fun draw(canvas: Canvas, paint: Paint) {
        val sym = "√"
        val symPaint = Paint(paint)
        symPaint.textSize = paint.textSize * 1.2f
        val symW = symPaint.measureText(sym)
        body.x = x + symW
        body.y = y
        body.draw(canvas, paint)
        // 先画 body 来拿到 height
        ascent = body.height + dp(2f)
        descent = dp(2f)
        height = ascent + descent
        width = symW + body.width

        // 重画 body，确保位置正确
        body.draw(canvas, paint)
        // 画 √ 符号
        canvas.drawText(sym, x, y, symPaint)
        // 画上方横线
        paint.strokeWidth = paint.textSize * 0.06f
        val oldStyle = paint.style
        paint.style = Paint.Style.STROKE
        canvas.drawLine(x + symW, y - ascent + dp(1f), x + width, y - ascent + dp(1f), paint)
        paint.style = oldStyle
    }
}

class RParen(val left: RNode, val right: RNode, val lpTok: Int, val rpTok: Int) : RNode() {
    val lpSym = "("
    val rpSym = ")"
    override fun draw(canvas: Canvas, paint: Paint) {
        val symPaint = Paint(paint)
        symPaint.textSize = paint.textSize
        val lpW = symPaint.measureText(lpSym)
        val rpW = symPaint.measureText(rpSym)
        left.x = x + lpW
        left.y = y
        left.draw(canvas, paint)
        width = lpW + left.width + rpW
        ascent = left.ascent
        descent = left.descent
        height = ascent + descent
        // 重画以修正 height
        left.draw(canvas, paint)
        canvas.drawText(lpSym, x, y, symPaint)
        canvas.drawText(rpSym, x + width - rpW, y, symPaint)
    }
}

private fun dp(v: Float): Float = v  // 在 Render 里不依赖 Android density，调用方传 dp 值

// ==================== 从 Tok 列表构建渲染树 ====================

fun buildRenderTree(tokens: List<Tok>, start: Int = 0, end: Int = tokens.size): RNode {
    val children = mutableListOf<RNode>()
    var i = start
    while (i < end) {
        val t = tokens[i]

        if (t is LpTok) {
            // 找到匹配的 RpTok
            var depth = 1
            var j = i + 1
            while (j < end && depth > 0) {
                if (tokens[j] is LpTok) depth++
                if (tokens[j] is RpTok) depth--
                if (depth == 0) break
                j++
            }
            val inner = buildRenderTree(tokens, i + 1, j)
            children.add(RParen(inner, RRow(emptyList(), i + 1), i, j))
            i = j + 1
        } else if (t is OpTok && t.op == "^") {
            // 幂运算 —— 上标
            val prev = children.removeLastOrNull() ?: RAtom(RpTok(), -1)
            val expEnd = findExprEnd(tokens, i + 1, end)
            val exp = buildRenderTree(tokens, i + 1, expEnd)
            children.add(RSup(prev, exp, i))
            i = expEnd
        } else if (t is OpTok && t.op == "÷") {
            // 除法 —— 分式。但要处理优先级，确保分子是正确的范围
            // 简化做法：把当前 row 的最后一个 atom/paren 作为分子，
            // 然后继续解析分母直到遇到 同级 运算符（+/-/×/÷/^）前
            if (children.isEmpty()) {
                children.add(RAtom(t, i))
                i++
            } else {
                val num = children.removeLast()
                val denEnd = findFracDenEnd(tokens, i + 1, end)
                val den = buildRenderTree(tokens, i + 1, denEnd)
                children.add(RFrac(num, den, i))
                i = denEnd
            }
        } else if (t is FnTok && (t.fn == "sqrt" || t.fn == "cbrt")) {
            // 根号
            var depth = 0
            var j = i + 1
            if (j < end && tokens[j] is LpTok) {
                depth = 1; j++
                while (j < end && depth > 0) {
                    if (tokens[j] is LpTok) depth++
                    if (tokens[j] is RpTok) depth--
                    if (depth == 0) break
                    j++
                }
                val inner = buildRenderTree(tokens, i + 2, j)
                children.add(RSqrt(inner, i + 1))
                i = j + 1
            } else {
                children.add(RAtom(t, i))
                i++
            }
        } else {
            children.add(RAtom(t, i))
            i++
        }
    }
    return RRow(children, start)
}

/** 找到表达式结束（下一个同级运算符前） */
private fun findExprEnd(tokens: List<Tok>, from: Int, end: Int): Int {
    var depth = 0
    for (i in from until end) {
        val t = tokens[i]
        if (t is LpTok) depth++
        if (t is RpTok) { depth--; if (depth == 0) return i }
        if (depth == 0 && t is OpTok && t.op in listOf("+", "-", "×", "÷", "^")) return i
    }
    return end
}

/** 分式分母结束位置 */
private fun findFracDenEnd(tokens: List<Tok>, from: Int, end: Int): Int {
    var depth = 0
    for (i in from until end) {
        val t = tokens[i]
        if (t is LpTok) depth++
        if (t is RpTok) { depth--; if (depth == 0) return i }
        if (depth == 0 && t is OpTok && t.op in listOf("+", "-")) return i
    }
    return end
}

// ==================== 光标计算 ====================

/**
 * 把 render 树布局好（设置 x, y）后，给定 cursorIndex（在哪个 token 之前），
 * 计算光标应该画在哪个 x 位置。返回 x 坐标。
 */
fun computeCursorX(
    tokens: List<Tok>,
    cursorIndex: Int,
    paint: Paint,
    originX: Float,
    baselineY: Float,
    availableWidth: Float
): Float {
    if (cursorIndex <= 0) return originX
    if (cursorIndex >= tokens.size) {
        // 在最后一个 token 后
        val tree = buildRenderTree(tokens, 0, tokens.size)
        tree.x = originX; tree.y = baselineY
        tree.draw(android.graphics.Canvas(), paint)
        return tree.x + tree.width
    }
    // cursorIndex 位于 tokens[cursorIndex] 之前
    val tree = buildRenderTree(tokens, 0, tokens.size)
    tree.x = originX; tree.y = baselineY
    tree.draw(android.graphics.Canvas(), paint)

    // 找 bounds
    val bounds = HashMap<Int, Triple<Float, Float, Float>>()
    collectAllBounds(tree, bounds)
    val b = bounds[cursorIndex]
    return b?.first ?: (originX + tree.width)
}

private fun collectAllBounds(node: RNode, map: HashMap<Int, Triple<Float, Float, Float>>) {
    when (node) {
        is RAtom -> node.collectTokenBounds(map)
        is RRow -> node.children.forEach { collectAllBounds(it, map) }
        is RFrac -> {
            collectAllBounds(node.num, map)
            collectAllBounds(node.den, map)
        }
        is RSup -> {
            collectAllBounds(node.base, map)
            collectAllBounds(node.sup, map)
        }
        is RSub -> {
            collectAllBounds(node.base, map)
            collectAllBounds(node.sub, map)
        }
        is RSqrt -> collectAllBounds(node.body, map)
        is RParen -> collectAllBounds(node.left, map)
    }
}

/** 根据 x 坐标反推光标 index */
fun findCursorByX(
    tokens: List<Tok>,
    touchX: Float,
    paint: Paint,
    originX: Float,
    baselineY: Float
): Int {
    if (tokens.isEmpty()) return 0
    val tree = buildRenderTree(tokens, 0, tokens.size)
    tree.x = originX; tree.y = baselineY
    tree.draw(android.graphics.Canvas(), paint)

    val bounds = HashMap<Int, Triple<Float, Float, Float>>()
    collectAllBounds(tree, bounds)

    var bestIdx = 0
    var bestDist = Float.MAX_VALUE
    for ((idx, b) in bounds) {
        val mid = (b.first + b.third) / 2f
        val d = kotlin.math.abs(touchX - mid)
        if (d < bestDist) {
            bestDist = d; bestIdx = idx
        }
    }
    // 光标在该 token 之前还是之后？看 x 距离
    val b = bounds[bestIdx] ?: return 0
    return if (touchX < (b.first + b.third) / 2f) bestIdx else bestIdx + 1
}
