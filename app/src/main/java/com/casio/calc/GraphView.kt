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
        /** 10 种颜色，按位置分配（首两条为参考图的蓝/红） */
        val PALETTE = intArrayOf(
            Color.rgb(0x37, 0x74, 0xd6),   // 蓝
            Color.rgb(0xd2, 0x4b, 0x4b),   // 红
            Color.rgb(0x3f, 0xa4, 0x5b),   // 绿
            Color.rgb(0xe0, 0x8a, 0x2e),   // 橙
            Color.rgb(0x8e, 0x5b, 0xc8),   // 紫
            Color.rgb(0x2a, 0xa6, 0xa6),   // 青
            Color.rgb(0xc2, 0x46, 0x9e),   // 洋红
            Color.rgb(0x8a, 0x6a, 0x4f),   // 棕
            Color.rgb(0x9a, 0xa0, 0x2c),   // 橄榄
            Color.rgb(0x2d, 0x8f, 0xd5),   // 浅蓝
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
                    c == '²' -> { out.add(PostTok("sq", "²")); i++ }
                    c == '³' -> {
                        if (i + 1 < s.length && s[i + 1] == '√') { out.add(FnTok("cbrt", "³√")); i += 2 }
                        else { out.add(PostTok("cube", "³")); i++ }
                    }
                    c == '⁻' -> {
                        if (i + 1 < s.length && s[i + 1] == '¹') { out.add(PostTok("inv", "⁻¹")); i += 2 }
                        else i++
                    }
                    c == '%' -> { out.add(PostTok("percent", "%")); i++ }
                    c == '!' -> { out.add(PostTok("fact", "!")); i++ }
                    c == '√' -> { out.add(FnTok("sqrt", "√")); i++ }
                    c == '−' -> { out.add(OpTok("-", "−")); i++ }
                    c.isLetter() -> {
                        var w = ""
                        while (i < s.length && s[i].isLetter()) { w += s[i]; i++ }
                        val lw = w.lowercase()
                        // 函数名后接上标 ⁻¹ → 反函数（如 sin⁻¹）
                        if (i + 1 < s.length && s[i] == '⁻' && s[i + 1] == '¹' &&
                            lw in setOf("sin", "cos", "tan", "sinh", "cosh", "tanh")) {
                            i += 2
                            val inv = when (lw) {
                                "sin" -> "asin"; "cos" -> "acos"; "tan" -> "atan"
                                "sinh" -> "asinh"; "cosh" -> "acosh"; else -> "atanh"
                            }
                            out.add(FnTok(inv, w + "⁻¹"))
                        } else when (lw) {
                            "sin","cos","tan","asin","acos","atan","sinh","cosh","tanh",
                            "asinh","acosh","atanh",
                            "ln","log","exp","sqrt","cbrt","abs","arg","conjg","int","frac" -> {
                                out.add(FnTok(lw, w))
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
                            "ans" -> out.add(VarTok("Ans"))
                            "preans" -> out.add(VarTok("PreAns"))
                            "ncr" -> out.add(OpTok("C", "nCr"))
                            "npr" -> out.add(OpTok("P", "nPr"))
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

    // 白底 + 浅灰网格（参考卡西欧原生图形界面）
    private val COLOR_GRAPH_BG = Color.WHITE
    private val COLOR_GRID = Color.rgb(0xE4, 0xE4, 0xE4)
    private val COLOR_AXIS = Color.rgb(0x22, 0x22, 0x22)
    private val COLOR_TICK = Color.rgb(0x4A, 0x4A, 0x4A)
    private val COLOR_BOX = Color.rgb(0xD2, 0xD5, 0xD9)
    private val COLOR_SURFACE_LO = Color.rgb(0xC0, 0x3A, 0x3A)
    private val COLOR_SURFACE_HI = Color.rgb(0xEC, 0x77, 0x77)
    private val COLOR_MESH = Color.argb(0xB0, 0xFF, 0xFF, 0xFF)
    private val COLOR_AXIS3D = Color.rgb(0x8C, 0x8C, 0x8C)

    private val paintGrid = Paint().apply {
        color = COLOR_GRID; strokeWidth = 1f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val paintAxis = Paint().apply {
        color = COLOR_AXIS; strokeWidth = dp(1.4f); style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val paintCurve = Paint().apply {
        strokeWidth = dp(2.6f); style = Paint.Style.STROKE; isAntiAlias = true
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val paintSurface = Paint().apply {
        style = Paint.Style.FILL; isAntiAlias = true
    }
    private val paintMesh = Paint().apply {
        color = COLOR_MESH; strokeWidth = dp(1f); style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val paintBox = Paint().apply {
        color = COLOR_BOX; strokeWidth = dp(1f); style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val paintAxis3D = Paint().apply {
        color = COLOR_AXIS3D; strokeWidth = dp(1.2f); style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val paintBg = Paint().apply { color = COLOR_GRAPH_BG }
    private val paintLabel = Paint().apply {
        color = COLOR_TICK; textSize = dp(11f)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true
    }
    private val paintTextSmall = Paint().apply {
        color = COLOR_TICK; textSize = dp(9f); typeface = Typeface.SANS_SERIF; isAntiAlias = true
    }
    private val paintLegend = Paint().apply {
        color = COLOR_TICK; textSize = dp(9.5f); typeface = Typeface.SANS_SERIF; isAntiAlias = true
    }
    private val paintLegendSwatch = Paint().apply {
        style = Paint.Style.STROKE; isAntiAlias = true; strokeWidth = dp(2.2f)
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)
        if (mode2D) draw2D(canvas) else draw3D(canvas)
    }

    // ---- 2D ----

    private fun draw2D(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paintTextSmall.textSize = dp(9.5f)

        val xStep = niceStep(xMax - xMin)
        val yStep = niceStep(yMax - yMin)

        // 网格
        var gx = ceil(xMin / xStep) * xStep
        while (gx <= xMax + 1e-9) {
            val px = xToPx(gx, w)
            canvas.drawLine(px, 0f, px, h, paintGrid)
            gx += xStep
        }
        var gy = ceil(yMin / yStep) * yStep
        while (gy <= yMax + 1e-9) {
            val py = yToPx(gy, h)
            canvas.drawLine(0f, py, w, py, paintGrid)
            gy += yStep
        }

        // 坐标轴
        val ax = if (xMin <= 0 && xMax >= 0) xToPx(0.0, w) else null
        val ay = if (yMin <= 0 && yMax >= 0) yToPx(0.0, h) else null
        if (ax != null) canvas.drawLine(ax, 0f, ax, h, paintAxis)
        if (ay != null) canvas.drawLine(0f, ay, w, ay, paintAxis)

        // 刻度数字
        val baseY = ay ?: (h - dp(4f))
        gx = ceil(xMin / xStep) * xStep
        while (gx <= xMax + 1e-9) {
            if (abs(gx) > 1e-9) {
                val px = xToPx(gx, w)
                val ty = (baseY + dp(12f)).coerceAtMost(h - dp(2f))
                val tx = px.coerceIn(dp(1f), w - dp(24f))
                canvas.drawText(fmtTick(gx, xStep), tx, ty, paintTextSmall)
            }
            gx += xStep
        }
        val baseX = ax ?: dp(4f)
        gy = ceil(yMin / yStep) * yStep
        while (gy <= yMax + 1e-9) {
            if (abs(gy) > 1e-9) {
                val py = yToPx(gy, h)
                val tx = (baseX + dp(4f)).coerceAtMost(w - dp(22f))
                canvas.drawText(fmtTick(gy, yStep), tx, (py - dp(3f)).coerceIn(dp(8f), h - dp(2f)), paintTextSmall)
            }
            gy += yStep
        }
        if (ax != null && ay != null) {
            canvas.drawText("0", ax - dp(9f), ay + dp(12f), paintTextSmall)
        }

        // 曲线
        for (f in funcs) {
            if (!f.enabled) continue
            paintCurve.color = f.color
            drawCurve2D(canvas, f)
        }

        if (funcs.count { it.enabled } > 1) drawLegend(canvas, w, h)
    }

    private fun fmtTick(v: Double, step: Double): String {
        if (step >= 1.0) {
            val r = round(v)
            return if (abs(r) < 1e15) r.toLong().toString() else Fmt.num(v)
        }
        val dec = (-floor(log10(step))).toInt().coerceIn(1, 6)
        return String.format("%.${dec}f", v).trimEnd('0').trimEnd('.')
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
        paintLegend.textSize = dp(9.5f)
        var y = h - dp(6f)
        for (f in enabled.reversed()) {
            val label = if (f.expr.length > 14) f.expr.substring(0, 14) + "…" else f.expr
            val tw = paintLegend.measureText(label)
            paintLegendSwatch.color = f.color
            canvas.drawLine(w - tw - dp(26f), y - dp(3.5f), w - dp(16f), y - dp(3.5f), paintLegendSwatch)
            canvas.drawText(label, w - tw - dp(12f), y, paintLegend)
            y -= dp(14f)
        }
    }

    // ---- 3D ----

    private fun draw3D(canvas: Canvas) {
        val f = funcs.firstOrNull() ?: return
        val w = width.toFloat(); val h = height.toFloat()

        // 世界范围：用 [-R, R] 覆盖 X/Y；Z 从采样自动算
        val range = run {
            val xw = if (xMax.isFinite() && xMin.isFinite() && xMax > xMin) max(abs(xMax), abs(xMin)) else 5.0
            val yw = if (yMax.isFinite() && yMin.isFinite() && yMax > yMin) max(abs(yMax), abs(yMin)) else 5.0
            max(xw, yw).coerceAtLeast(3.0)
        }
        val steps = 44
        val step = range * 2.0 / steps

        // 采样 z
        val zs = Array(steps + 1) { i ->
            DoubleArray(steps + 1) { j ->
                val x = -range + i * step
                val y = -range + j * step
                val z = evalTokens(f.tokens, mapOf("X" to x, "Y" to y))
                if (z.isFinite()) z else Double.NaN
            }
        }
        var minZ = Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        for (row in zs) for (v in row) {
            if (v.isFinite()) { if (v < minZ) minZ = v; if (v > maxZ) maxZ = v }
        }
        if (!minZ.isFinite() || !maxZ.isFinite() || maxZ - minZ < 1e-9) {
            minZ = -1.0; maxZ = 1.0
        }
        val spanZ = (maxZ - minZ).coerceAtLeast(1e-6)

        // 等距投影基向量
        val az = Math.toRadians(azim); val el = Math.toRadians(elev)
        val cosA = cos(az); val sinA = sin(az); val cosE = cos(el); val sinE = sin(el)

        val ax = cosA; val ay = -sinA
        // z 视觉归一化：把 z 起伏缩放得与 xy 同量级，避免低振幅曲面看起来像斜平面
        val zK = ((2.0 * range) / spanZ).coerceIn(0.05, 40.0)
        val bx = sinA * sinE; val by = cosA * sinE; val bz = cosE * zK
        // 深度（z-sort 用，越大越靠近屏幕）
        val dx = -sinA * cosE; val dy = -cosA * cosE; val dz = sinE * zK

        fun projectWorld(x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
            val sx = ax * x + ay * y
            val sy = bx * x + by * y + bz * z
            val dep = dx * x + dy * y + dz * z
            return Triple(sx, sy, dep)
        }

        // 包围盒屏幕极值 → 缩放
        val corners = listOf(
            projectWorld(range, range, minZ), projectWorld(-range, range, minZ),
            projectWorld(range, -range, minZ), projectWorld(-range, -range, minZ),
            projectWorld(range, range, maxZ), projectWorld(-range, range, maxZ),
            projectWorld(range, -range, maxZ), projectWorld(-range, -range, maxZ),
        )
        var minSX = Double.MAX_VALUE; var maxSX = -Double.MAX_VALUE
        var minSY = Double.MAX_VALUE; var maxSY = -Double.MAX_VALUE
        for (c in corners) {
            if (c.first < minSX) minSX = c.first; if (c.first > maxSX) maxSX = c.first
            if (c.second < minSY) minSY = c.second; if (c.second > maxSY) maxSY = c.second
        }
        val padX = dp(26f).toDouble(); val padY = dp(22f).toDouble()
        val spanSx = (maxSX - minSX).coerceAtLeast(1.0)
        val spanSy = (maxSY - minSY).coerceAtLeast(1.0)
        val scaleXY = min((w - 2 * padX) / spanSx, (h - 2 * padY) / spanSy) * zoom3D
        val originSx = -minSX * scaleXY + padX + (w - 2 * padX - spanSx * scaleXY) / 2.0
        val originSy = maxSY * scaleXY + padY + (h - 2 * padY - spanSy * scaleXY) / 2.0

        fun project(x: Double, y: Double, z: Double): Triple<Float, Float, Float> {
            val sx = (ax * x + ay * y) * scaleXY + originSx
            val sy = -(bx * x + by * y + bz * z) * scaleXY + originSy
            val dep = dx * x + dy * y + dz * z
            return Triple(sx.toFloat(), sy.toFloat(), dep.toFloat())
        }

        // 1) 包围盒（浅灰细线）
        val c000 = project(-range, -range, minZ); val c100 = project(range, -range, minZ)
        val c010 = project(-range, range, minZ);  val c110 = project(range, range, minZ)
        val c001 = project(-range, -range, maxZ); val c101 = project(range, -range, maxZ)
        val c011 = project(-range, range, maxZ);  val c111 = project(range, range, maxZ)
        val edges = listOf(
            c000 to c100, c100 to c110, c110 to c010, c010 to c000,
            c001 to c101, c101 to c111, c111 to c011, c011 to c001,
            c000 to c001, c100 to c101, c010 to c011, c110 to c111
        )
        for ((p0, p1) in edges) canvas.drawLine(p0.first, p0.second, p1.first, p1.second, paintBox)

        // 2) 曲面三角形（按深度远→近排序）
        data class Tri(
            val p0: Triple<Float, Float, Float>, val p1: Triple<Float, Float, Float>,
            val p2: Triple<Float, Float, Float>, val depth: Float, val t: Float
        )
        val tris = ArrayList<Tri>()
        for (i in 0 until steps) {
            for (j in 0 until steps) {
                val z00 = zs[i][j]; val z10 = zs[i + 1][j]; val z01 = zs[i][j + 1]; val z11 = zs[i + 1][j + 1]
                if (!(z00.isFinite() && z10.isFinite() && z01.isFinite() && z11.isFinite())) continue
                val x0 = -range + i * step; val x1 = x0 + step
                val y0 = -range + j * step; val y1 = y0 + step
                val p00 = project(x0, y0, z00); val p10 = project(x1, y0, z10)
                val p01 = project(x0, y1, z01); val p11 = project(x1, y1, z11)
                val t0d = (p00.third + p10.third + p01.third) / 3f
                val t1d = (p11.third + p10.third + p01.third) / 3f
                val h0 = (((z00 + z10 + z01) / 3 - minZ) / spanZ).toFloat().coerceIn(0f, 1f)
                val h1 = (((z11 + z10 + z01) / 3 - minZ) / spanZ).toFloat().coerceIn(0f, 1f)
                tris.add(Tri(p00, p10, p01, t0d, h0))
                tris.add(Tri(p11, p10, p01, t1d, h1))
            }
        }
        tris.sortBy { -it.depth }  // 深度小的（远）先画

        val path = Path()
        for (t in tris) {
            paintSurface.color = blend(COLOR_SURFACE_LO, COLOR_SURFACE_HI, t.t)
            path.reset()
            path.moveTo(t.p0.first, t.p0.second)
            path.lineTo(t.p1.first, t.p1.second)
            path.lineTo(t.p2.first, t.p2.second)
            path.close()
            canvas.drawPath(path, paintSurface)
        }

        // 3) 曲面网格线（白色细线覆盖）
        val ms = 2
        for (i in 0..steps step ms) {
            val p = Path(); var started = false
            for (j in 0..steps) {
                val z = zs[i][j]
                if (!z.isFinite()) { started = false; continue }
                val pt = project(-range + i * step, -range + j * step, z)
                if (!started) { p.moveTo(pt.first, pt.second); started = true } else p.lineTo(pt.first, pt.second)
            }
            canvas.drawPath(p, paintMesh)
        }
        for (j in 0..steps step ms) {
            val p = Path(); var started = false
            for (i in 0..steps) {
                val z = zs[i][j]
                if (!z.isFinite()) { started = false; continue }
                val pt = project(-range + i * step, -range + j * step, z)
                if (!started) { p.moveTo(pt.first, pt.second); started = true } else p.lineTo(pt.first, pt.second)
            }
            canvas.drawPath(p, paintMesh)
        }

        // 4) 坐标轴 + 标注
        val O = project(0.0, 0.0, 0.0)
        val XA = project(range, 0.0, 0.0); val XB = project(-range, 0.0, 0.0)
        val YA = project(0.0, range, 0.0); val YB = project(0.0, -range, 0.0)
        val ZA = project(0.0, 0.0, maxZ); val ZB = project(0.0, 0.0, minZ)
        canvas.drawLine(XB.first, XB.second, XA.first, XA.second, paintAxis3D)
        canvas.drawLine(YB.first, YB.second, YA.first, YA.second, paintAxis3D)
        canvas.drawLine(ZB.first, ZB.second, ZA.first, ZA.second, paintAxis3D)
        arrowHead(canvas, O, XA, paintAxis3D)
        arrowHead(canvas, O, YA, paintAxis3D)
        arrowHead(canvas, O, ZA, paintAxis3D)

        paintLabel.textSize = dp(11f)
        canvas.drawText("X", XA.first + dp(3f), XA.second - dp(2f), paintLabel)
        canvas.drawText("Y", YA.first + dp(3f), YA.second - dp(2f), paintLabel)
        canvas.drawText("Z", ZA.first - dp(1f), ZA.second - dp(4f), paintLabel)

        // 轴端刻度值
        paintTextSmall.textSize = dp(8.5f)
        canvas.drawText(Fmt.num(range), XA.first + dp(3f), XA.second + dp(9f), paintTextSmall)
        canvas.drawText(Fmt.num(-range), XB.first - dp(14f), XB.second + dp(9f), paintTextSmall)
        canvas.drawText(Fmt.num(range), YA.first + dp(3f), YA.second + dp(9f), paintTextSmall)
        canvas.drawText(Fmt.num(-range), YB.first - dp(14f), YB.second + dp(9f), paintTextSmall)
        canvas.drawText(Fmt.num(maxZ), ZA.first + dp(3f), ZA.second + dp(3f), paintTextSmall)
        canvas.drawText(Fmt.num(minZ), ZB.first + dp(3f), ZB.second + dp(3f), paintTextSmall)
        canvas.drawText("0", O.first - dp(8f), O.second + dp(10f), paintTextSmall)
    }

    /** 画一个小箭头（只画两条短线，避免和网格线冲突） */
    private fun arrowHead(canvas: Canvas, from: Triple<Float, Float, Float>, to: Triple<Float, Float, Float>, p: Paint) {
        val vx = to.first - from.first; val vy = to.second - from.second
        val len = sqrt((vx * vx + vy * vy).toDouble()).toFloat()
        if (len < 1f) return
        val ux = vx / len; val uy = vy / len
        val s = dp(6f)
        val px = -uy; val py = ux
        canvas.drawLine(to.first, to.second, to.first - ux * s + px * s * 0.6f, to.second - uy * s + py * s * 0.6f, p)
        canvas.drawLine(to.first, to.second, to.first - ux * s - px * s * 0.6f, to.second - uy * s - py * s * 0.6f, p)
    }

    /** 颜色线性插值 */
    private fun blend(c0: Int, c1: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(c0) + (Color.red(c1) - Color.red(c0)) * k).toInt(),
            (Color.green(c0) + (Color.green(c1) - Color.green(c0)) * k).toInt(),
            (Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * k).toInt()
        )
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
        s.cfg.angle = AngleMode.RAD   // 函数图像按弧度求值，否则 sin 等被当作角度制压成近平面
        s.vars["X"] = Complex.fromReal(vars["X"] ?: 0.0)
        s.vars["Y"] = Complex.fromReal(vars["Y"] ?: 0.0)
        val ctx = EvalCtx(s.buildVars(), s.cfg)
        return try {
            val r = Parser(s.tokens.toList(), ctx).evaluate()
            if (r.isReal) r.re else r.absv()
        } catch (e: Exception) { Double.NaN }
    }
}
