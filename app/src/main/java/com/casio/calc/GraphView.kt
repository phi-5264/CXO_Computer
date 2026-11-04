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
    var zoom3D = 1.0   // 3D 缩放

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

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
            zoomBy(detector.scaleFactor.toDouble())
            return true
        }
    })

    /** 缩放：2D 缩放视口，3D 缩放任一视角大小 */
    fun zoomBy(factor: Double) {
        if (mode2D) {
            val cx = (xMin + xMax) / 2.0
            val cy = (yMin + yMax) / 2.0
            val hw = (xMax - xMin) / 2.0 / factor
            val hh = (yMax - yMin) / 2.0 / factor
            xMin = cx - hw; xMax = cx + hw
            yMin = cy - hh; yMax = cy + hh
        } else {
            zoom3D = (zoom3D * factor).coerceIn(0.35, 4.0)
        }
    }

    /** 重置视角 */
    fun resetView() {
        xMin = -10.0; xMax = 10.0; yMin = -6.0; yMax = 6.0
        azim = -35.0; elev = 30.0; zoom3D = 1.0
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (mode2D) {
                        val w = width.toFloat().coerceAtLeast(1f)
                        val h = height.toFloat().coerceAtLeast(1f)
                        xMin -= (xMax - xMin) * dx / w; xMax -= (xMax - xMin) * dx / w
                        yMin += (yMax - yMin) * dy / h; yMax += (yMax - yMin) * dy / h
                    } else {
                        azim += dx * 0.3
                        elev = (elev + dy * 0.3).coerceIn(-85.0, 85.0)
                    }
                    lastX = event.x; lastY = event.y
                }
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
        paintTextSmall.textSize = dp(8f)
        // 网格
        val xStep = niceStep(xMax - xMin)
        val yStep = niceStep(yMax - yMin)
        paintGrid.strokeWidth = 1f
        paintGrid.pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        var gx = ceil(xMin / xStep) * xStep
        while (gx <= xMax + 1e-9) {
            val px = xToPx(gx, w)
            canvas.drawLine(px, 0f, px, h, paintGrid)
            if (abs(gx) > 1e-9) canvas.drawText(Fmt.num(gx), px + 2f, yToPx(0.0, h) - 2f, paintTextSmall)
            gx += xStep
        }
        var gy = ceil(yMin / yStep) * yStep
        while (gy <= yMax + 1e-9) {
            val py = yToPx(gy, h)
            canvas.drawLine(0f, py, w, py, paintGrid)
            if (abs(gy) > 1e-9) canvas.drawText(Fmt.num(gy), xToPx(0.0, w) + 2f, py - 2f, paintTextSmall)
            gy += yStep
        }
        paintGrid.pathEffect = null

        // 坐标轴
        paintAxis.strokeWidth = dp(1.2f)
        if (xMin <= 0 && xMax >= 0) canvas.drawLine(xToPx(0.0, w), 0f, xToPx(0.0, w), h, paintAxis)
        if (yMin <= 0 && yMax >= 0) canvas.drawLine(0f, yToPx(0.0, h), w, yToPx(0.0, h), paintAxis)

        // 曲线
        drawCurve2D(canvas)

        // 标题
        paintLabel.textSize = dp(10f)
        var shown = expr2D
        while (paintLabel.measureText("y=$shown") > w - dp(16f) && shown.length > 4) {
            shown = shown.substring(0, shown.length - 1)
        }
        canvas.drawText("y=$shown", dp(6f), dp(14f), paintLabel)
    }

    private fun drawCurve2D(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val samples = min(600, max(200, w.toInt()))
        val step = (xMax - xMin) / samples
        val path = Path()
        var started = false
        var prevValid = false
        var prevY = 0.0
        var x = xMin
        while (x <= xMax) {
            val y = evalAt(expr2D, mapOf("X" to x, "Y" to 0.0))
            val valid = y.isFinite() && !y.isNaN()
            if (valid) {
                val px = xToPx(x, w); val py = yToPx(y, h)
                if (!started) { path.moveTo(px, py); started = true }
                else if (prevValid && abs(y - prevY) > (yMax - yMin) * 2) {
                    path.moveTo(px, py)
                } else {
                    path.lineTo(px, py)
                }
            } else {
                started = false
            }
            prevValid = valid; prevY = y
            x += step
        }
        canvas.drawPath(path, paintCurve)
    }

    private fun draw3D(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.58f
        val range = 5.0
        val steps = 26
        val step = range * 2 / steps

        // 采样曲面并归一化 z
        val zs = Array(steps + 1) { i ->
            DoubleArray(steps + 1) { j ->
                val x = -range + i * step
                val y = -range + j * step
                val z = evalAt(expr3D, mapOf("X" to x, "Y" to y))
                if (z.isFinite()) z else 0.0
            }
        }
        var minZ = Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE
        for (row in zs) for (v in row) { if (v < minZ) minZ = v; if (v > maxZ) maxZ = v }
        if (maxZ - minZ < 1e-9) { minZ -= 1.0; maxZ += 1.0 }
        val midZ = (minZ + maxZ) / 2.0
        val spanZ = maxZ - minZ

        val az = Math.toRadians(azim)
        val el = Math.toRadians(elev)
        val cosA = cos(az); val sinA = sin(az)
        val cosE = cos(el); val sinE = sin(el)

        val scaleXY = min(w / (range * 2.8f), h / (range * 1.9f)) * zoom3D.toFloat()
        val scaleZ = (h * 0.8f / spanZ) * zoom3D

        fun project(x: Double, y: Double, z: Double): Triple<Float, Float, Float> {
            val xr = x * cosA - y * sinA
            val yr = x * sinA + y * cosA
            val dz = z - midZ
            val px = cx + (xr * scaleXY).toFloat()
            val py = cy + (yr * scaleXY * sinE).toFloat() - (dz * cosE * scaleZ).toFloat()
            return Triple(px, py, yr.toFloat())
        }

        // 四边面片
        data class Quad(val p0: Triple<Float, Float, Float>, val p1: Triple<Float, Float, Float>,
                        val p2: Triple<Float, Float, Float>, val p3: Triple<Float, Float, Float>,
                        val depth: Float, val z: Double)

        val quads = ArrayList<Quad>(steps * steps)
        for (i in 0 until steps) {
            for (j in 0 until steps) {
                val z00 = zs[i][j]; val z10 = zs[i + 1][j]
                val z01 = zs[i][j + 1]; val z11 = zs[i + 1][j + 1]
                val avgZ = (z00 + z10 + z01 + z11) / 4
                val x0 = -range + i * step; val x1 = -range + (i + 1) * step
                val y0 = -range + j * step; val y1 = -range + (j + 1) * step
                val p0 = project(x0, y0, z00)
                val p1 = project(x1, y0, z10)
                val p2 = project(x1, y1, z11)
                val p3 = project(x0, y1, z01)
                val depth = (p0.third + p1.third + p2.third + p3.third) / 4f
                quads.add(Quad(p0, p1, p2, p3, depth, avgZ))
            }
        }
        quads.sortBy { it.depth }

        // 坐标轴
        paintAxis.strokeWidth = dp(1.2f)
        val origin = project(0.0, 0.0, 0.0)
        val ax = project(range, 0.0, 0.0)
        val ay = project(0.0, range, 0.0)
        val az2 = project(0.0, 0.0, maxZ)
        canvas.drawLine(origin.first, origin.second, ax.first, ax.second, paintAxis)
        canvas.drawLine(origin.first, origin.second, ay.first, ay.second, paintAxis)
        canvas.drawLine(origin.first, origin.second, az2.first, az2.second, paintAxis)

        // 曲面（按深度从远到近）
        val path = Path()
        for (q in quads) {
            val t = ((q.z - minZ) / spanZ).coerceIn(0.0, 1.0)
            val hue = (240.0 - t * 240.0).toFloat()   // 蓝 → 红
            paintSurface.color = Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.9f))
            path.reset()
            path.moveTo(q.p0.first, q.p0.second)
            path.lineTo(q.p1.first, q.p1.second)
            path.lineTo(q.p2.first, q.p2.second)
            path.lineTo(q.p3.first, q.p3.second)
            path.close()
            canvas.drawPath(path, paintSurface)
        }

        // 标题
        paintLabel.textSize = dp(10f)
        val title = expr3D
        var shown = title
        while (paintLabel.measureText(shown) > w - dp(16f) && shown.length > 4) {
            shown = shown.substring(0, shown.length - 1)
        }
        canvas.drawText("z=$shown", dp(6f), dp(14f), paintLabel)
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
