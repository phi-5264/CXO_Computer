package com.casio.calc

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.text.InputType
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max

class CalculatorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    val state = CalcState()
    private var resultText: String? = null
    private var shiftArmed = false
    private var alphaArmed = false
    private var justEvaluated = false

    private val matrices = mutableMapOf<String, Matrix>()
    private val vectors = mutableMapOf<String, Vector>()

    // GRAPH 模式
    private var graphView: GraphView? = null
    private var graphFuncs = mutableListOf(
        FuncItem("sin(X)", true, GraphView.PALETTE[0]),
    )
    private var graph3D = false
    /** 3D 模式表达式（同步到 graphFuncs[0].expr） */
    private var graph3DExpr = "sin(sqrt(X*X+Y*Y))/(sqrt(X*X+Y*Y)+0.001)"
    /** GRAPH 子状态：true=编辑表达式（用本机数学键盘），false=绘图 */
    private var graphEditing = true
    /** 当前正在编辑的函数索引（2D 模式） */
    private var graphEditingIdx = 0

    /** GRAPH 显示区短按追踪 */
    private var graphDownX = 0f; private var graphDownY = 0f; private var graphDownTime = 0L

    // 布局几何
    private lateinit var displayRect: RectF
    private val keyRects = mutableListOf<List<RectF>>()

    // 画笔
    private val brushBg = Paint()
    private val brushLcd = Paint()
    private val brushLcdBorder = Paint()
    private val brushInd = Paint()
    private val brushExpr = Paint()
    private val brushExprBold = Paint()
    private val brushResult = Paint()
    private val brushCursor = Paint()
    private val brushKey = Paint()
    private val brushKeyAccent = Paint()
    private val brushKeyFunc = Paint()
    private val brushKeyNum = Paint()
    private val brushKeyOp = Paint()
    private val brushKeyShift = Paint()
    private val brushKeyDel = Paint()
    private val brushPrimary = Paint()
    private val brushPrimarySmall = Paint()
    private val brushShift = Paint()
    private val brushAlpha = Paint()
    private val brushCursorBlink = Paint()

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    // ===== 配色 =====
    private val COLOR_BG = Color.rgb(0x1e, 0x1e, 0x26)
    private val COLOR_LCD = Color.rgb(0xc9, 0xd6, 0xc0)
    private val COLOR_LCD_BORDER = Color.rgb(0x3a, 0x3a, 0x42)
    private val COLOR_LCD_DARK = Color.rgb(0x1a, 0x1a, 0x1a)
    private val COLOR_KEY_NUM = Color.rgb(0x44, 0x44, 0x4e)
    private val COLOR_KEY_OP = Color.rgb(0x5a, 0x5a, 0x66)
    private val COLOR_KEY_FUNC = Color.rgb(0x33, 0x33, 0x3c)
    private val COLOR_KEY_SHIFT = Color.rgb(0x2a, 0x3d, 0x5e)
    private val COLOR_KEY_ACCENT = Color.rgb(0xd8, 0x7b, 0x2d)
    private val COLOR_KEY_DEL = Color.rgb(0x9e, 0x40, 0x40)
    private val COLOR_WHITE = Color.WHITE
    private val COLOR_SHIFT = Color.rgb(0xff, 0xcf, 0x45)
    private val COLOR_ALPHA = Color.rgb(0xff, 0x6b, 0x7a)

    private val funcs = mapOf(
        "sin" to "sin", "cos" to "cos", "tan" to "tan",
        "asin" to "sin⁻¹", "acos" to "cos⁻¹", "atan" to "tan⁻¹",
        "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh",
        "log" to "log", "ln" to "ln", "tenx" to "10^", "ex" to "e^",
        "sqrt" to "√", "cbrt" to "³√", "abs" to "Abs", "arg" to "Arg", "conjg" to "Conjg"
    )
    private val posts = mapOf(
        "sq" to "²", "cube" to "³", "inv" to "⁻¹", "percent" to "%", "fact" to "!"
    )
    private val varLetters = setOf("A", "B", "C", "D", "E", "F", "X", "Y", "M")

    // 按键类别 → 颜色
    private fun keyColor(action: String, primary: String): Int {
        if (action == "eq") return COLOR_KEY_ACCENT
        if (action == "del" || action == "ac") return COLOR_KEY_DEL
        if (action == "shift") return COLOR_KEY_SHIFT
        if (action.length == 1 && action[0].isDigit()) return COLOR_KEY_NUM
        if (action == "dot") return COLOR_KEY_NUM
        if (primary in listOf("+", "−", "×", "÷", "^")) return COLOR_KEY_OP
        return COLOR_KEY_FUNC
    }

    init {
        isClickable = true
        brushLcd.style = Paint.Style.FILL
        brushLcdBorder.style = Paint.Style.STROKE
        brushLcdBorder.strokeWidth = dp(2f)

        brushInd.textSize = dp(13f); brushInd.typeface = Typeface.MONOSPACE
        brushInd.color = COLOR_LCD_DARK

        brushExpr.textSize = dp(26f); brushExpr.typeface = Typeface.MONOSPACE
        brushExpr.color = COLOR_LCD_DARK

        brushExprBold.textSize = dp(26f)
        brushExprBold.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        brushExprBold.color = COLOR_LCD_DARK

        brushResult.textSize = dp(34f)
        brushResult.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        brushResult.color = COLOR_LCD_DARK

        brushCursor.color = COLOR_LCD_DARK
        brushCursor.style = Paint.Style.STROKE
        brushCursor.strokeWidth = dp(2f)

        brushCursorBlink.color = COLOR_LCD_DARK

        brushPrimary.color = COLOR_WHITE; brushPrimary.typeface = Typeface.DEFAULT_BOLD
        brushPrimarySmall.color = COLOR_WHITE; brushPrimarySmall.typeface = Typeface.DEFAULT_BOLD

        brushShift.color = COLOR_SHIFT; brushShift.typeface = Typeface.DEFAULT_BOLD
        brushAlpha.color = COLOR_ALPHA; brushAlpha.typeface = Typeface.DEFAULT_BOLD

        brushKeyNum.style = Paint.Style.FILL; brushKeyOp.style = Paint.Style.FILL
        brushKeyFunc.style = Paint.Style.FILL; brushKeyAccent.style = Paint.Style.FILL
        brushKeyShift.style = Paint.Style.FILL; brushKeyDel.style = Paint.Style.FILL
        brushKey.style = Paint.Style.FILL
    }

    private var cursorBlink = true
    private val cursorBlinkRunnable = object : Runnable {
        override fun run() {
            cursorBlink = !cursorBlink
            invalidate()
            postDelayed(this, 500)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(cursorBlinkRunnable, 500)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(cursorBlinkRunnable)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layout(w.toFloat(), h.toFloat())
    }

    private fun layout(w: Float, h: Float) {
        val margin = dp(10f)
        // 显示区增加 50%：72dp → 108dp
        val dispH = dp(108f)
        displayRect = RectF(margin, margin, w - margin, margin + dispH)

        val keypadTop = displayRect.bottom + dp(14f)
        val bottom = h - dp(8f)
        val keypadH = bottom - keypadTop
        val rows = Keypad.rows.size
        val rowH = keypadH / rows
        val gap = dp(7f)

        keyRects.clear()
        for (i in 0 until rows) {
            val keys = Keypad.rows[i]
            val n = keys.size
            val keyW = (w - 2 * margin - (n - 1) * gap) / n
            val rects = mutableListOf<RectF>()
            for (j in 0 until n) {
                val x = margin + j * (keyW + gap)
                val y = keypadTop + i * rowH
                rects.add(RectF(x, y, x + keyW, y + rowH - gap))
            }
            keyRects.add(rects)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::displayRect.isInitialized) layout(width.toFloat(), height.toFloat())

        canvas.drawColor(COLOR_BG)

        // LCD 显示屏 —— GRAPH 绘图状态下显示区改为函数图像；编辑状态仍为表达式
        if (state.mode == Mode.GRAPH && !graphEditing) {
            drawGraphInDisplay(canvas)
        } else {
            brushLcd.color = COLOR_LCD
            canvas.drawRoundRect(displayRect, dp(10f), dp(10f), brushLcd)
            brushLcdBorder.color = COLOR_LCD_BORDER
            canvas.drawRoundRect(displayRect, dp(10f), dp(10f), brushLcdBorder)

            val padL = displayRect.left + dp(14f)
            val padR = displayRect.right - dp(14f)
            // 指示行
            val ind = indicatorLine()
            brushInd.textAlign = Paint.Align.LEFT
            canvas.drawText(ind, padL, displayRect.top + dp(18f), brushInd)
            // 表达式 + 光标
            drawExpression(canvas, padL, padR)
            // 结果
            if (resultText != null) {
                brushResult.textSize = dp(28f)
                val resY = displayRect.bottom - dp(8f)
                drawRight(canvas, resultText!!, brushResult, padL, padR, resY)
            }
        }

        // 按键始终可见
        for (i in Keypad.rows.indices) {
            val keys = Keypad.rows[i]
            for (j in keys.indices) {
                val r = keyRects[i][j]
                val key = keys[j]
                paintKey(canvas, r, key, i, j)
            }
        }
    }

    /** GRAPH 模式：在 LCD 显示区绘制图像（多函数叠加），按键保留 */
    private fun drawGraphInDisplay(canvas: Canvas) {
        val gv = graphView ?: GraphView(context).also { graphView = it }
        syncGraphView(gv)

        val offX = displayRect.left + dp(3f)
        val offY = displayRect.top + dp(3f)
        val gw = (displayRect.width() - dp(6f)).toInt().coerceAtLeast(1)
        val gh = (displayRect.height() - dp(6f)).toInt().coerceAtLeast(1)
        gv.layout(0, 0, gw, gh)

        brushLcd.color = COLOR_LCD_BORDER
        canvas.drawRoundRect(displayRect, dp(10f), dp(10f), brushLcd)

        canvas.save()
        canvas.translate(offX, offY)
        canvas.clipRect(0f, 0f, gw.toFloat(), gh.toFloat())
        gv.draw(canvas)
        canvas.restore()

        brushLcdBorder.color = COLOR_LCD_BORDER
        brushLcdBorder.style = Paint.Style.STROKE
        brushLcdBorder.strokeWidth = dp(2f)
        canvas.drawRoundRect(displayRect, dp(10f), dp(10f), brushLcdBorder)

        drawGraphButtons(canvas)
    }

    /** 把 graphFuncs/graph3D/视角同步到 GraphView */
    private fun syncGraphView(gv: GraphView) {
        gv.funcs.clear()
        gv.mode2D = !graph3D
        if (graph3D) {
            gv.funcs.add(FuncItem(graph3DExpr, true, GraphView.PALETTE[0]))
        } else {
            gv.funcs.addAll(graphFuncs)
        }
        gv.invalidate()
    }

    /** 图像模式下叠加的小按钮：模式切换、放大、缩小、重置、编辑表达式、退出 */
    private fun graphButtons(): List<Pair<String, RectF>> {
        val size = dp(28f)
        val gap = dp(5f)
        val bottom = displayRect.bottom - dp(5f)
        val top = bottom - size
        val labels = listOf(if (graph3D) "3D" else "2D", "＋", "－", "⟲", "✎", "✕")
        var left = displayRect.left + dp(8f)
        val out = mutableListOf<Pair<String, RectF>>()
        for (l in labels) {
            out.add(l to RectF(left, top, left + size, bottom))
            left += size + gap
        }
        return out
    }

    private fun drawGraphButtons(canvas: Canvas) {
        val bg = Paint()
        bg.color = Color.argb(170, 20, 20, 26)
        bg.style = Paint.Style.FILL
        val tx = Paint()
        tx.color = Color.WHITE
        tx.textSize = dp(12f)
        tx.typeface = Typeface.DEFAULT_BOLD
        tx.textAlign = Paint.Align.CENTER
        for ((label, r) in graphButtons()) {
            canvas.drawRoundRect(r, dp(6f), dp(6f), bg)
            val ty = r.centerY() - (tx.ascent() + tx.descent()) / 2f
            canvas.drawText(label, r.centerX(), ty, tx)
        }
    }

    private fun onGraphButton(label: String) {
        val gv = graphView ?: return
        gv.mode2D = !graph3D
        when (label) {
            "2D", "3D" -> { graph3D = !graph3D; gv.mode2D = !graph3D }
            "＋" -> gv.zoomBy(1.4)
            "－" -> gv.zoomBy(1.0 / 1.4)
            "⟲" -> gv.resetView()
            "✎" -> enterGraphEditing()          // 回编辑状态，表达式保留
            "✕" -> exitGraph()                  // 退出图像模式
        }
        invalidate()
    }

    /** 进入 GRAPH 编辑状态：把当前函数 tokens 放进编辑缓冲，用本机数学键盘编辑 */
    private fun enterGraphEditing() {
        state.mode = Mode.GRAPH
        graphEditing = true
        val gv = graphView ?: GraphView(context).also { graphView = it }
        val expr = if (graph3D) graph3DExpr else graphFuncs.getOrNull(graphEditingIdx)?.expr ?: "sin(X)"
        val cur = GraphView.tokenize(expr)
        state.tokens.clear()
        state.tokens.addAll(cur)
        state.cursor = state.tokens.size
        resultText = null
        justEvaluated = false
        toast("用本机键盘输入，按 = 绘图")
        invalidate()
    }

    /** 编辑状态按 = 确认表达式并绘图 */
    private fun confirmGraphExpr() {
        val gv = graphView ?: GraphView(context).also { graphView = it }
        var toks = state.tokens.toList()
        if (toks.isEmpty()) {
            val fallback = if (graph3D) graph3DExpr else graphFuncs.getOrNull(graphEditingIdx)?.expr ?: "sin(X)"
            toks = GraphView.tokenize(fallback)
            state.tokens.clear(); state.tokens.addAll(toks); state.cursor = toks.size
        }
        val text = toks.joinToString("") { it.d }
        if (graph3D) {
            graph3DExpr = text
        } else {
            if (graphFuncs.isEmpty()) graphFuncs.add(FuncItem(text, true, GraphView.PALETTE[0]))
            else {
                val idx = graphEditingIdx.coerceIn(0, graphFuncs.size - 1)
                graphFuncs[idx] = graphFuncs[idx].copy(expr = text)
            }
        }
        syncGraphView(gv)
        graphEditing = false
        resultText = null
        invalidate()
    }

    /** 退出图像模式，回到 COMP */
    private fun exitGraph() {
        state.mode = Mode.COMP
        graphEditing = true
        state.clearAll()
        resultText = null
        invalidate()
    }

    /** 渲染单行表达式 + 光标 */
    private fun drawExpression(canvas: Canvas, padL: Float, padR: Float) {
        val tokens = state.tokens
        val exprY = displayRect.top + dp(50f)

        // 关键：必须复位对齐方式，否则空表达式分支设成 RIGHT 后会让所有 token 互相压住
        brushExprBold.textAlign = Paint.Align.LEFT
        brushExprBold.textSize = dp(24f)

        if (tokens.isEmpty()) {
            brushExprBold.textAlign = Paint.Align.RIGHT
            canvas.drawText("0", padR, exprY, brushExprBold)
            brushExprBold.textAlign = Paint.Align.LEFT
            if (cursorBlink) drawCursorAt(canvas, padR + dp(1f), exprY, brushExprBold)
            return
        }

        // 自适应字号，避免超出显示区
        val avail = padR - padL
        val rawWidth = tokens.sumOf { brushExprBold.measureText(it.d).toDouble() }.toFloat()
        if (avail > 0 && rawWidth > avail) {
            brushExprBold.textSize = (dp(24f) * avail / rawWidth).coerceIn(dp(10f), dp(24f))
        }

        drawLine(canvas, tokens, brushExprBold, padL, exprY)

        // 光标
        if (cursorBlink) {
            val cx = computeCursorX(tokens, state.cursor, brushExprBold, padL)
            drawCursorAt(canvas, cx, exprY, brushExprBold)
        }
    }

    private fun drawCursorAt(canvas: Canvas, cx: Float, baseline: Float, paint: Paint) {
        brushCursor.strokeWidth = dp(2f)
        canvas.drawLine(
            cx, baseline - paint.ascent() - dp(1f),
            cx, baseline + paint.descent() + dp(1f),
            brushCursor
        )
    }

    /** 绘制单个按键（美化版） */
    private fun paintKey(canvas: Canvas, r: RectF, key: Key, row: Int, col: Int) {
        // 按键颜色
        val color = keyColor(key.action, key.primary)
        brushKey.color = color
        brushKey.style = Paint.Style.FILL
        // 圆角 + 轻微阴影感
        canvas.drawRoundRect(r, dp(10f), dp(10f), brushKey)

        // 按压效果：顶部高光
        val hl = Paint()
        hl.color = Color.argb(30, 255, 255, 255)
        hl.style = Paint.Style.STROKE
        hl.strokeWidth = dp(1f)
        canvas.drawRoundRect(
            RectF(r.left + dp(1f), r.top + dp(1f), r.right - dp(1f), r.top + dp(r.height() * 0.3f)),
            dp(10f), dp(10f), hl
        )

        // 主标签
        val cx = r.centerX()
        val cy = r.centerY()
        brushPrimary.textAlign = Paint.Align.CENTER

        // 特殊按键更大字
        when (key.action) {
            "eq" -> brushPrimary.textSize = dp(24f)
            "ac" -> brushPrimary.textSize = dp(20f)
            "del" -> brushPrimary.textSize = dp(20f)
            "shift", "alpha" -> brushPrimary.textSize = dp(13f)
            "abc" -> brushPrimary.textSize = dp(15f)
            "mode" -> brushPrimary.textSize = dp(15f)
            in listOf("7","8","9","4","5","6","1","2","3","0") -> brushPrimary.textSize = dp(22f)
            else -> brushPrimary.textSize = dp(17f)
        }

        // 上下居中
        val textHeight = brushPrimary.descent() - brushPrimary.ascent()
        val topPos = cy - textHeight / 2f - brushPrimary.ascent()
        canvas.drawText(key.primary, cx, topPos, brushPrimary)
        // SHIFT 标签（左上）
        if (key.shift.isNotEmpty()) {
            brushShift.textAlign = Paint.Align.LEFT
            brushShift.textSize = dp(10f)
            canvas.drawText(key.shift, r.left + dp(4f), r.top + dp(12f), brushShift)
        }
        // ALPHA 标签（右上）
        if (key.alpha.isNotEmpty()) {
            brushAlpha.textAlign = Paint.Align.RIGHT
            brushAlpha.textSize = dp(10f)
            canvas.drawText(key.alpha, r.right - dp(4f), r.top + dp(12f), brushAlpha)
        }
    }

    private fun drawRight(canvas: Canvas, text: String, paint: Paint, left: Float, right: Float, baseline: Float) {
        paint.textAlign = Paint.Align.RIGHT
        var size = paint.textSize
        val min = dp(12f)
        while (paint.measureText(text) > (right - left) && size > min) {
            size *= 0.9f; paint.textSize = size
        }
        canvas.drawText(text, right, baseline, paint)
        // 恢复默认
        if (paint === brushResult) paint.textSize = dp(34f)
    }

    private fun indicatorLine(): String {
        val modeStr = when (state.mode) {
            Mode.COMP -> "COMP"
            Mode.STAT -> "STAT"
            Mode.EQN -> "EQN"
            Mode.MATRIX -> "MATRIX"
            Mode.VECTOR -> "VECTOR"
            Mode.BASEN -> "BASE-N"
            Mode.UNIT -> "UNIT"
            Mode.CONST -> "CONST"
            Mode.GRAPH -> if (graphEditing) "GRAPH✎(=绘图)" else "GRAPH"
        }
        val ang = when (state.cfg.angle) {
            AngleMode.DEG -> "D"
            AngleMode.RAD -> "R"
            AngleMode.GRA -> "G"
        }
        var s = "$modeStr $ang"
        if (!state.memory.isZero()) s += " M"
        if (shiftArmed) s += " S"
        if (alphaArmed) s += " A"
        return s
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y

        // GRAPH 绘图状态：悬浮按钮优先；按键区仍可用（MODE 弹菜单，其他输入键自动回编辑）；显示区触摸转发给 GraphView
        if (state.mode == Mode.GRAPH && !graphEditing) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                graphDownX = x; graphDownY = y; graphDownTime = event.eventTime
                for ((label, r) in graphButtons()) {
                    if (r.contains(x, y)) {
                        onGraphButton(label)
                        return true
                    }
                }
                // 按键区：MODE 直接弹出菜单（可切换模式退出），其他输入键先回编辑再执行
                for (i in keyRects.indices) {
                    val rects = keyRects[i]
                    for (j in rects.indices) {
                        if (rects[j].contains(x, y)) {
                            val key = Keypad.rows[i][j]
                            if (key.action != "mode") enterGraphEditing()
                            onKey(key)
                            return true
                        }
                    }
                }
            }
            if (graphView != null && displayRect.contains(x, y)) {
                val gv = graphView!!
                gv.mode2D = !graph3D
                val me = MotionEvent.obtain(event)
                me.offsetLocation(-(displayRect.left + dp(3f)), -(displayRect.top + dp(3f)))
                val handled = gv.onTouchEvent(me)
                me.recycle()
                // 短按弹全屏（ACTION_UP 且没拖拽）
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val dx = x - graphDownX; val dy = y - graphDownY
                    if (dx * dx + dy * dy < dp(10f) * dp(10f) && event.eventTime - graphDownTime < 300) {
                        showFullscreenGraph()
                    }
                }
                if (handled) invalidate()
            }
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // 显示区点击 → 光标定位
            if (displayRect.contains(x, y)) {
                val padL = displayRect.left + dp(14f)
                val idx = findCursorByX(state.tokens.toList(), x, brushExprBold, padL)
                state.cursor = idx
                justEvaluated = false
                invalidate()
                return true
            }
            // 按键命中测试
            for (i in keyRects.indices) {
                val rects = keyRects[i]
                for (j in rects.indices) {
                    if (rects[j].contains(x, y)) {
                        onKey(Keypad.rows[i][j])
                        return true
                    }
                }
            }
        }
        return true
    }

    private fun onKey(key: Key) {
        if (key.action == "shift") {
            shiftArmed = !shiftArmed; if (shiftArmed) alphaArmed = false; invalidate(); return
        }
        if (key.action == "alpha") {
            alphaArmed = !alphaArmed; if (alphaArmed) shiftArmed = false; invalidate(); return
        }
        val act = when {
            shiftArmed && key.shiftAction.isNotEmpty() -> key.shiftAction
            alphaArmed && key.alphaAction.isNotEmpty() -> key.alphaAction
            else -> key.action
        }
        // SHIFT 采用锁定模式：按一次后一直有效，直到再按一次 SHIFT 手动取消，
        // 方便连续输入 sin⁻¹ / cos⁻¹ / tan⁻¹ 等上档功能。ALPHA 仍为单次生效。
        alphaArmed = false
        perform(act)
        invalidate()
    }

    private fun startFreshIfNeeded() {
        if (justEvaluated) {
            state.clearAll(); justEvaluated = false
        }
    }
    private fun clearResult() { resultText = null }

    /** 刚按过 = 之后继续按运算符：用 Ans 接着算，而不是把旧表达式拿来续接。 */
    private fun beginOp() {
        if (justEvaluated) {
            state.clearAll()
            state.inputVar("Ans")
        }
        justEvaluated = false
    }

    private fun perform(act: String) {
        when {
            act.length == 1 && act[0].isDigit() -> {
                startFreshIfNeeded(); state.inputDigit(act.toInt()); clearResult()
            }
            act == "dot" -> { startFreshIfNeeded(); state.inputDot(); clearResult() }
            act == "add" -> { beginOp(); state.inputOperator("+"); clearResult() }
            act == "sub" -> { beginOp(); state.inputOperator("-"); clearResult() }
            act == "mul" -> { beginOp(); state.inputOperator("×"); clearResult() }
            act == "div" -> { beginOp(); state.inputOperator("÷"); clearResult() }
            act == "pow" -> { beginOp(); state.inputOperator("^"); clearResult() }
            act == "ncr" -> { beginOp(); state.inputOperator("C"); clearResult() }
            act == "npr" -> { beginOp(); state.inputOperator("P"); clearResult() }
            act == "lp" -> { startFreshIfNeeded(); state.inputLParen(); clearResult() }
            act == "rp" -> { justEvaluated = false; state.inputRParen(); clearResult() }
            act == "neg" -> { startFreshIfNeeded(); state.inputNegate(); clearResult() }
            act == "exp" -> { startFreshIfNeeded(); state.inputExp(); clearResult() }
            act == "del" -> { justEvaluated = false; state.backspace(); clearResult() }
            act == "ac" -> {
                state.clearAll(); state.cursor = 0; resultText = null; justEvaluated = false
                shiftArmed = false; alphaArmed = false   // AC 一并取消上档锁定
            }
            act == "left" -> { justEvaluated = false; state.moveCursorLeft() }
            act == "right" -> { justEvaluated = false; state.moveCursorRight() }
            act == "eq" -> {
                if (state.mode == Mode.GRAPH && graphEditing) confirmGraphExpr()
                else evaluate()
            }
            act == "abc" -> showLetterMenu()
            act == "Ans" -> { startFreshIfNeeded(); state.inputVar("Ans"); clearResult() }
            act == "PreAns" -> { startFreshIfNeeded(); state.inputVar("PreAns"); clearResult() }
            act == "pi" -> { startFreshIfNeeded(); state.inputConst("pi"); clearResult() }
            act == "e" -> { startFreshIfNeeded(); state.inputConst("e"); clearResult() }
            act == "i" -> { startFreshIfNeeded(); state.inputImag(); clearResult() }
            act == "angle" -> { startFreshIfNeeded(); state.inputAngle(); clearResult() }
            act == "mplus" -> memoryAdd(true)
            act == "mminus" -> memoryAdd(false)
            act == "sto" -> showStoreDialog()
            act == "rcl" -> showRecallDialog()
            act == "mode" -> showModeMenu()
            funcs.containsKey(act) -> { startFreshIfNeeded(); state.inputFunc(act, funcs[act]!!); clearResult() }
            posts.containsKey(act) -> { startFreshIfNeeded(); state.inputPostfix(act, posts[act]!!); clearResult() }
            act in varLetters -> { startFreshIfNeeded(); state.inputVar(act); clearResult() }
            else -> toast("未定义功能:$act")
        }
    }

    private fun evaluate() {
        try {
            val v = if (state.tokens.isEmpty()) state.ans else state.evaluateValue()
            resultText = Fmt.both(v, state.cfg)
            justEvaluated = true
        } catch (e: Exception) {
            resultText = e.message?.let { if (it.isEmpty()) "ERROR" else it } ?: "ERROR"
            justEvaluated = false
        }
    }

    private fun memoryAdd(plus: Boolean) {
        val v = if (state.tokens.isEmpty()) state.ans else {
            try {
                Parser(state.tokens.toList(), EvalCtx(state.buildVars(), state.cfg)).evaluate()
            } catch (e: Exception) { state.ans }
        }
        if (plus) state.memoryAdd(v) else state.memorySub(v)
        resultText = Fmt.complex(state.memory, state.cfg)
        justEvaluated = true
    }

    // ============ 对话框辅助 ============

    private fun toast(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    private fun listDialog(title: String, items: List<String>, onPick: (Int) -> Unit) {
        AlertDialog.Builder(context).setTitle(title)
            .setItems(items.toTypedArray()) { _, i -> onPick(i) }.show()
    }

    private fun inputDialog(title: String, hint: String, multiline: Boolean = false, onOk: (String) -> Unit) {
        val et = EditText(context)
        et.hint = hint
        et.setPadding(dp(16f).toInt(), dp(8f).toInt(), dp(16f).toInt(), dp(8f).toInt())
        et.inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else InputType.TYPE_CLASS_TEXT
        if (multiline) et.minLines = 3
        AlertDialog.Builder(context).setTitle(title).setView(et)
            .setPositiveButton("确定") { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("取消", null).show()
    }

    private fun resultDialog(title: String, text: String) {
        AlertDialog.Builder(context).setTitle(title).setMessage(text)
            .setPositiveButton("确定", null).show()
    }

    private fun parseDoubles(s: String): List<Double> =
        s.trim().split(Regex("[,\\s;]+")).filter { it.isNotEmpty() }.map { it.toDouble() }

    private fun parsePairs(s: String): List<Pair<Double, Double>> {
        val res = mutableListOf<Pair<Double, Double>>()
        for (seg in s.trim().split(';')) {
            val t = seg.trim(); if (t.isEmpty()) continue
            val d = t.split(Regex("[,\\s]+")).filter { it.isNotEmpty() }.map { it.toDouble() }
            if (d.size >= 2) res.add(d[0] to d[1])
        }
        return res
    }

    // ============ 存储 / 调取 ============

    private fun showStoreDialog() {
        val names = listOf("A", "B", "C", "D", "E", "F", "X", "Y", "M", "VctA", "VctB", "VctC")
        listDialog("STO 存入变量", names) { i ->
            val v = if (state.tokens.isEmpty()) state.ans else {
                try { Parser(state.tokens.toList(), EvalCtx(state.buildVars(), state.cfg)).evaluate() }
                catch (e: Exception) { state.ans }
            }
            state.storeVar(names[i], v)
            if (names[i].startsWith("Vct")) vectors[names[i].removePrefix("Vct")] = Vector.fromComplex(v)
            resultText = Fmt.complex(v, state.cfg)
            justEvaluated = true; invalidate()
        }
    }

    private fun showRecallDialog() {
        val names = listOf("A", "B", "C", "D", "E", "F", "X", "Y", "M", "VctA", "VctB", "VctC")
        listDialog("RCL 调取变量", names) { i ->
            startFreshIfNeeded(); state.inputVar(names[i]); clearResult(); invalidate()
        }
    }

    // ============ 模式菜单（合并原 SETUP） ============

    private fun showModeMenu() {
        val items = listOf(
            "1: 计算 COMP",
            "2: 复数 a+bi",
            "3: 统计 STAT",
            "4: 方程 EQN",
            "5: 矩阵 MATRIX",
            "6: 向量 VECTOR",
            "7: 进制转换 BASE-N",
            "8: 单位换算 UNIT",
            "9: 科学常数 CONST",
            "10: 函数图像 GRAPH (2D/3D)",
            "── 设置 ──",
            "角度: 度 DEG   (当前 ${angleLabel()})",
            "角度: 弧度 RAD",
            "角度: 百分度 GRA",
            "显示: 常规 Norm",
            "显示: 固定小数 Fix",
            "显示: 科学计数 Sci",
            "复数显示: ${complexFormLabel()}（点按切换）"
        )
        listDialog("菜单 / 设置", items) { i ->
            when (i) {
                0, 1 -> state.mode = Mode.COMP
                2 -> { state.mode = Mode.STAT; showStatMenu() }
                3 -> { state.mode = Mode.EQN; showEqnMenu() }
                4 -> { state.mode = Mode.MATRIX; showMatrixMenu() }
                5 -> { state.mode = Mode.VECTOR; showVectorMenu() }
                6 -> { state.mode = Mode.BASEN; showBaseMenu() }
                7 -> { state.mode = Mode.UNIT; showUnitCatMenu() }
                8 -> { state.mode = Mode.CONST; showConstList() }
                9 -> { state.mode = Mode.GRAPH; showGraphMenu() }
                10 -> {}
                11 -> state.cfg.angle = AngleMode.DEG
                12 -> state.cfg.angle = AngleMode.RAD
                13 -> state.cfg.angle = AngleMode.GRA
                14 -> state.cfg.dispMode = DispMode.NORM
                15 -> fixDigits()
                16 -> sciDigits()
                17 -> state.cfg.complexForm =
                    if (state.cfg.complexForm == ComplexForm.RECT) ComplexForm.POLAR else ComplexForm.RECT
            }
            invalidate()
        }
    }

    private fun complexFormLabel(): String = when (state.cfg.complexForm) {
        ComplexForm.RECT -> "直角 a+bi"
        ComplexForm.POLAR -> "极坐标 r∠θ"
    }

    private fun angleLabel(): String = when (state.cfg.angle) {
        AngleMode.DEG -> "DEG"; AngleMode.RAD -> "RAD"; AngleMode.GRA -> "GRA"
    }

    // ============ 字母 / 函数选择器（屏幕键盘直接输公式） ============

    /** "字母" 键：先选字母 A–Z，或选函数名（sin、arcsin…）直接插入整词 */
    private fun showLetterMenu() {
        listDialog("输入字母 / 函数", listOf("字母 A–Z（含 X、Y）", "函数 sin cos arcsin …")) { i ->
            if (i == 0) showLetterGrid() else showFuncPicker()
        }
    }

    private fun showLetterGrid() {
        val letters = ('A'..'Z').map { it.toString() }
        listDialog("选择字母", letters) { i ->
            startFreshIfNeeded()
            state.inputVar(letters[i])
            clearResult()
            invalidate()
        }
    }

    /** 函数名 → 内部函数标识（arcsin 与 asin 等价） */
    private val funcPicker = listOf(
        "sin" to "sin", "cos" to "cos", "tan" to "tan",
        "arcsin" to "asin", "arccos" to "acos", "arctan" to "atan",
        "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh",
        "arsinh" to "asinh", "arcosh" to "acosh", "artanh" to "atanh",
        "ln" to "ln", "log" to "log", "√" to "sqrt", "³√" to "cbrt",
        "Abs" to "abs", "Exp" to "exp", "10^" to "tenx", "e^" to "ex"
    )

    private fun showFuncPicker() {
        listDialog("选择函数", funcPicker.map { it.first }) { i ->
            startFreshIfNeeded()
            val (label, fn) = funcPicker[i]
            state.inputFunc(fn, label)
            clearResult()
            invalidate()
        }
    }


    private fun showGraphMenu() {
        if (graph3D) {
            // 3D 模式只有一条表达式
            listDialog("GRAPH 3D", listOf(
                "切换到 2D 模式",
                "编辑 z=f(X,Y)（=绘图）",
                "全屏显示",
                "重置视角 ⟲",
                "退出 GRAPH")
            ) { i ->
                when (i) {
                    0 -> { graph3D = false; showGraphMenu() }
                    1 -> enterGraphEditing()
                    2 -> showFullscreenGraph()
                    3 -> { graphView?.resetView(); invalidate() }
                    4 -> exitGraph()
                }
            }
            return
        }
        // 2D 模式：多函数管理
        val items = mutableListOf(
            "切换到 3D 模式",
            "＋ 新增函数 (最多10条)",
            "全屏显示（点画面也可）",
            "重置视角 ⟲",
            "退出 GRAPH",
        )
        for ((idx, f) in graphFuncs.withIndex()) {
            val mark = if (f.enabled) "√" else "×"
            items.add("[$mark] f${idx + 1}(X) = ${f.expr}")
        }
        listDialog("GRAPH 2D（${graphFuncs.size}/10）", items) { i ->
            when (i) {
                0 -> { graph3D = true; showGraphMenu() }
                1 -> addGraphFunc()
                2 -> showFullscreenGraph()
                3 -> { graphView?.resetView(); invalidate() }
                4 -> exitGraph()
                else -> {
                    val idx = i - 5
                    if (idx in graphFuncs.indices) showGraphFuncActions(idx)
                }
            }
        }
    }

    private fun addGraphFunc() {
        if (graphFuncs.size >= 10) { toast("最多 10 条函数"); return }
        val newIdx = graphFuncs.size
        graphFuncs.add(FuncItem("X", true, GraphView.PALETTE[newIdx]))
        graphEditingIdx = newIdx
        graph3D = false
        enterGraphEditing()
    }

    private fun showGraphFuncActions(idx: Int) {
        val f = graphFuncs[idx]
        val items = listOf(
            if (f.enabled) "禁用" else "启用",
            "编辑 f${idx + 1}",
            if (graphFuncs.size > 1) "删除 f${idx + 1}" else null,
        ).filterNotNull()
        listDialog("f${idx + 1}(X)", items) { i ->
            when (i) {
                0 -> { graphFuncs[idx] = f.copy(enabled = !f.enabled); invalidate() }
                1 -> { graphEditingIdx = idx; enterGraphEditing() }
                2 -> {
                    graphFuncs.removeAt(idx)
                    if (graphFuncs.isEmpty()) graphFuncs.add(FuncItem("X", true, GraphView.PALETTE[0]))
                    graphEditingIdx = graphEditingIdx.coerceIn(0, graphFuncs.size - 1)
                    invalidate()
                }
            }
        }
    }

    // ============ 全屏图像 ============

    private fun showFullscreenGraph() {
        val activity = context as? android.app.Activity ?: return
        val dialog = AlertDialog.Builder(context)
            .setCancelable(true)
            .create()
        dialog.window?.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        val container = android.widget.FrameLayout(context)
        val gv = GraphView(context).also { v ->
            v.mode2D = !graph3D
            if (graph3D) v.funcs.add(FuncItem(graph3DExpr, true, GraphView.PALETTE[0]))
            else v.funcs.addAll(graphFuncs)
            v.xMin = graphView?.xMin ?: -10.0
            v.xMax = graphView?.xMax ?: 10.0
            v.yMin = graphView?.yMin ?: -6.0
            v.yMax = graphView?.yMax ?: 6.0
            v.azim = graphView?.azim ?: -35.0
            v.elev = graphView?.elev ?: 30.0
            v.zoom3D = graphView?.zoom3D ?: 1.0
        }

        // 顶部控制栏
        val closeBtn = android.widget.Button(context).apply {
            text = "退出"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(170, 30, 30, 36))
            textSize = 12f
            setOnClickListener { dialog.dismiss() }
        }
        val resetBtn = android.widget.Button(context).apply {
            text = "⟲"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(170, 30, 30, 36))
            textSize = 14f
            setOnClickListener { gv.resetView(); gv.invalidate() }
        }
        val bg = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(170, 20, 20, 26))
            addView(resetBtn, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(closeBtn, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            setPadding(dp(8f).toInt(), dp(4f).toInt(), dp(8f).toInt(), dp(4f).toInt())
        }

        val lp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        val gvLp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        val bgLp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.RIGHT; topMargin = dp(8f).toInt(); rightMargin = dp(8f).toInt() }

        container.addView(gv, gvLp)
        container.addView(bg, bgLp)

        dialog.setView(container, 0, 0, 0, 0)
        dialog.show()
    }

    // ============ 设置项（由合并后的菜单调用） ============

    private fun fixDigits() {
        inputDialog("小数位数 Fix", "输入 0-9") { s ->
            runCatching {
                val n = s.trim().toInt().coerceIn(0, 9)
                state.cfg.dispMode = DispMode.FIX; state.cfg.digits = n
            }
        }
    }
    private fun sciDigits() {
        inputDialog("有效位数 Sci", "输入 0-9") { s ->
            runCatching {
                val n = s.trim().toInt().coerceIn(1, 9)
                state.cfg.dispMode = DispMode.SCI; state.cfg.digits = n
            }
        }
    }

    // ============ 统计 ============

    private fun showStatMenu() {
        listDialog("统计 STAT", listOf("单变量统计 (1-VAR)", "双变量/线性回归 (2-VAR)")) { i ->
            if (i == 0) stat1() else stat2()
        }
    }
    private fun stat1() {
        inputDialog("单变量数据", "如 1,2,3,4,5（逗号/空格分隔）", multiline = true) { s ->
            runCatching {
                val xs = parseDoubles(s)
                if (xs.size < 2) { toast("至少需要 2 个数据"); return@runCatching }
                val r = Modes.stat1Var(xs)
                resultDialog("统计结果", r.lines.joinToString("\n") { "${it.first} = ${it.second}" })
            }.onFailure { toast("输入格式错误") }
        }
    }
    private fun stat2() {
        inputDialog("双变量数据", "每对 x,y 用分号分隔，如 1,1; 2,3; 3,5", multiline = true) { s ->
            runCatching {
                val xy = parsePairs(s)
                if (xy.size < 2) { toast("至少需要 2 对数据"); return@runCatching }
                val r = Modes.stat2Var(xy)
                resultDialog("回归结果", r.lines.joinToString("\n") { "${it.first} = ${it.second}" })
            }.onFailure { toast("输入格式错误") }
        }
    }

    // ============ 方程 ============

    private fun showEqnMenu() {
        val items = listOf("二元一次方程组", "三元一次方程组", "四元一次方程组", "二次方程 ax²+bx+c=0", "三次方程 ax³+bx²+cx+d=0")
        listDialog("方程 EQN", items) { i ->
            when (i) { 0 -> linearEqn(2); 1 -> linearEqn(3); 2 -> linearEqn(4)
                3 -> polyEqn(2); 4 -> polyEqn(3) }
        }
    }
    private fun linearEqn(n: Int) {
        inputDialog("${n} 元一次方程组", "每行输入 ${n + 1} 个系数(含常数项)，回车分隔各行", multiline = true) { s ->
            runCatching {
                val lines = s.trim().split(Regex("[\n]"))
                if (lines.size != n) { toast("需要 $n 行系数"); return@runCatching }
                val a = Array(n) { DoubleArray(n) }; val b = DoubleArray(n)
                for (r in 0 until n) {
                    val c = parseDoubles(lines[r])
                    if (c.size != n + 1) { toast("第 ${r + 1} 行需要 ${n + 1} 个数"); return@runCatching }
                    for (j in 0 until n) a[r][j] = c[j]; b[r] = c[n]
                }
                val x = Modes.solveLinear(n, a, b)
                val names = listOf("x", "y", "z", "t", "u")
                resultDialog("方程解", (0 until n).joinToString("\n") { "${names[it]} = ${Fmt.num(x[it])}" })
            }.onFailure { toast("输入格式错误") }
        }
    }
    private fun polyEqn(degree: Int) {
        inputDialog(if (degree == 2) "二次方程系数" else "三次方程系数",
            if (degree == 2) "输入 a b c（空格分隔）" else "输入 a b c d（空格分隔）") { s ->
            runCatching {
                val c = parseDoubles(s)
                val roots = if (degree == 2) {
                    if (c.size != 3) { toast("需要 3 个系数"); return@runCatching }
                    Modes.solveQuadratic(c[0], c[1], c[2])
                } else {
                    if (c.size != 4) { toast("需要 4 个系数"); return@runCatching }
                    Modes.solveCubic(c[0], c[1], c[2], c[3])
                }
                resultDialog("方程的根", roots.mapIndexed { i, r -> "x${i + 1} = ${Fmt.complex(r, state.cfg)}" }.joinToString("\n"))
            }.onFailure { toast("输入格式错误") }
        }
    }

    // ============ 矩阵 / 向量 / 进制 / 单位 / 常数（原逻辑保留） ============

    private fun showMatrixMenu() {
        listDialog("矩阵 MATRIX", listOf("定义矩阵", "A+B", "A×B", "det(A)", "A⁻¹", "转置 Aᵀ")) { i ->
            when (i) { 0 -> pickMatrix("定义矩阵") { defineMatrix(it) }
                1 -> matrixBinary("+"); 2 -> matrixBinary("×")
                3 -> matrixUnary { m -> resultDialog("行列式", Fmt.num(m.det())) }
                4 -> matrixUnary { m -> resultDialog("逆矩阵", m.inverse().toStringFmt()) }
                5 -> matrixUnary { m -> resultDialog("转置", m.transpose().toStringFmt()) } }
        }
    }
    private fun pickMatrix(title: String, onPick: (String) -> Unit) {
        val names = listOf("MatA", "MatB", "MatC", "MatD")
        listDialog(title, names) { i -> onPick(names[i].removePrefix("Mat")) }
    }
    private fun matrixOf(name: String): Matrix? = matrices[name]
    private fun defineMatrix(name: String) {
        inputDialog("$name 维度", "输入 行 列，如 2 2") { s ->
            runCatching {
                val d = parseDoubles(s)
                if (d.size != 2 || d[0].toInt() <= 0 || d[1].toInt() <= 0) { toast("维度格式错误"); return@runCatching }
                val rows = d[0].toInt(); val cols = d[1].toInt()
                inputDialog("$name 元素", "按行输入 ${rows * cols} 个数，空格分隔") { v ->
                    runCatching {
                        val vals = parseDoubles(v)
                        if (vals.size != rows * cols) { toast("需要 ${rows * cols} 个数"); return@runCatching }
                        val data = Array(rows) { i -> DoubleArray(cols) { j -> vals[i * cols + j] } }
                        matrices[name] = Matrix(rows, cols, data); toast("Mat$name 已定义")
                    }.onFailure { toast("输入格式错误") }
                }
            }.onFailure { toast("输入格式错误") }
        }
    }
    private fun matrixBinary(op: String) {
        pickMatrix("左矩阵") { a ->
            matrixOf(a) ?: run { toast("Mat$a 未定义"); return@pickMatrix }
            pickMatrix("右矩阵") { b ->
                val ma = matrixOf(a)!!; val mb = matrixOf(b)!!
                runCatching { val r = if (op == "+") ma + mb else ma * mb
                    resultDialog("结果", r.toStringFmt()) }
                .onFailure { e -> toast(e.message ?: "Dimension ERROR") }
            }
        }
    }
    private fun matrixUnary(op: (Matrix) -> Unit) {
        pickMatrix("选择矩阵") { a ->
            val m = matrixOf(a)
            if (m == null) toast("Mat$a 未定义")
            else runCatching { op(m) }.onFailure { e -> toast(e.message ?: "ERROR") }
        }
    }

    private fun showVectorMenu() {
        val items = listOf(
            "定义向量 (2D→复数可用)",
            "加法 A+B", "减法 A−B", "数乘 k·A",
            "点积 A·B", "叉积 A×B", "夹角 θ",
            "模 |A|", "单位向量 Â", "投影 Proj(A→B)"
        )
        listDialog("向量 VECTOR", items) { i ->
            when (i) {
                0 -> pickVector("定义向量") { defineVector(it) }
                1 -> vectorBinary("add")
                2 -> vectorBinary("sub")
                3 -> vectorScale()
                4 -> vectorBinary("dot")
                5 -> vectorBinary("cross")
                6 -> vectorAngle()
                7 -> vectorUnary("模") { resultDialog("模", Fmt.num(it.norm())) }
                8 -> vectorUnary("单位向量") { resultDialog("单位向量", it.unit().toStringFmt()) }
                9 -> vectorProj()
            }
        }
    }
    private fun pickVector(title: String, onPick: (String) -> Unit) {
        listDialog(title, listOf("VctA", "VctB", "VctC")) { i -> onPick(listOf("A", "B", "C")[i]) }
    }
    private fun vec(n: String): Vector? = vectors[n]
    private fun defineVector(name: String) {
        inputDialog("Vct$name 分量", "输入 x y 或 x y z（空格分隔；2D 可参与复数四则）") { s ->
            runCatching {
                val d = parseDoubles(s)
                if (d.size !in 2..3) { toast("需要 2 或 3 个分量"); return@runCatching }
                val v = Vector.fromReals(if (d.size == 3) doubleArrayOf(d[0], d[1], d[2]) else doubleArrayOf(d[0], d[1]))
                vectors[name] = v
                if (v.dim == 2) {
                    state.storeVar("Vct$name", v.toComplex())
                    toast("Vct$name = ${Fmt.complex(v.toComplex(), state.cfg)}，可 RCL 插入表达式直接四则")
                } else {
                    state.storeVar("Vct$name", Complex.ZERO)
                    toast("Vct$name 已定义（3D 用于点积/叉积等）")
                }
            }.onFailure { toast("输入格式错误") }
        }
    }
    private fun vectorBinary(mode: String) {
        pickVector("第一个向量") { a ->
            val va = vec(a) ?: run { toast("Vct$a 未定义"); return@pickVector }
            pickVector("第二个向量") { b ->
                val vb = vec(b) ?: run { toast("Vct$b 未定义"); return@pickVector }
                runCatching {
                    when (mode) {
                        "add" -> resultDialog("和", (va + vb).toStringFmt())
                        "sub" -> resultDialog("差", (va - vb).toStringFmt())
                        "dot" -> resultDialog("点积", Fmt.complex(va.dot(vb), state.cfg))
                        "cross" -> resultDialog("叉积", va.cross(vb).toStringFmt())
                    }
                }.onFailure { e -> toast(e.message ?: "Dimension ERROR") }
            }
        }
    }
    private fun vectorScale() {
        pickVector("选择向量") { a ->
            val va = vec(a) ?: run { toast("Vct$a 未定义"); return@pickVector }
            inputDialog("数乘 k·Vct$a", "输入标量 k") { s ->
                runCatching {
                    val k = s.trim().toDouble()
                    resultDialog("k·Vct$a", (va * k).toStringFmt())
                }.onFailure { toast("输入格式错误") }
            }
        }
    }
    private fun vectorAngle() {
        pickVector("第一个向量") { a ->
            val va = vec(a) ?: run { toast("Vct$a 未定义"); return@pickVector }
            pickVector("第二个向量") { b ->
                val vb = vec(b) ?: run { toast("Vct$b 未定义"); return@pickVector }
                runCatching { resultDialog("夹角", "${Fmt.num(va.angleDeg(vb))}°") }
                    .onFailure { e -> toast(e.message ?: "ERROR") }
            }
        }
    }
    private fun vectorUnary(label: String, op: (Vector) -> Unit) {
        pickVector("选择向量") { a -> vec(a)?.let(op) ?: toast("Vct$a 未定义") }
    }
    private fun vectorProj() {
        pickVector("被投影向量 A") { a ->
            val va = vec(a) ?: run { toast("Vct$a 未定义"); return@pickVector }
            pickVector("方向向量 B") { b ->
                val vb = vec(b) ?: run { toast("Vct$b 未定义"); return@pickVector }
                runCatching { resultDialog("投影", va.proj(vb).toStringFmt()) }
                    .onFailure { e -> toast(e.message ?: "ERROR") }
            }
        }
    }
    private fun showBaseMenu() {
        listDialog("进制 BASE-N", listOf("数值转换", "逻辑运算 AND/OR/XOR/NOT")) { i ->
            if (i == 0) baseConvertFlow() else logicFlow()
        }
    }
    private fun baseConvertFlow() {
        listDialog("源进制", listOf("DEC", "HEX", "BIN", "OCT")) { fi ->
            val from = listOf(Modes.Base.DEC, Modes.Base.HEX, Modes.Base.BIN, Modes.Base.OCT)[fi]
            inputDialog("输入数值", "在 ${from.label} 进制中输入") { s ->
                runCatching {
                    val v = s.trim()
                    val dec = Modes.baseConvert(v, from, Modes.Base.DEC).toLong()
                    val sb = StringBuilder()
                    sb.append("DEC: ").append(dec).append('\n')
                    sb.append("HEX: ").append(java.lang.Long.toHexString(dec).uppercase()).append('\n')
                    sb.append("BIN: ").append(java.lang.Long.toBinaryString(dec)).append('\n')
                    sb.append("OCT: ").append(java.lang.Long.toOctalString(dec))
                    resultDialog("转换结果", sb.toString())
                }.onFailure { toast("输入格式错误") }
            }
        }
    }
    private fun logicFlow() {
        listDialog("逻辑运算", listOf("AND", "OR", "XOR", "NOT")) { oi ->
            val op = listOf("and", "or", "xor", "not")[oi]
            inputDialog(op.uppercase(), if (op == "not") "输入一个数" else "输入两个数，空格分隔") { s ->
                runCatching {
                    val parts = s.trim().split(Regex("\\s+"))
                    val a = parseBaseLong(parts[0])
                    val res = if (op == "not") Modes.notOp(a, 32)
                    else Modes.logicOp(a, parseBaseLong(parts[1]), op)
                    resultDialog("结果", "DEC: $res\nHEX: ${java.lang.Long.toHexString(res).uppercase()}")
                }.onFailure { toast("输入格式错误") }
            }
        }
    }
    private fun parseBaseLong(s: String): Long =
        if (s.startsWith("0x", true)) s.substring(2).toLong(16) else s.toLong()

    private fun showUnitCatMenu() {
        listDialog("单位类别", Modes.unitCategories.map { it.name }) { i -> showUnitFrom(i) }
    }
    private fun showUnitFrom(cat: Int) {
        listDialog("从单位", Modes.unitCategories[cat].units.map { it.name }) { f -> showUnitTo(cat, f) }
    }
    private fun showUnitTo(cat: Int, fromIdx: Int) {
        listDialog("到单位", Modes.unitCategories[cat].units.map { it.name }) { t ->
            val cat2 = Modes.unitCategories[cat]
            inputDialog("输入数值", "${cat2.units[fromIdx].name} → ${cat2.units[t].name}") { s ->
                runCatching {
                    val v = s.trim().toDouble()
                    val r = Modes.convertUnit(v, cat2.units[fromIdx], cat2.units[t])
                    resultDialog("换算结果", "${Fmt.num(v)} ${cat2.units[fromIdx].name}\n= ${Fmt.num(r)} ${cat2.units[t].name}")
                }.onFailure { toast("输入格式错误") }
            }
        }
    }

    private fun showConstList() {
        listDialog("科学常数", Modes.constants.map { "${it.symbol}  ${it.name}" }) { i ->
            val c = Modes.constants[i]
            startFreshIfNeeded()
            state.tokens.add(NumTok(Complex.fromReal(c.value),
                java.lang.String.format("%.9g", c.value).replace('e', 'ᴱ')))
            state.cursor = state.tokens.size
            clearResult(); invalidate()
        }
    }
}
