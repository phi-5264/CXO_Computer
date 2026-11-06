package com.casio.calc

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

/** 单个函数条目 */
data class FuncItem(
    var expr: String,        // 原始表达式（含 X 或 X,Y）
    var enabled: Boolean = true,
    val color: Int,          // 绘制颜色
) {
    val tokens: List<Tok> by lazy(LazyThreadSafetyMode.NONE) { GraphView.tokenize(expr) }
}

/** 函数图像视图：支持 2D f(x) 多函数、3D f(x,y) 单函数（等距投影）。 */
class GraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        /** 10 种高对比度颜色，按位置分配 */
        val PALETTE = intArrayOf(
            Color.rgb(0xd8, 0x7b, 0x2d),   // 橙
            Color.rgb(0x42, 0xa5, 0xf5),   // 蓝
            Color.rgb(0x66, 0xbb, 0x6a),   // 绿
            Color.rgb(0xe5, 0x39, 0x35),   // 红
            Color.rgb(0xab, 0x47, 0xbc),   // 紫
            Color.rgb(0xff, 0xeb, 0x3b),   // 黄
            Color.rgb(0x00, 0x89, 0x7b),   // 青
            Color.rgb(0xff, 0x6e, 0x40),   // 深橙
            Color.rgb(0x9c, 0x27, 0xb0),   // 深紫
            Color.rgb(0x26, 0xc6, 0xda),   // 浅青
        )

        /** 字符串 → token 列表 */
        fun tokenize(s: String): List<Tok> {
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
                    c == '∠' -> { out.add(com.casio.calc.AngleTok()); i++ }
                    c == '(' -> { out.add(LpTok()); i++ }
                    c == ')' -> { out.add(RpTok()); i++ }
                    c == ',' -> i++
                    c.isLetter() -> {
                        var w = ""
                        while (i < s.length && s[i].isLetter()) { w += s[i]; i++ }
                        when (w.lowercase()) {
                            "sin","cos","tan","asin","acos","atan","sinh","cosh","tanh",
                            "asinh","acosh","atanh",
                            "ln","log","exp","sqrt","cbrt","abs","int","frac" -> {
                                out.add(FnTok(w.lowercase(), w))
                            }
                            "arcsin" -> out.add(FnTok("asin", "arcsin"))
                            "arccos" -> out.add(FnTok("acos", "arccos"))
                            "arctan" -> out.add(FnTok("atan", "arctan"))
                            "arsinh" -> out.add(FnTok("asinh", "arsinh"))
                            "arcosh" -> out.add(FnTok("acosh", "arcosh"))
                            "artanh" -> out.add(FnTok("atanh", "artanh"))
                            "pi","π" -> out.add(ConstTok("pi"))
                            "e" -> out.add(ConstTok("e"))
                            "x" -> out.add(VarTok("X"))
                            "y" -> out.add(VarTok("Y"))
                            "i" -> out.add(ImagTok())
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

    // ========== 外部 API（CalculatorView 调用） ==========

    /** 函数列表（2D 模式下最多 10 条，3D 只用第一条） */
    val funcs = mutableListOf<FuncItem>()

    /** 当前活动（2D=true，3D=false） */
    var mode2D = true

    /** 3D 模式下的表达式（与 funcs[0].expr 同步） */
    var expr3D: String = "sin(sqrt(X*X+Y*Y))/(sqrt(X*X+Y*Y)+0.001)"

    // ========== 视口 ==========
    var xMin = -10.0; var xMax = 10.0
    var yMin = -6.0;  var yMax = 6.0
    var zMin: Double = Double.NaN
    var zMax: Double = Double.NaN

    var azim = -35.0
    var elev = 30.0
    var zoom3D = 1.0

    // ========== 触摸追踪 ==========
    private var lastX = 0f; private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomBy(detector.scaleFactor.toDouble()); return true
        }
    })

    /** 缩放 */
    fun zoomBy(factor: Double) {
        if (mode2D) {
            val cx = (xMin + xMax) / 2.0; val cy = (yMin + yMax) / 2.0
            val hw = (xMax - xMin) / 2.0 / factor; val hh = (yMax - yMin) / 2.0 / factor
            xMin = cx - hw; xMax = cx + hw
            yMin = cy - hh; yMax = cy + hh
        } else {
            zoom3D = (zoom3D * factor).coerceIn(0.35, 6.0)
        }
    }

    /** 重置视角 */
    fun resetView() {
        xMin = -10.0; xMax = 10.0; yMin = -6.0; yMax = 6.0
        zMin = Double.NaN; zMax = Double.NaN
        azim = -35.0; elev = 30.0; zoom3D = 1.0
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX; val dy = event.y - lastY
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
                    invalidate()
                }
            }
        }
        return true
    }

    // ========== 绘图 ==========

    private val density get() = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val paintGrid = Paint().apply {
        color = Color.argb(0x66, 0xcc, 0xcc, 0xcc)
        strokeWidth = 1f; style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    private val paintAxis = Paint().apply {
        color = Color.argb(0xcc, 0x33, 0x33, 0x33); strokeWidth = 2f
    }
    private val paintCurve = Paint().apply {
        strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = true
        pathEffect = CornerPathEffect(4f)
    }
    private val paintSurface = Paint().apply {
        style = Paint.Style.FILL; isAntiAlias = true
    }
    private val paintBg = Paint().apply { color = Color.rgb(0x1a, 0x1a, 0x1e) }
    private val paintLabel = Paint().apply {
        color = Color.WHITE; textSize = 32f; typeface = Typeface.MONOSPACE
    }
    private val paintTextSmall = Paint().apply {
        color = Color.argb(0xcc, 0xcc, 0xcc, 0xcc); textSize = 24f; typeface = Typeface.MONOSPACE
    }
    private val paintLegend = Paint().apply {
        color = Color.WHITE; textSize = 26f; typeface = Typeface.DEFAULT
    }
    private val paintLegendSwatch = Paint().apply {
        style = Paint.Style.FILL; isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)
        if (mode2D) draw2D(canvas) else draw3D(canvas)
    }

    // ---- 2D ----

    private fun draw2D(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paintTextSmall.textSize = dp(9f)

        // 网格
        val xStep = niceStep(xMax - xMin)
        val yStep = niceStep(yMax - yMin)
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

        // 坐标轴
        paintAxis.strokeWidth = dp(1.2f)
        paintAxis.pathEffect = null
        if (xMin <= 0 && xMax >= 0) canvas.drawLine(xToPx(0.0, w), 0f, xToPx(0.0, w), h, paintAxis)
        if (yMin <= 0 && yMax >= 0) canvas.drawLine(0f, yToPx(0.0, h), w, yToPx(0.0, h), paintAxis)

        // 每条曲线
        for ((idx, f) in funcs.withIndex()) {
            if (!f.enabled) continue
            paintCurve.color = f.color
            drawCurve2D(canvas, f)
        }

        // 顶部标题（首函数）
        paintLabel.textSize = dp(11f)
        if (funcs.isNotEmpty()) {
            var shown = funcs.first().expr
            while (paintLabel.measureText("y=$shown") > w - dp(16f) && shown.length > 4) {
                shown = shown.substring(0, shown.length - 1)
            }
            canvas.drawText("y=$shown", dp(6f), dp(14f), paintLabel)
        }

        // 图例（多函数时显示在右下）
        if (funcs.count { it.enabled } > 1) drawLegend(canvas, w, h)
    }

    private fun drawCurve2D(canvas: Canvas, f: FuncItem) {
        val w = width.toFloat(); val h = height.toFloat()
        val samples = max(800, width * 2).toInt()
        val step = (xMax - xMin) / samples
        val path = Path()
        var started = false
        var prevValid = false
        var prevY = 0.0
        var x = xMin
        while (x <= xMax) {
            val y = evalTokens(f.tokens, mapOf("X" to x, "Y" to 0.0))
            val valid = y.isFinite() && !y.isNaN()
            if (valid) {
                val px = xToPx(x, w); val py = yToPx(y, h)
                // 越界但接近可视 → 裁剪后连线，避免跨越巨大值时误连
                val clippedPy = py.coerceIn(-dp(20f), h + dp(20f))
                if (!started) { path.moveTo(px, clippedPy); started = true }
                else if (prevValid && abs(y - prevY) > (yMax - yMin) * 3) {
                    path.moveTo(px, clippedPy)
                } else {
                    path.lineTo(px, clippedPy)
                }
            } else {
                started = false
            }
            prevValid = valid; prevY = y
            x += step
        }
        canvas.drawPath(path, paintCurve)
    }

    private fun drawLegend(canvas: Canvas, w: Float, h: Float) {
        val enabled = funcs.filter { it.enabled }
        var y = h - dp(8f)
        paintLegend.textSize = dp(10f)
        paintLegendSwatch.style = Paint.Style.FILL
        for (f in enabled.reversed()) {
            val label = if (f.expr.length > 16) f.expr.substring(0, 16) + "…" else f.expr
            val tw = paintLegend.measureText(label)
            paintLegendSwatch.color = f.color
            canvas.drawRoundRect(w - tw - dp(32f), y - dp(10f), w - dp(26f), y + dp(4f),
                dp(3f), dp(3f), paintLegendSwatch)
            canvas.drawText(label, w - tw - dp(22f), y, paintLegend)
            y -= dp(18f)
        }
    }

    // ---- 3D ----

    private fun draw3D(canvas: Canvas) {
        val f = funcs.firstOrNull() ?: return
        val w = width.toFloat(); val h = height.toFloat()
        android.util.Log.d("Graph3D", "draw3D w=$w h=$h expr='${f.expr}'")

        // 世界范围：用 [-R, R] 覆盖 X/Y；Z 从采样自动算
        val range = run {
            val xw = if (xMax.isFinite() && xMin.isFinite() && xMax > xMin) max(abs(xMax), abs(xMin)) else 5.0
            val yw = if (yMax.isFinite() && yMin.isFinite() && yMax > yMin) max(abs(yMax), abs(yMin)) else 5.0
            max(xw, yw).coerceAtLeast(3.0)
        }
        val steps = 45
        val step = range * 2.0 / steps

        // 先采样所有 z 值，得到 z 范围
        val zs = Array(steps + 1) { i ->
            DoubleArray(steps + 1) { j ->
                val x = -range + i * step
                val y = -range + j * step
                val z = evalTokens(f.tokens, mapOf("X" to x, "Y" to y))
                if (z.isFinite()) z else Double.NaN
            }
        }
        var minZ = Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        var finiteCount = 0
        for (row in zs) for (v in row) {
            if (v.isFinite()) { finiteCount++; if (v < minZ) minZ = v; if (v > maxZ) maxZ = v }
        }
        if (!minZ.isFinite() || !maxZ.isFinite() || maxZ - minZ < 1e-9) {
            minZ = -1.0; maxZ = 1.0
        }
        // 打印几个采样点，诊断 evalTokens 是否正常
        val samples = listOf(
            -range to -range, 0.0 to 0.0, range to range,
            -range to 0.0, 0.0 to range
        )
        val sb = StringBuilder()
        for ((x, y) in samples) {
            val z = evalTokens(f.tokens, mapOf("X" to x, "Y" to y))
            sb.append("(${String.format("%.2f", x)},${String.format("%.2f", y)})=z=${String.format("%.3f", z)}  ")
        }
        android.util.Log.d("Graph3D", "range=$range steps=$steps finite=$finiteCount minZ=${String.format("%.4f", minZ)} maxZ=${String.format("%.4f", maxZ)} samples=$sb")

        // 等距投影基向量
        val az = Math.toRadians(azim); val el = Math.toRadians(elev)
        val cosA = cos(az); val sinA = sin(az); val cosE = cos(el); val sinE = sin(el)

        // 屏幕 x 轴（世界中 x-y 平面旋转 az 后水平向右）
        val ax = cosA; val ay = -sinA
        // 屏幕 y 轴（el 倾斜后 z 分量参与）
        val bx = sinA * sinE; val by = cosA * sinE; val bz = cosE
        // 深度（z-sort 用，越大越靠近屏幕）
        val dx = -sinA * cosE; val dy = -cosA * cosE; val dz = sinE

        // 计算 scale：让整个 [±range, ±range, minZ..maxZ] 包围盒完整落进画布
        fun projectWorld(x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
            val sx = ax * x + ay * y
            val sy = bx * x + by * y + bz * z
            val dp = dx * x + dy * y + dz * z
            return Triple(sx, sy, dp)
        }

        // 找到包围盒的屏幕 x/y 极值
        val corners = listOf(
            projectWorld( range,  range, minZ), projectWorld(-range,  range, minZ),
            projectWorld( range, -range, minZ), projectWorld(-range, -range, minZ),
            projectWorld( range,  range, maxZ), projectWorld(-range,  range, maxZ),
            projectWorld( range, -range, maxZ), projectWorld(-range, -range, maxZ),
        )
        var minSX = Double.MAX_VALUE; var maxSX = -Double.MAX_VALUE
        var minSY = Double.MAX_VALUE; var maxSY = -Double.MAX_VALUE
        for (c in corners) {
            if (c.first < minSX) minSX = c.first; if (c.first > maxSX) maxSX = c.first
            if (c.second < minSY) minSY = c.second; if (c.second > maxSY) maxSY = c.second
        }
        val pad = dp(20f).toDouble()
        val spanSx = (maxSX - minSX).coerceAtLeast(1.0)
        val spanSy = (maxSY - minSY).coerceAtLeast(1.0)
        val scaleXY = min((w - 2 * pad) / spanSx, (h - 2 * pad) / spanSy) * zoom3D.toDouble()
        val originSx = -minSX * scaleXY + pad + (w - 2 * pad - spanSx * scaleXY) / 2.0
        val originSy = -minSY * scaleXY + pad + (h - 2 * pad - spanSy * scaleXY) / 2.0

        fun projectFinal(x: Double, y: Double, z: Double): Triple<Float, Float, Float> {
            val sx = (ax * x + ay * y) * scaleXY + originSx
            val sy = -(bx * x + by * y + bz * z) * scaleXY + originSy  // screen y 反向下为正
            val dp = (dx * x + dy * y + dz * z)
            return Triple(sx.toFloat(), sy.toFloat(), dp.toFloat())
        }

        // 坐标轴
        paintAxis.strokeWidth = dp(1.5f); paintAxis.pathEffect = null; paintAxis.color = Color.argb(0xaa, 0xbb, 0xbb, 0xbb)
        // 原点
        val O = projectFinal(0.0, 0.0, 0.0)
        // X 轴（画到 range 处）
        val XA = projectFinal(range, 0.0, 0.0)
        val XB = projectFinal(-range, 0.0, 0.0)
        // Y 轴
        val YA = projectFinal(0.0, range, 0.0)
        val YB = projectFinal(0.0, -range, 0.0)
        // Z 轴（从 minZ 到 maxZ）
        val ZA = projectFinal(0.0, 0.0, maxZ)
        val ZB = projectFinal(0.0, 0.0, minZ)
        canvas.drawLine(XB.first, XB.second, XA.first, XA.second, paintAxis)
        canvas.drawLine(YB.first, YB.second, YA.first, YA.second, paintAxis)
        canvas.drawLine(ZB.first, ZB.second, ZA.first, ZA.second, paintAxis)
        // 轴标签
        paintTextSmall.textSize = dp(9f); paintTextSmall.color = Color.argb(0xcc, 0xff, 0xff, 0xff)
        canvas.drawText("X", XA.first + dp(2f), XA.second, paintTextSmall)
        canvas.drawText("Y", YA.first + dp(2f), YA.second, paintTextSmall)
        canvas.drawText("Z", ZA.first + dp(2f), ZA.second, paintTextSmall)

        // 曲面三角形（按深度排序，远→近）
        data class Tri(val p0: Triple<Float, Float, Float>, val p1: Triple<Float, Float, Float>,
                       val p2: Triple<Float, Float, Float>, val depth: Float, val z: Double)
        val tris = ArrayList<Tri>()
        for (i in 0 until steps) {
            for (j in 0 until steps) {
                val z00 = zs[i][j]; val z10 = zs[i + 1][j]; val z01 = zs[i][j + 1]; val z11 = zs[i + 1][j + 1]
                val allFinite = z00.isFinite() && z10.isFinite() && z01.isFinite() && z11.isFinite()
                if (!allFinite) continue
                val x0 = -range + i * step; val x1 = x0 + step
                val y0 = -range + j * step; val y1 = y0 + step
                val p00 = projectFinal(x0, y0, z00); val p10 = projectFinal(x1, y0, z10)
                val p01 = projectFinal(x0, y1, z01); val p11 = projectFinal(x1, y1, z11)
                // 两个三角形
                val t0d = (p00.third + p10.third + p01.third) / 3f
                val t1d = (p11.third + p10.third + p01.third) / 3f
                val avgZ0 = (z00 + z10 + z01) / 3
                val avgZ1 = (z11 + z10 + z01) / 3
                tris.add(Tri(p00, p10, p01, t0d, avgZ0))
                tris.add(Tri(p11, p10, p01, t1d, avgZ1))
            }
        }
        tris.sortBy { -it.depth }  // 深度小的（远）先画

        val path = Path()
        val spanZ = (maxZ - minZ).coerceAtLeast(1e-6)
        for (t in tris) {
            val hue = ((t.z - minZ) / spanZ * 240.0).toFloat()  // 蓝→红
            paintSurface.color = Color.HSVToColor(floatArrayOf(hue, 0.78f, 0.92f))
            path.reset()
            path.moveTo(t.p0.first, t.p0.second)
            path.lineTo(t.p1.first, t.p1.second)
            path.lineTo(t.p2.first, t.p2.second)
            path.close()
            canvas.drawPath(path, paintSurface)
        }

        // 标题
        paintLabel.textSize = dp(11f)
        var shown = f.expr
        while (paintLabel.measureText("z=$shown") > w - dp(16f) && shown.length > 4) {
            shown = shown.substring(0, shown.length - 1)
        }
        canvas.drawText("z=$shown", dp(6f), dp(14f), paintLabel)
    }

    // ---- 工具 ----

    private fun xToPx(x: Double, w: Float): Float =
        ((x - xMin) / (xMax - xMin) * w).toFloat()
    private fun yToPx(y: Double, h: Float): Float =
        h - ((y - yMin) / (yMax - yMin) * h).toFloat()

    private fun niceStep(range: Double): Double {
        val raw = range / 8
        val mag = 10.0.pow(floor(log10(abs(raw.coerceAtLeast(1e-12)))))
        val n = raw / mag
        val nice = when { n < 1.5 -> 1.0; n < 3.5 -> 2.0; n < 7.5 -> 5.0; else -> 10.0 }
        return nice * mag
    }

    private fun evalTokens(tokens: List<Tok>, vars: Map<String, Double>): Double {
        if (tokens.isEmpty()) return Double.NaN
        val s = CalcState(); s.tokens.addAll(tokens)
        s.vars["X"] = Complex.fromReal(vars["X"] ?: 0.0)
        s.vars["Y"] = Complex.fromReal(vars["Y"] ?: 0.0)
        val ctx = EvalCtx(s.buildVars(), s.cfg)
        return try {
            val r = Parser(s.tokens.toList(), ctx).evaluate()
            if (r.isReal) r.re else r.absv()
        } catch (e: Exception) { Double.NaN }
    }
}
