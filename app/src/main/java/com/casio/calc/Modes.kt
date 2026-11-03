package com.casio.calc

import kotlin.math.*

/** 矩阵 */
class Matrix(val rows: Int, val cols: Int, val data: Array<DoubleArray>) {
    operator fun plus(o: Matrix): Matrix {
        check(rows == o.rows && cols == o.cols) { "Dimension ERROR" }
        val r = Array(rows) { DoubleArray(cols) }
        for (i in 0 until rows) for (j in 0 until cols) r[i][j] = data[i][j] + o.data[i][j]
        return Matrix(rows, cols, r)
    }

    operator fun minus(o: Matrix): Matrix {
        check(rows == o.rows && cols == o.cols) { "Dimension ERROR" }
        val r = Array(rows) { DoubleArray(cols) }
        for (i in 0 until rows) for (j in 0 until cols) r[i][j] = data[i][j] - o.data[i][j]
        return Matrix(rows, cols, r)
    }

    operator fun times(o: Matrix): Matrix {
        check(cols == o.rows) { "Dimension ERROR" }
        val r = Array(rows) { DoubleArray(o.cols) }
        for (i in 0 until rows) for (j in 0 until o.cols) for (k in 0 until cols)
            r[i][j] += data[i][k] * o.data[k][j]
        return Matrix(rows, o.cols, r)
    }

    fun transpose(): Matrix {
        val r = Array(cols) { DoubleArray(rows) }
        for (i in 0 until rows) for (j in 0 until cols) r[j][i] = data[i][j]
        return Matrix(cols, rows, r)
    }

    fun det(): Double {
        check(rows == cols) { "Dimension ERROR" }
        val n = rows
        if (n == 1) return data[0][0]
        if (n == 2) return data[0][0] * data[1][1] - data[0][1] * data[1][0]
        var d = 0.0
        for (j in 0 until n) {
            val sub = Array(n - 1) { DoubleArray(n - 1) }
            for (i in 1 until n) {
                var cc = 0
                for (jj in 0 until n) if (jj != j) sub[i - 1][cc++] = data[i][jj]
            }
            d += (if (j % 2 == 0) 1 else -1) * data[0][j] * Matrix(n - 1, n - 1, sub).det()
        }
        return d
    }

    fun inverse(): Matrix {
        val n = rows
        check(n == cols) { "Dimension ERROR" }
        val d = det()
        if (abs(d) < 1e-12) throw IllegalArgumentException("Math ERROR")
        if (n == 1) return Matrix(1, 1, arrayOf(doubleArrayOf(1.0 / d)))
        // 伴随矩阵法
        val adj = Array(n) { DoubleArray(n) }
        for (i in 0 until n) for (j in 0 until n) {
            val sub = Array(n - 1) { DoubleArray(n - 1) }
            for (ii in 0 until n) if (ii != i)
                for (jj in 0 until n) if (jj != j)
                    sub[if (ii < i) ii else ii - 1][if (jj < j) jj else jj - 1] = data[ii][jj]
            adj[j][i] = (if ((i + j) % 2 == 0) 1 else -1) * Matrix(n - 1, n - 1, sub).det()
        }
        for (i in 0 until n) for (j in 0 until n) adj[i][j] /= d
        return Matrix(n, n, adj)
    }

    fun toStringFmt(): String {
        return (0 until rows).joinToString("\n") { i ->
            (0 until cols).joinToString("  ") { j -> Fmt.num(data[i][j]) }
        }
    }

    companion object {
        fun identity(n: Int): Matrix {
            val r = Array(n) { DoubleArray(n) }
            for (i in 0 until n) r[i][i] = 1.0
            return Matrix(n, n, r)
        }
    }
}

/** 方程与科学常量等模式逻辑 */
object Modes {

