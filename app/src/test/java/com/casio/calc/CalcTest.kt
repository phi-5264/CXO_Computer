package com.casio.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.sin

/** 纯逻辑回归测试：不依赖 Android 运行时。 */
class CalcTest {

    private fun eval(vararg toks: Tok, cfg: CalcConfig = CalcConfig()): Complex =
        Parser(toks.toList(), EvalCtx(emptyMap(), cfg)).evaluate()

    private fun num(v: Double) = NumTok(Complex.fromReal(v), Fmt.num(v))
    private fun op(s: String) = OpTok(s, s)

    @Test
    fun `四则运算与优先级`() {
        // 2 + 3 × 4 = 14
        val r = eval(num(2.0), op("+"), num(3.0), op("×"), num(4.0))
        assertEquals(14.0, r.re, 1e-12)
    }

    @Test
    fun `幂运算右结合`() {
        // 2 ^ 3 ^ 2 = 512
        val r = eval(num(2.0), op("^"), num(3.0), op("^"), num(2.0))
        assertEquals(512.0, r.re, 1e-9)
    }

    @Test
    fun `负号优先级低于幂`() {
        // -2 ^ 2 = -4
        val r = eval(op("-"), num(2.0), op("^"), num(2.0))
        assertEquals(-4.0, r.re, 1e-12)
    }

    @Test
    fun `隐式乘法`() {
        // 2(3+4) = 14
        val r = eval(num(2.0), LpTok(), num(3.0), op("+"), num(4.0), RpTok())
        assertEquals(14.0, r.re, 1e-12)
    }

    @Test
    fun `隐式乘法优先级高于除法`() {
        // 6÷2(1+2) = 1（卡西欧 fx-991CN X 语义：省略乘号先结合）
        val implicit = eval(
            num(6.0), op("÷"), num(2.0),
            LpTok(), num(1.0), op("+"), num(2.0), RpTok()
        )
        assertEquals(1.0, implicit.re, 1e-12)
        // 显式写 × 时仍按从左到右：6÷2×(1+2) = 9
        val explicit = eval(
            num(6.0), op("÷"), num(2.0), op("×"),
            LpTok(), num(1.0), op("+"), num(2.0), RpTok()
        )
        assertEquals(9.0, explicit.re, 1e-12)
    }

    @Test
    fun `隐式乘法优先级的其他写法`() {
        // 8÷2(2+2) = 1
        val a = eval(
            num(8.0), op("÷"), num(2.0),
            LpTok(), num(2.0), op("+"), num(2.0), RpTok()
        )
        assertEquals(1.0, a.re, 1e-12)
        // 1÷2(3) = 1/6
        val b = eval(num(1.0), op("÷"), num(2.0), LpTok(), num(3.0), RpTok())
        assertEquals(1.0 / 6.0, b.re, 1e-12)
        // 2(3+4) 不受影响
        val c = eval(num(2.0), LpTok(), num(3.0), op("+"), num(4.0), RpTok())
        assertEquals(14.0, c.re, 1e-12)
    }

    @Test
    fun `百分号语义`() {
        // 100 + 10% = 110
        val r = eval(num(100.0), op("+"), num(10.0), PostTok("percent", "%"))
        assertEquals(110.0, r.re, 1e-12)
    }

    @Test
    fun `未闭合左括号自动补全`() {
        val r = eval(num(1.0), op("+"), LpTok(), num(2.0))
        assertEquals(3.0, r.re, 1e-12)
    }

    @Test
    fun `多余的右括号报错`() {
        assertThrows(IllegalArgumentException::class.java) {
            eval(num(1.0), RpTok())
        }
    }

    @Test
    fun `角度制三角函数`() {
        val r = eval(FnTok("sin", "sin"), LpTok(), num(30.0), RpTok())
        assertEquals(0.5, r.re, 1e-12)
    }

    @Test
    fun `FIX 模式保留尾零`() {
        val cfg = CalcConfig().apply { dispMode = DispMode.FIX; digits = 2 }
        assertEquals("1.50", Fmt.real(1.5, cfg))
        assertEquals("-0.50", Fmt.real(-0.5, cfg))
    }

