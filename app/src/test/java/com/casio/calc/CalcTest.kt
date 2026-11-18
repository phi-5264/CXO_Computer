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
    fun `温度换算双向正确`() {
        val t = Modes.unitCategories.first { it.name == "温度" }
        val c = t.units[0]; val f = t.units[1]; val k = t.units[2]
        assertEquals(212.0, Modes.convertUnit(100.0, c, f), 1e-9)
        assertEquals(0.0, Modes.convertUnit(32.0, f, c), 1e-9)
        assertEquals(273.15, Modes.convertUnit(0.0, c, k), 1e-9)
        assertEquals(-273.15, Modes.convertUnit(0.0, k, c), 1e-9)
    }
}