    // ---------- 线性方程组（高斯消元）----------
    fun solveLinear(n: Int, a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        // 增广矩阵
        val m = Array(n) { DoubleArray(n + 1) }
        for (i in 0 until n) {
            for (j in 0 until n) m[i][j] = a[i][j]
            m[i][n] = b[i]
        }
        for (col in 0 until n) {
            // 部分主元
            var piv = col
            for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[piv][col])) piv = r
            if (abs(m[piv][col]) < 1e-12) throw IllegalArgumentException("No unique solution")
            val tmp = m[col]; m[col] = m[piv]; m[piv] = tmp
            val dv = m[col][col]
            for (j in col until n + 1) m[col][j] /= dv
            for (r in 0 until n) {
                if (r == col) continue
                val f = m[r][col]
                for (j in col until n + 1) m[r][j] -= f * m[col][j]
            }
        }
        return DoubleArray(n) { i -> m[i][n] }
    }

    // ---------- 二次/三次方程 ----------
    fun solveQuadratic(a: Double, b: Double, c: Double): List<Complex> {
        if (abs(a) < 1e-15) {
            if (abs(b) < 1e-15) return emptyList()
            return listOf(Complex.fromReal(-c / b))
        }
        val d = b * b - 4 * a * c
        return if (d >= 0) {
            val sq = sqrt(d)
            listOf(Complex.fromReal((-b + sq) / (2 * a)), Complex.fromReal((-b - sq) / (2 * a)))
        } else {
            val re = -b / (2 * a)
            val im = sqrt(-d) / (2 * a)
            listOf(Complex(re, im), Complex(re, -im))
        }
    }

    fun solveCubic(a: Double, b: Double, c: Double, d: Double): List<Complex> {
        if (abs(a) < 1e-15) return solveQuadratic(b, c, d)
        val A = b / a
        val B = c / a
        val C = d / a
        val Q = (A * A - 3 * B) / 9.0
        val R = (2 * A * A * A - 9 * A * B + 27 * C) / 54.0
        val D = Q * Q * Q - R * R
        val roots = mutableListOf<Complex>()
        if (D >= 0) {
            val sqD = sqrt(D)
            val S = cbrt(R + sqD)
            val T = cbrt(R - sqD)
            val x1 = Complex.fromReal(S + T - A / 3.0)
            roots.add(x1)
            // 其余两根（复数）
            val reMid = -(S + T) / 2 - A / 3.0
            val imMid = sqrt(3.0) / 2.0 * (S - T)
            roots.add(Complex(reMid, imMid))
            roots.add(Complex(reMid, -imMid))
        } else {
            val theta = acos(R / sqrt(Q * Q * Q))
            val s = -2 * sqrt(Q)
            roots.add(Complex.fromReal(s * cos(theta / 3.0) - A / 3.0))
            roots.add(Complex.fromReal(s * cos((theta + 2 * PI) / 3.0) - A / 3.0))
            roots.add(Complex.fromReal(s * cos((theta + 4 * PI) / 3.0) - A / 3.0))
        }
        return roots
    }

    // ---------- 统计 ----------
    class StatResult(val lines: List<Pair<String, String>>)

    fun stat1Var(x: List<Double>): StatResult {
        val n = x.size
        val mean = x.sum() / n
        val sumX = x.sum()
        val sumX2 = x.sumOf { it * it }
        val variancePop = sumX2 / n - mean * mean
        val sx = if (n > 1) sqrt((sumX2 - n * mean * mean) / (n - 1)) else 0.0
        val sigmax = sqrt(max(0.0, variancePop))
        val lines = listOf(
            "n" to n.toString(),
            "x̄" to Fmt.num(mean),
            "Σx" to Fmt.num(sumX),
            "Σx²" to Fmt.num(sumX2),
            "σx" to Fmt.num(sigmax),
            "sx" to Fmt.num(sx)
        )
        return StatResult(lines)
    }

    fun stat2Var(xy: List<Pair<Double, Double>>): StatResult {
        val n = xy.size
        val sx = xy.sumOf { it.first }
        val sy = xy.sumOf { it.second }
        val sx2 = xy.sumOf { it.first * it.first }
        val sy2 = xy.sumOf { it.second * it.second }
        val sxy = xy.sumOf { it.first * it.second }
        val mx = sx / n
        val my = sy / n
        val denom = n * sx2 - sx * sx
        val slope = if (abs(denom) > 1e-15) (n * sxy - sx * sy) / denom else 0.0
        val intercept = my - slope * mx
        val r = if (abs(n * sx2 - sx * sx) > 1e-15 && abs(n * sy2 - sy * sy) > 1e-15)
            (n * sxy - sx * sy) / sqrt((n * sx2 - sx * sx) * (n * sy2 - sy * sy))
        else 0.0
        val lines = listOf(
            "n" to n.toString(),
            "x̄" to Fmt.num(mx),
            "ȳ" to Fmt.num(my),
            "Σx" to Fmt.num(sx),
            "Σy" to Fmt.num(sy),
            "Σx²" to Fmt.num(sx2),
            "Σy²" to Fmt.num(sy2),
            "Σxy" to Fmt.num(sxy),
            "a(截距)" to Fmt.num(intercept),
            "b(斜率)" to Fmt.num(slope),
            "r(相关系数)" to Fmt.num(r)
        )
        return StatResult(lines)
    }

    // ---------- 进制换算 ----------
    enum class Base(val radix: Int, val label: String) {
        DEC(10, "DEC"), HEX(16, "HEX"), BIN(2, "BIN"), OCT(8, "OCT")
    }

    fun baseConvert(value: String, from: Base, to: Base): String {
        val v = when (from) {
            Base.DEC -> value.toLongOrNull() ?: throw IllegalArgumentException("Math ERROR")
            else -> value.toLongOrNull(from.radix) ?: throw IllegalArgumentException("Math ERROR")
        }
        return when (to) {
            Base.DEC -> v.toString()
            else -> java.lang.Long.toString(v, to.radix).uppercase()
        }
    }

    fun logicOp(a: Long, b: Long, op: String): Long = when (op) {
        "and" -> a and b
        "or" -> a or b
        "xor" -> a xor b
        else -> a
    }

    fun notOp(a: Long, bits: Int): Long {
        val mask = (1L shl bits) - 1
        return a.inv() and mask
    }

    // ---------- 单位换算（长度/面积/体积/质量/温度/时间/速度/压力/能量/功率）----------
    data class UnitItem(val name: String, val factorToBase: Double, val offset: Double = 0.0) {
        fun toBase(v: Double) = v * factorToBase + offset
        fun fromBase(v: Double) = (v - offset) / factorToBase
    }

    data class UnitCategory(val name: String, val units: List<UnitItem>)

    val unitCategories: List<UnitCategory> = listOf(
        UnitCategory("长度", listOf(
            UnitItem("米 m", 1.0), UnitItem("千米 km", 1e3), UnitItem("厘米 cm", 1e-2),
            UnitItem("毫米 mm", 1e-3), UnitItem("微米 μm", 1e-6), UnitItem("纳米 nm", 1e-9),
            UnitItem("英寸 in", 0.0254), UnitItem("英尺 ft", 0.3048), UnitItem("码 yd", 0.9144),
            UnitItem("英里 mi", 1609.344), UnitItem("海里 nmi", 1852.0)
        )),
        UnitCategory("面积", listOf(
            UnitItem("平方米 m²", 1.0), UnitItem("平方厘米 cm²", 1e-4), UnitItem("公顷 ha", 1e4),
            UnitItem("平方千米 km²", 1e6), UnitItem("亩", 666.6667), UnitItem("英亩 acre", 4046.856)
        )),
        UnitCategory("体积", listOf(
            UnitItem("立方米 m³", 1.0), UnitItem("升 L", 1e-3), UnitItem("毫升 mL", 1e-6),
            UnitItem("加仑(美) gal", 0.00378541), UnitItem("加仑(英) gal", 0.00454609)
        )),
        UnitCategory("质量", listOf(
            UnitItem("千克 kg", 1.0), UnitItem("克 g", 1e-3), UnitItem("毫克 mg", 1e-6),
            UnitItem("吨 t", 1e3), UnitItem("磅 lb", 0.4535924), UnitItem("盎司 oz", 0.02834952)
        )),
        UnitCategory("温度", listOf(
            UnitItem("摄氏度 ℃", 1.0, 0.0), UnitItem("华氏度 ℉", 5.0 / 9.0, -160.0 / 9.0),
            UnitItem("开尔文 K", 1.0, -273.15)
        )),
        UnitCategory("时间", listOf(
            UnitItem("秒 s", 1.0), UnitItem("分 min", 60.0), UnitItem("小时 h", 3600.0),
            UnitItem("天 d", 86400.0)
        )),
        UnitCategory("速度", listOf(
            UnitItem("米/秒 m/s", 1.0), UnitItem("千米/时 km/h", 3.6), UnitItem("英里/时 mph", 2.236936),
            UnitItem("节 kn", 1.943844)
        )),
        UnitCategory("压力", listOf(
            UnitItem("帕 Pa", 1.0), UnitItem("千帕 kPa", 1e3), UnitItem("巴 bar", 1e5),
            UnitItem("大气压 atm", 101325.0), UnitItem("毫米汞柱 mmHg", 133.3224)
        )),
        UnitCategory("能量", listOf(
            UnitItem("焦耳 J", 1.0), UnitItem("千焦 kJ", 1e3), UnitItem("卡路里 cal", 4.184),
            UnitItem("千瓦时 kWh", 3.6e6), UnitItem("电子伏 eV", 1.602176634e-19)
        )),
        UnitCategory("功率", listOf(
            UnitItem("瓦 W", 1.0), UnitItem("千瓦 kW", 1e3), UnitItem("马力 hp", 745.6999)
        ))
    )

    fun convertUnit(value: Double, from: UnitItem, to: UnitItem): Double =
        to.fromBase(from.toBase(value))

    // ---------- 科学常数 ----------
    data class Constant(val name: String, val symbol: String, val value: Double, val unit: String)

    val constants: List<Constant> = listOf(
        Constant("阿伏伽德罗常数", "NA", 6.02214076e23, "1/mol"),
        Constant("玻尔兹曼常数", "k", 1.380649e-23, "J/K"),
        Constant("气体常数", "R", 8.314462618, "J/(mol·K)"),
        Constant("普朗克常数", "h", 6.62607015e-34, "J·s"),
        Constant("光速", "c", 299792458.0, "m/s"),
        Constant("真空介电常数", "ε0", 8.8541878128e-12, "F/m"),
        Constant("真空磁导率", "μ0", 1.25663706212e-6, "N/A²"),
        Constant("电子电荷", "e", 1.602176634e-19, "C"),
        Constant("电子质量", "me", 9.1093837015e-31, "kg"),
        Constant("质子质量", "mp", 1.67262192369e-27, "kg"),
        Constant("中子质量", "mn", 1.67492749804e-27, "kg"),
        Constant("引力常数", "G", 6.67430e-11, "m³/(kg·s²)"),
        Constant("重力加速度", "g", 9.80665, "m/s²"),
        Constant("标准大气压", "atm", 101325.0, "Pa"),
        Constant("绝对零度", "T0", -273.15, "℃"),
        Constant("理想气体摩尔体积", "Vm", 22.41396954e-3, "m³/mol"),
        Constant("玻尔半径", "a0", 5.29177210903e-11, "m"),
        Constant("里德伯常数", "R∞", 10973731.568160, "1/m"),
        Constant("斯特藩-玻尔兹曼常数", "σ", 5.670374419e-8, "W/(m²·K⁴)")
    )
}