    @Test
    fun `SCI 模式保留有效位`() {
        val cfg = CalcConfig().apply { dispMode = DispMode.SCI; digits = 3 }
        assertEquals("1.50×10^3", Fmt.real(1500.0, cfg))
    }

    @Test
    fun `NORM 模式去掉尾零`() {
        val cfg = CalcConfig()
        assertEquals("0.3", Fmt.real(0.1 + 0.2, cfg))
        assertEquals("2", Fmt.real(2.0, cfg))
    }

    @Test
    fun `百分号不越过括号泄漏`() {
        // 100 + (10%) 应按普通数值相加，而不是卡西欧的 A+A×B% 语义
        val r = eval(num(100.0), op("+"), LpTok(), num(10.0), PostTok("percent", "%"), RpTok())
        assertEquals(100.1, r.re, 1e-12)
    }

    @Test
    fun `百分号不越过函数参数泄漏`() {
        val cfg = CalcConfig().apply { angle = AngleMode.RAD }
        val r = eval(
            num(100.0), op("+"), FnTok("sin", "sin"),
            LpTok(), num(10.0), PostTok("percent", "%"), RpTok(), cfg = cfg
        )
        assertEquals(100.0 + sin(0.1), r.re, 1e-9)
        val r2 = eval(num(100.0), op("+"), FnTok("sqrt", "sqrt"), LpTok(), num(25.0), PostTok("percent", "%"), RpTok())
        assertEquals(100.5, r2.re, 1e-12)
    }

    @Test
    fun `幂运算后百分号不再是整体百分数`() {
        // 100 + 10%^2 = 100 + 0.01
        val r = eval(num(100.0), op("+"), num(10.0), PostTok("percent", "%"), op("^"), num(2.0))
        assertEquals(100.01, r.re, 1e-12)
    }

    @Test
    fun `乘除里的百分号不污染加法`() {
        // 100 + 200×10% = 120
        val r = eval(num(100.0), op("+"), num(200.0), op("×"), num(10.0), PostTok("percent", "%"))
        assertEquals(120.0, r.re, 1e-12)
    }

    @Test
    fun `特殊角三角函数归零`() {
        val deg = CalcConfig()
        assertEquals(0.0, eval(FnTok("sin", "sin"), LpTok(), num(180.0), RpTok(), cfg = deg).re, 1e-12)
        assertEquals(0.0, eval(FnTok("cos", "cos"), LpTok(), num(90.0), RpTok(), cfg = deg).re, 1e-12)
        assertEquals(0.0, eval(FnTok("tan", "tan"), LpTok(), num(180.0), RpTok(), cfg = deg).re, 1e-12)
    }

    @Test
    fun `极小正弦值不被归零`() {
        // sin(1e-13) = 1e-13，不能被特殊角归零逻辑抹成 0
        val cfg = CalcConfig().apply { angle = AngleMode.RAD }
        val r = eval(FnTok("sin", "sin"), LpTok(), num(1e-13), RpTok(), cfg = cfg)
        assertEquals(1e-13, r.re, 1e-27)
    }

    @Test
    fun `大实部不吞掉小虚部`() {
        val r = eval(num(1e15), op("+"), ImagTok())
        assertEquals(1e15, r.re, 1e-3)
        assertEquals(1.0, r.im, 1e-12)
        assertEquals("1×10^15+i", Fmt.complex(r, CalcConfig()))
    }

