package com.casio.calc

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

/**
 * 函数图像视图：支持 2D f(x) 和 3D f(x,y)（等距投影）。
 * 用户输入表达式后，可以 pinch zoom / drag 平移 / tap 显示坐标。
 */
class GraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // 输入表达式
    var expr2D: String = "sin(X)"
    var expr3D: String = "sin(sqrt(X*X+Y*Y))/(sqrt(X*X+Y*Y)+0.001)"

    var mode2D = true  // true=2D  false=3D

    // 视口：2D 下 xRange = (xMin..xMax)，yRange = (yMin..yMax)
    var xMin = -10.0; var xMax = 10.0
    var yMin = -6.0;  var yMax = 6.0

    // 3D 视角
    var azim = -35.0   // 方位角
    var elev = 30.0    // 仰角

    private val paintGrid = Paint().apply {
        color = Color.rgb(0xcc, 0xcc, 0xcc)
        textSize = 0f; strokeWidth = 1f; style = Paint.Style.STROKE
    }
    private val paintAxis = Paint().apply {
        color = Color.rgb(0x33, 0x33, 0x33); strokeWidth = 2f
    }
    private val paintCurve = Paint().apply {
        color = Color.rgb(0xd8, 0x7b, 0x2d); strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val paintSurface = Paint().apply {
        style = Paint.Style.FILL; isAntiAlias = true
    }
    private val paintBg = Paint().apply { color = Color.rgb(0x2b, 0x2b, 0x30) }
    private val paintLabel = Paint().apply {
        color = Color.WHITE; textSize = 32f; typeface = Typeface.MONOSPACE
    }
    private val paintTextSmall = Paint().apply {
        color = Color.rgb(0x99, 0x99, 0x99); textSize = 24f; typeface = Typeface.MONOSPACE
    }

    private var lastX = 0f; private var lastY = 0f
    private var scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            val cx = (xMin + xMax) / 2.0
            val cy = (yMin + yMax) / 2.0
            val dx = (xMax - xMin) / 2.0 / factor
            val dy = (yMax - yMin) / 2.0 / factor
            xMin = cx - dx; xMax = cx + dx
            yMin = cy - dy; yMax = cy + dy
            invalidate()
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (scaleDetector.onTouchEvent(event)) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (mode2D) {
                    val w = width.toFloat()
                    val h = height.toFloat()
                    xMin -= (xMax - xMin) * dx / w; xMax -= (xMax - xMin) * dx / w
                    yMin += (yMax - yMin) * dy / h; yMax += (yMax - yMin) * dy / h
                } else {
                    azim += dx * 0.3
                    elev += dy * 0.3
                }
                lastX = event.x; lastY = event.y
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)
        if (mode2D) draw2D(canvas) else draw3D(canvas)
    }

    private fun draw2D(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // 网格
        val xStep = niceStep(xMax - xMin)
        val yStep = niceStep(yMax - yMin)
        paintGrid.strokeWidth = 1f
        paintGrid.pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        var gx = ceil(xMin / xStep) * xStep
        while (gx <= xMax + 1e-9) {
            val px = xToPx(gx, w)
            canvas.drawLine(px, 0f, px, h, paintGrid)
            canvas.drawText(Fmt.num(gx), px + 4f, yToPx(0.0, h) - 6f, paintTextSmall)
            gx += xStep
        }
        var gy = ceil(yMin / yStep) * yStep
        while (gy <= yMax + 1e-9) {
            val py = yToPx(gy, h)
            canvas.drawLine(0f, py, w, py, paintGrid)
            canvas.drawText(Fmt.num(gy), xToPx(0.0, w) + 4f, py - 4f, paintTextSmall)
            gy += yStep
        }
        paintGrid.pathEffect = null

        // 坐标轴
        paintAxis.strokeWidth = 2.5f
        if (xMin <= 0 && xMax >= 0) canvas.drawLine(xToPx(0.0, w), 0f, xToPx(0.0, w), h, paintAxis)
        if (yMin <= 0 && yMax >= 0) canvas.drawLine(0f, yToPx(0.0, h), w, yToPx(0.0, h), paintAxis)

        // 曲线
        drawCurve2D(canvas)

        // 标题
        paintLabel.textSize = 36f
        canvas.drawText("y = $expr2D", 16f, 48f, paintLabel)
    }

    private fun drawCurve2D(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val step = (xMax - xMin) / (w * 2)
        val path = Path()
        var started = false
        var prevValid = false
        var prevY = 0.0
        for (x in generateSequence(xMin) { it + step }.takeWhile { it <= xMax }) {
            val y = evalAt(expr2D, mapOf("X" to x, "Y" to 0.0))
            val valid = y.isFinite() && !y.isNaN()
            if (valid) {
                val px = xToPx(x, w); val py = yToPx(y, h)
                if (!started) { path.moveTo(px, py); started = true }
                else {
                    // 检测不连续（跳变超过范围）
                    if (prevValid && abs(y - prevY) > (yMax - yMin) * 2) {
                        path.moveTo(px, py)
                    } else {
                        path.lineTo(px, py)
                    }
                }
            } else {
                started = false
            }
            prevValid = valid; prevY = y
        }
        canvas.drawPath(path, paintCurve)
    }

    private fun draw3D(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        // 网格大小
        val range = 5.0
        val steps = 40
        val step = range * 2 / steps

        // 计算网格点
        val grid = Array(steps + 1) { i ->
            Array(steps + 1) { j ->
                val x = -range + i * step
                val y = -range + j * step
                val z = evalAt(expr3D, mapOf("X" to x, "Y" to y))
                Triple(x, y, if (z.isFinite() && !z.isNaN()) z else 0.0)
            }
        }

        // 旋转 + 投影
        val az = Math.toRadians(azim)
        val el = Math.toRadians(elev)
        val cosA = cos(az); val sinA = sin(az)
        val cosE = cos(el); val sinE = sin(el)

        val scale = min(w, h) / (range * 5)

        data class ProjPt(val px: Float, val py: Float, val z: Float)
        fun project(pt: Triple<Double, Double, Double>): ProjPt {
            val (x, y, z) = pt
            // 先绕 y 轴（方位）
            val x1 = x * cosA - y * sinA
            val y1 = x * sinA + y * cosA
            val z1 = z
            // 再绕 x 轴（仰角）
            val x2 = x1
            val y2 = y1 * cosE - z1 * sinE
            val z2 = y1 * sinE + z1 * cosE
            return ProjPt((cx + x2 * scale).toFloat(), (cy - y2 * scale).toFloat(), z2.toFloat())
        }

        // 绘制深度排序（从后到前）
        // 每个方格 = 4 个点，深度取 avg
        data class Quad(val p0: ProjPt, val p1: ProjPt, val p2: ProjPt, val p3: ProjPt, val depth: Float, val z: Double)

        val quads = mutableListOf<Quad>()
        for (i in 0 until steps) {
            for (j in 0 until steps) {
                val z00 = grid[i][j].third
                val z10 = grid[i + 1][j].third
                val z01 = grid[i][j + 1].third
                val z11 = grid[i + 1][j + 1].third
                val avgZ = (z00 + z10 + z01 + z11) / 4
                val p0 = project(grid[i][j])
                val p1 = project(grid[i + 1][j])
                val p2 = project(grid[i + 1][j + 1])
                val p3 = project(grid[i][j + 1])
                val depth = (p0.z + p1.z + p2.z + p3.z) / 4f
                quads.add(Quad(p0, p1, p2, p3, depth, avgZ))
            }
        }
        quads.sortBy { it.depth }

        // 画坐标轴（在中心）
        paintAxis.strokeWidth = 3f
        val origin = project(Triple(0.0, 0.0, 0.0))
        val ex = project(Triple(range, 0.0, 0.0))
        val ey = project(Triple(0.0, range, 0.0))
        val ez = project(Triple(0.0, 0.0, range))
        canvas.drawLine(origin.px, origin.py, ex.px, ex.py, paintAxis)
        canvas.drawLine(origin.px, origin.py, ey.px, ey.py, paintAxis)
        canvas.drawLine(origin.px, origin.py, ez.px, ez.py, paintAxis)

        // 画曲面
        val path = Path()
        for (q in quads) {
            val maxZ = grid.maxOf { row -> row.maxOf { it.third } }
            val minZ = grid.minOf { row -> row.minOf { it.third } }
            val t = if (maxZ > minZ) ((q.z - minZ) / (maxZ - minZ)).coerceIn(0.0, 1.0) else 0.5
            val hue = (240 - t * 240).toInt()  // 蓝(240) → 红(0)
            val color = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.7f, 0.85f))
            paintSurface.color = color
            path.reset()
            path.moveTo(q.p0.px, q.p0.py)
            path.lineTo(q.p1.px, q.p1.py)
            path.lineTo(q.p2.px, q.p2.py)
            path.lineTo(q.p3.px, q.p3.py)
            path.close()
            canvas.drawPath(path, paintSurface)
        }

        // 标题
        paintLabel.textSize = 36f
        canvas.drawText("z = $expr3D", 16f, 48f, paintLabel)
    }

    private fun xToPx(x: Double, w: Float): Float =
        ((x - xMin) / (xMax - xMin) * w).toFloat()
    private fun yToPx(y: Double, h: Float): Float =
        h - ((y - yMin) / (yMax - yMin) * h).toFloat()

    private fun niceStep(range: Double): Double {
        val raw = range / 8
        val mag = 10.0.pow(floor(log10(abs(raw))))
        val n = raw / mag
        val nice = when { n < 1.5 -> 1.0; n < 3.5 -> 2.0; n < 7.5 -> 5.0; else -> 10.0 }
        return nice * mag
    }

    /**
     * 简单表达式求值：支持 X, Y, + - * / ^ sin cos tan ln log sqrt abs pi e
     */
    fun evalAt(expr: String, vars: Map<String, Double>): Double {
        val e = expr.trim()
        val tokens = tokenize(e)
        val state = CalcState()
        val toks = tokens.map { tok ->
            when (tok) {
                is NumTok -> tok
                is OpTok -> tok
                is FnTok -> tok
                is LpTok -> tok
                is RpTok -> tok
                is VarTok -> tok
                is ConstTok -> tok
                is ImagTok -> tok
                is PostTok -> tok
            }
        }
        state.tokens.clear()
        state.tokens.addAll(toks.filterNotNull())
        state.vars["X"] = vars["X"]?.let { Complex.fromReal(it) } ?: Complex.ZERO
        state.vars["Y"] = vars["Y"]?.let { Complex.fromReal(it) } ?: Complex.ZERO
        val ctx = EvalCtx(state.buildVars(), state.cfg)
        val r = Parser(state.tokens.toList(), ctx).evaluate()
        return if (r.isReal) r.re else r.absv()
    }

    private fun tokenize(s: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() || c == '.' -> {
                    var n = ""
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) { n += s[i]; i++ }
                    out.add(NumTok(Complex.fromReal(n.toDouble()), n))
                }
                c == '+' -> { out.add(OpTok("+", "+")); i++ }
                c == '-' -> { out.add(OpTok("-", "-")); i++ }
                c == '*' || c == '×' -> { out.add(OpTok("×", "×")); i++ }
                c == '/' || c == '÷' -> { out.add(OpTok("÷", "÷")); i++ }
                c == '^' -> { out.add(OpTok("^", "^")); i++ }
                c == '(' -> { out.add(LpTok()); i++ }
                c == ')' -> { out.add(RpTok()); i++ }
                c == ',' -> i++
                c.isLetter() -> {
                    var w = ""
                    while (i < s.length && s[i].isLetter()) { w += s[i]; i++ }
                    when (w.lowercase()) {
                        "sin","cos","tan","asin","acos","atan","sinh","cosh","tanh",
                        "ln","log","exp","sqrt","cbrt","abs" -> {
                            out.add(FnTok(w.lowercase(), w))
                        }
                        "pi","π" -> out.add(ConstTok("pi"))
                        "e" -> out.add(ConstTok("e"))
                        "x" -> out.add(VarTok("X"))
                        "y" -> out.add(VarTok("Y"))
                        else -> out.add(VarTok(w.uppercase()))
                    }
                }
                c == ' ' -> i++
                else -> i++
            }
        }
        return out
    }
}
