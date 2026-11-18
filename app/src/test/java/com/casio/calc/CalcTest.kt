package com.casio.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

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
}