    @Test
    fun `单位换算使用精确系数`() {
        fun cat(name: String) = Modes.unitCategories.first { it.name == name }
        fun unit(catName: String, prefix: String) = cat(catName).units.first { it.name.startsWith(prefix) }

        assertEquals(0.45359237, Modes.convertUnit(1.0, unit("质量", "磅"), cat("质量").units[0]), 1e-12)
        assertEquals(1e4 / 15.0, Modes.convertUnit(1.0, unit("面积", "亩"), cat("面积").units[0]), 1e-9)
        assertEquals(4046.8564224, Modes.convertUnit(1.0, unit("面积", "英亩"), cat("面积").units[0]), 1e-9)
        assertEquals(1852.0 / 3600.0, Modes.convertUnit(1.0, unit("速度", "节"), cat("速度").units[0]), 1e-12)
        assertEquals(133.322387415, Modes.convertUnit(1.0, unit("压力", "毫米汞柱"), cat("压力").units[0]), 1e-9)
        assertEquals(745.6998715822702, Modes.convertUnit(1.0, unit("功率", "马力"), cat("功率").units[0]), 1e-9)
    }

    @Test
    fun `新增单位类别换算正确`() {
        fun cat(name: String) = Modes.unitCategories.first { it.name == name }
        fun unit(catName: String, prefix: String) = cat(catName).units.first { it.name.startsWith(prefix) }

        // 数据：1 MiB = 1024 KiB = 1048576 B；1 kB = 8000 bit
        assertEquals(1048576.0, Modes.convertUnit(1.0, unit("数据", "MiB"), cat("数据").units[0]), 1e-9)
        assertEquals(8000.0, Modes.convertUnit(1.0, unit("数据", "千字节"), unit("数据", "比特")), 1e-9)
        assertEquals(1000.0, Modes.convertUnit(1.0, unit("数据", "吉字节"), unit("数据", "兆字节")), 1e-9)

        // 频率：1 MHz = 1000 kHz；60 rpm = 1 Hz
        assertEquals(1000.0, Modes.convertUnit(1.0, unit("频率", "兆赫兹"), unit("频率", "千赫兹")), 1e-12)
        assertEquals(1.0, Modes.convertUnit(60.0, unit("频率", "转/分"), unit("频率", "赫兹")), 1e-12)

        // 角度：180° = π rad；1 rad ≈ 57.29577951308232°
        assertEquals(Math.PI, Modes.convertUnit(180.0, unit("角度", "度"), unit("角度", "弧度")), 1e-12)
        assertEquals(180.0 / Math.PI, Modes.convertUnit(1.0, unit("角度", "弧度"), unit("角度", "度")), 1e-12)
        assertEquals(1.0, Modes.convertUnit(60.0, unit("角度", "角分"), unit("角度", "度")), 1e-12)

        // 光照：1 fc ≈ 10.7639104167 lx；1000 lx = 1 klx
        assertEquals(10.763910416709722, Modes.convertUnit(1.0, unit("光照", "英尺烛光"), unit("光照", "勒克斯")), 1e-9)
        assertEquals(1.0, Modes.convertUnit(1000.0, unit("光照", "勒克斯"), unit("光照", "千勒克斯")), 1e-12)
    }

    @Test
    fun `FIX 模式超大数自动切科学计数`() {
        val cfg = CalcConfig().apply { dispMode = DispMode.FIX; digits = 2 }
        assertEquals("1.00×10^10", Fmt.real(1e10, cfg))
        assertEquals("-1.00×10^10", Fmt.real(-1e10, cfg))
        // 舍入后尾数进到 10 时，指数要跟着进一位
        assertEquals("1.00×10^11", Fmt.real(9.999e10, cfg))
        // 阈值以下仍保持固定小数位
        assertEquals("9999999999.00", Fmt.real(9999999999.0, cfg))
        // 阈值看四舍五入后的值：9.9999999999999e9 进位后是 11 位整数，应切科学计数
        assertEquals("1.00×10^10", Fmt.real(9999999999.9999, cfg))
        assertEquals("-1.00×10^10", Fmt.real(-9999999999.9999, cfg))
        // 极大值不能拼出几百位字符
        assertEquals("1.00×10^300", Fmt.real(1e300, cfg))
    }

