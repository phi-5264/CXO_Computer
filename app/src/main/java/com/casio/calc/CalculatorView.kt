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
    private val vectors = mutableMapOf<String, DoubleArray>()

    // GRAPH 模式
    private var graphView: GraphView? = null
    private var graphExpr2D: String = "sin(X)"
    private var graphExpr3D: String = "sin(sqrt(X*X+Y*Y))/(sqrt(X*X+Y*Y)+0.001)"

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

        // LCD 显示屏 —— GRAPH 模式下显示区改为函数图像
        if (state.mode == Mode.GRAPH) {
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
                val resY = displayRect.bottom - dp(14f)
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

        // GRAPH 模式下在按键区最右上角显示返回图标提示
        if (state.mode == Mode.GRAPH) {
            brushInd.textAlign = Paint.Align.LEFT
            brushInd.textSize = dp(12f)
            canvas.drawText("← 按菜单退出图像模式", displayRect.left + dp(8f), displayRect.bottom - dp(8f), brushInd)
        }
    }

    /** GRAPH 模式：在 LCD 显示区里画图像，按键保留 */
    private fun drawGraphInDisplay(canvas: Canvas) {
        if (graphView == null) graphView = GraphView(context)
        val gv = graphView!!
        gv.mode2D = true
        gv.expr2D = graphExpr2D
        gv.expr3D = graphExpr3D
        // 让 GraphView 只占 displayRect 区域
        gv.layout(
            displayRect.left.toInt() + 4,
            displayRect.top.toInt() + 4,
            displayRect.right.toInt() - 4,
            displayRect.bottom.toInt() - 4
        )
        gv.draw(canvas)
        brushLcdBorder.color = COLOR_LCD_BORDER
        brushLcdBorder.style = Paint.Style.STROKE
        brushLcdBorder.strokeWidth = dp(2f)
        canvas.drawRoundRect(displayRect, dp(10f), dp(10f), brushLcdBorder)
    }

    /** 渲染表达式 + 光标（稳定版：先 measure 再 layout 再 draw） */
    private fun drawExpression(canvas: Canvas, padL: Float, padR: Float) {
        val tokens = state.tokens
        val exprY = displayRect.top + dp(62f)

        if (tokens.isEmpty()) {
            brushExprBold.textAlign = Paint.Align.RIGHT
            brushExprBold.textSize = dp(26f)
            canvas.drawText("0", padR, exprY, brushExprBold)
            if (cursorBlink) drawCursorAt(canvas, padR + dp(1f), exprY, brushExprBold)
            return
        }

        // 稳定的三步流程
        val (tree, _) = layoutAndRender(tokens, brushExprBold, padL, exprY, canvas)

        // 光标
        if (cursorBlink) {
            val cx = computeCursorX(tokens, state.cursor, brushExprBold, padL, exprY)
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
            in listOf("7","8","9","4","5","6","1","2","3","0") -> brushPrimary.textSize = dp(22f)
            else -> brushPrimary.textSize = dp(17f)
        }

        // 数字键字更大更粗
        val text = if (row == 8 && col == 4) "=" else key.primary
        // 上下居中
        val textHeight = brushPrimary.descent() - brushPrimary.ascent()
        val topPos = cy - textHeight / 2f - brushPrimary.ascent()
        canvas.drawText(text, cx, topPos, brushPrimary)

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
            Mode.GRAPH -> "GRAPH"
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

        // GRAPH 模式：只有显示区的触摸转发给 GraphView，按键继续可用
        if (state.mode == Mode.GRAPH && graphView != null && displayRect.contains(x, y)) {
            graphView!!.onTouchEvent(event)
            invalidate()
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // 非 GRAPH 模式：显示区点击 → 光标定位
            if (state.mode != Mode.GRAPH && displayRect.contains(x, y)) {
                val padL = displayRect.left + dp(14f)
                val exprY = displayRect.top + dp(62f)
                val idx = findCursorByX(state.tokens.toList(), x, brushExprBold, padL, exprY)
                state.cursor = idx
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
        shiftArmed = false; alphaArmed = false
        perform(act)
        invalidate()
    }

    private fun startFreshIfNeeded() {
        if (justEvaluated) {
            state.clearAll(); justEvaluated = false
        }
    }
    private fun clearResult() { resultText = null }

    private fun perform(act: String) {
        when {
            act.length == 1 && act[0].isDigit() -> {
                startFreshIfNeeded(); state.inputDigit(act.toInt()); clearResult()
            }
            act == "dot" -> { startFreshIfNeeded(); state.inputDot(); clearResult() }
            act == "add" -> { justEvaluated = false; state.inputOperator("+"); clearResult() }
            act == "sub" -> { justEvaluated = false; state.inputOperator("-"); clearResult() }
            act == "mul" -> { justEvaluated = false; state.inputOperator("×"); clearResult() }
            act == "div" -> { justEvaluated = false; state.inputOperator("÷"); clearResult() }
            act == "pow" -> { justEvaluated = false; state.inputOperator("^"); clearResult() }
            act == "ncr" -> { justEvaluated = false; state.inputOperator("C"); clearResult() }
            act == "npr" -> { justEvaluated = false; state.inputOperator("P"); clearResult() }
            act == "lp" -> { startFreshIfNeeded(); state.inputLParen(); clearResult() }
            act == "rp" -> { justEvaluated = false; state.inputRParen(); clearResult() }
            act == "neg" -> { startFreshIfNeeded(); state.inputNegate(); clearResult() }
            act == "exp" -> { startFreshIfNeeded(); state.inputExp(); clearResult() }
            act == "del" -> { justEvaluated = false; state.backspace(); clearResult() }
            act == "ac" -> { state.clearAll(); state.cursor = 0; resultText = null; justEvaluated = false }
            act == "left" -> state.moveCursorLeft()
            act == "right" -> state.moveCursorRight()
            act == "eq" -> evaluate()
            act == "Ans" -> { startFreshIfNeeded(); state.inputVar("Ans"); clearResult() }
            act == "PreAns" -> { startFreshIfNeeded(); state.inputVar("PreAns"); clearResult() }
            act == "pi" -> { startFreshIfNeeded(); state.inputConst("pi"); clearResult() }
            act == "e" -> { startFreshIfNeeded(); state.inputConst("e"); clearResult() }
            act == "i" -> { startFreshIfNeeded(); state.inputImag(); clearResult() }
            act == "mplus" -> memoryAdd(true)
            act == "mminus" -> memoryAdd(false)
            act == "sto" -> showStoreDialog()
            act == "rcl" -> showRecallDialog()
            act == "mode" -> showModeMenu()
            act == "setup" -> showSetupMenu()
            funcs.containsKey(act) -> { startFreshIfNeeded(); state.inputFunc(act, funcs[act]!!); clearResult() }
            posts.containsKey(act) -> { startFreshIfNeeded(); state.inputPostfix(act, posts[act]!!); clearResult() }
            act in varLetters -> { startFreshIfNeeded(); state.inputVar(act); clearResult() }
            else -> toast("未定义功能:$act")
        }
    }

    private fun evaluate() {
        try {
            resultText = state.evaluate(); justEvaluated = true
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
        val names = listOf("A", "B", "C", "D", "E", "F", "X", "Y", "M")
        listDialog("STO 存入变量", names) { i ->
            val v = if (state.tokens.isEmpty()) state.ans else {
                try { Parser(state.tokens.toList(), EvalCtx(state.buildVars(), state.cfg)).evaluate() }
                catch (e: Exception) { state.ans }
            }
            state.storeVar(names[i], v)
            resultText = Fmt.complex(v, state.cfg)
            justEvaluated = true; invalidate()
        }
    }

    private fun showRecallDialog() {
        val names = listOf("A", "B", "C", "D", "E", "F", "X", "Y", "M")
        listDialog("RCL 调取变量", names) { i ->
            startFreshIfNeeded(); state.inputVar(names[i]); clearResult(); invalidate()
        }
    }

    // ============ 模式菜单 ============

    private fun showModeMenu() {
        val items = listOf(
            "1: 计算 COMP",
            "2: 复数 a+bi (COMP, i 直接可用)",
            "3: 统计 STAT",
            "4: 方程 EQN",
            "5: 矩阵 MATRIX",
            "6: 向量 VECTOR",
            "7: 进制转换 BASE-N",
            "8: 单位换算 UNIT",
            "9: 科学常数 CONST",
            "10: 函数图像 GRAPH (2D/3D)"
        )
        listDialog("模式菜单", items) { i ->
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
            }
            invalidate()
        }
    }

    private fun showGraphMenu() {
        listDialog("函数图像", listOf("2D: y = f(X)", "3D: z = f(X,Y)")) { i ->
            if (i == 0) open2DGraph() else open3DGraph()
        }
    }

    private fun open2DGraph() {
        inputDialog("2D 函数表达式", "用 X 表示自变量，如 sin(X)、X^2-3*X+1") { s ->
            graphExpr2D = s.ifBlank { "sin(X)" }
            state.mode = Mode.GRAPH
            val gv = graphView ?: GraphView(context).also { graphView = it }
            gv.mode2D = true
            invalidate()
        }
    }

    private fun open3DGraph() {
        inputDialog("3D 曲面表达式", "用 X, Y 表示自变量，如 X^2-Y^2、sin(sqrt(X*X+Y*Y))") { s ->
            graphExpr3D = s.ifBlank { "X*X-Y*Y" }
            state.mode = Mode.GRAPH
            val gv = graphView ?: GraphView(context).also { graphView = it }
            gv.mode2D = false
            invalidate()
        }
    }

    // ============ 设置 ============

    private fun showSetupMenu() {
        val items = listOf(
            "角度单位: 度 DEG", "角度单位: 弧度 RAD", "角度单位: 百分度 GRA",
            "显示格式: 常规 Norm", "显示格式: 固定小数 Fix", "显示格式: 科学计数 Sci"
        )
        listDialog("设置", items) { i ->
            when (i) {
                0 -> state.cfg.angle = AngleMode.DEG
                1 -> state.cfg.angle = AngleMode.RAD
                2 -> state.cfg.angle = AngleMode.GRA
                3 -> state.cfg.dispMode = DispMode.NORM
                4 -> fixDigits()
                5 -> sciDigits()
            }
            invalidate()
        }
    }

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
        listDialog("向量 VECTOR", listOf("定义向量", "点积 A·B", "叉积 A×B", "夹角 θ", "模 |A|", "加法 A+B")) { i ->
            when (i) { 0 -> pickVector("定义向量") { defineVector(it) }
                1 -> vectorBinary("dot"); 2 -> vectorBinary("cross"); 3 -> vectorAngle()
                4 -> vectorUnary("模") { v -> resultDialog("模", Fmt.num(kotlin.math.sqrt(v.sumOf { it * it }))) }
                5 -> vectorBinary("add") }
        }
    }
    private fun pickVector(title: String, onPick: (String) -> Unit) {
        listDialog(title, listOf("VctA", "VctB", "VctC")) { i -> onPick(listOf("A", "B", "C")[i]) }
    }
    private fun defineVector(name: String) {
        inputDialog("Vct$name 分量", "输入 x y z（空格分隔，2D 可只输入 x y）") { s ->
            runCatching {
                val d = parseDoubles(s)
                if (d.size < 2) { toast("至少需要 2 个分量"); return@runCatching }
                vectors[name] = doubleArrayOf(d[0], d[1], if (d.size >= 3) d[2] else 0.0)
                toast("Vct$name 已定义")
            }.onFailure { toast("输入格式错误") }
        }
    }
    private fun vec(n: String): DoubleArray? = vectors[n]
    private fun vectorBinary(mode: String) {
        pickVector("第一个向量") { a ->
            vec(a) ?: run { toast("Vct$a 未定义"); return@pickVector }
            pickVector("第二个向量") { b ->
                val va = vec(a)!!; val vb = vec(b)!!
                runCatching {
                    val res = when (mode) {
                        "dot" -> Modes_Res.dot(va, vb)
                        "cross" -> { resultDialog("叉积", "(${Fmt.num(Modes_Res.cross(va, vb).let { it[0] })}, ${Fmt.num(Modes_Res.cross(va, vb).let { it[1] })}, ${Fmt.num(Modes_Res.cross(va, vb).let { it[2] })})"); return@runCatching }
                        "add" -> { val c = doubleArrayOf(va[0]+vb[0], va[1]+vb[1], va[2]+vb[2]); resultDialog("和", "(${Fmt.num(c[0])}, ${Fmt.num(c[1])}, ${Fmt.num(c[2])})"); return@runCatching }
                        else -> 0.0
                    }
                    resultDialog("点积", Fmt.num(res))
                }.onFailure { toast("ERROR") }
            }
        }
    }
    private fun vectorAngle() {
        pickVector("第一个向量") { a ->
            vec(a) ?: run { toast("Vct$a 未定义"); return@pickVector }
            pickVector("第二个向量") { b ->
                val va = vec(a)!!; val vb = vec(b)!!
                val dot = va[0]*vb[0]+va[1]*vb[1]+va[2]*vb[2]
                val ma = kotlin.math.sqrt(va.sumOf { it*it })
                val mb = kotlin.math.sqrt(vb.sumOf { it*it })
                if (ma < 1e-12 || mb < 1e-12) { toast("向量为零"); return@pickVector }
                val cosT = (dot/(ma*mb)).coerceIn(-1.0,1.0)
                val deg = Math.toDegrees(kotlin.math.acos(cosT))
                resultDialog("夹角", "${Fmt.num(deg)}°")
            }
        }
    }
    private fun vectorUnary(label: String, op: (DoubleArray) -> Unit) {
        pickVector("选择向量") { a -> vec(a)?.let { op(it) } ?: toast("Vct$a 未定义") }
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
            state.tokens.add(NumTok(Complex.fromReal(c.value), java.lang.String.format("%.9g", c.value)))
            state.cursor = state.tokens.size
            clearResult(); invalidate()
        }
    }
}

private object Modes_Res {
    fun dot(a: DoubleArray, b: DoubleArray) = a[0]*b[0]+a[1]*b[1]+a[2]*b[2]
    fun cross(a: DoubleArray, b: DoubleArray): DoubleArray = doubleArrayOf(
        a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]
    )
}