    @Test
    fun `温度换算双向正确`() {
        val t = Modes.unitCategories.first { it.name == "温度" }
        val c = t.units[0]; val f = t.units[1]; val k = t.units[2]
        assertEquals(212.0, Modes.convertUnit(100.0, c, f), 1e-9)
        assertEquals(0.0, Modes.convertUnit(32.0, f, c), 1e-9)
        assertEquals(273.15, Modes.convertUnit(0.0, c, k), 1e-9)
        assertEquals(-273.15, Modes.convertUnit(0.0, k, c), 1e-9)
    }

    @Test
    fun `向量夹角跟随角度制`() {
        val a = Vector.fromReals(doubleArrayOf(3.0, 0.0))
        val b = Vector.fromReals(doubleArrayOf(0.0, 4.0))
        val rad = a.angleRad(b)

        val deg = CalcConfig().apply { angle = AngleMode.DEG }
        val r = CalcConfig().apply { angle = AngleMode.RAD }
        val gra = CalcConfig().apply { angle = AngleMode.GRA }

        assertEquals("90°", Fmt.angle(rad, deg))
        assertEquals("1.570796327 rad", Fmt.angle(rad, r))
        assertEquals("100g", Fmt.angle(rad, gra))
    }

    @Test
    fun `零向量夹角报错`() {
        assertThrows(IllegalArgumentException::class.java) {
            Vector.fromReals(doubleArrayOf(0.0, 0.0))
                .angleRad(Vector.fromReals(doubleArrayOf(1.0, 0.0)))
        }
    }

    @Test
    fun `极坐标角度跟随角度制`() {
        val z = Complex(0.0, 1.0)
        assertEquals("1∠90°", Fmt.polar(z, CalcConfig().apply { angle = AngleMode.DEG }))
        assertEquals("1∠100g", Fmt.polar(z, CalcConfig().apply { angle = AngleMode.GRA }))
    }

    @Test
    fun `省略函数括号只取一个操作数`() {
        val deg = CalcConfig().apply { angle = AngleMode.DEG }
        // sin 30 = 0.5
        assertEquals(0.5, eval(FnTok("sin", "sin"), num(30.0), cfg = deg).re, 1e-12)
        // sin 30 + 2 = 2.5（不能被解析成 sin(32)）
        assertEquals(2.5, eval(FnTok("sin", "sin"), num(30.0), op("+"), num(2.0), cfg = deg).re, 1e-12)
        // 2sin 30 = 1（隐式乘法 + 无括号函数）
        assertEquals(1.0, eval(num(2.0), FnTok("sin", "sin"), num(30.0), cfg = deg).re, 1e-12)
        // √9 + 1 = 4（不能变成 √10）
        assertEquals(4.0, eval(FnTok("sqrt", "√"), num(9.0), op("+"), num(1.0)).re, 1e-12)
    }

    @Test
    fun `极小纯虚数不被当作实数`() {
        // 1e-13·i 是真实的虚数，isReal 不能按 1e-12 绝对阈值误判为实数
        val z = Complex(0.0, 1e-13)
        assertEquals(false, z.isReal)

        // 平方应走复数路线得到 -1e-26，而不是被实数分支算成 0
        val sq = z.pow(Complex.fromReal(2.0))
        assertEquals(-1e-26, sq.re, 1e-30)
        assertEquals(0.0, sq.im, 1e-30)

        // 开方应为 (√1e-13)·e^{iπ/4}，实部虚部都约 7.07e-7，而不是 0
        val rt = z.sqrt()
        assertEquals(kotlin.math.sqrt(1e-13) / kotlin.math.sqrt(2.0), rt.re, 1e-15)
        assertEquals(rt.re, rt.im, 1e-15)
    }

    @Test
    fun `大实部旁的真实小虚部不丢`() {
        // (1 + 1e-13·i)^2 = 1 + 2e-13·i - 1e-26，虚部虽小但相对 1 是 1e-13，必须保留
        val w = Complex(1.0, 1e-13)
        assertEquals(true, w.isReal.not())
        val sq = w.pow(Complex.fromReal(2.0))
        assertEquals(1.0, sq.re, 1e-12)
        assertEquals(2e-13, sq.im, 1e-18)
    }
}
