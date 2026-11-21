package com.casio.calc

import org.junit.Assert.assertEquals
import org.junit.Test

/** 科学计数法常数插入后的数字输入：显示串与实际求值必须一致。 */
class CalcStateSciConstTest {

    private fun state(vararg toks: Tok): CalcState = CalcState().apply {
        for (t in toks) tokens.add(t)
        cursor = tokens.size
    }

    @Test
    fun `指数常数后接数字补显式乘号`() {
        val h = NumTok(Complex.fromReal(6.62607015e-34), "6.62607015ᴱ-34")
        val s = state(h)
        s.inputDigit(2)
        assertEquals("6.62607015ᴱ-34×2", s.displayInput())
        assertEquals(6.62607015e-34 * 2, s.evaluateValue().re, 1e-45)
    }

    @Test
    fun `负指数常数后接数字同样补乘号`() {
        val h = NumTok(Complex.fromReal(-6.62607015e-34), "-6.62607015ᴱ-34")
        val s = state(h)
        s.inputDigit(2)
        assertEquals("-6.62607015ᴱ-34×2", s.displayInput())
        assertEquals(-6.62607015e-34 * 2, s.evaluateValue().re, 1e-45)
    }

    @Test
    fun `普通数字仍逐位合并`() {
        val s = CalcState()
        s.inputDigit(1)
        s.inputDigit(2)
        assertEquals("12", s.displayInput())
        assertEquals(12.0, s.evaluateValue().re, 0.0)
    }

    @Test
    fun `幂运算符后的指数仍可逐位输入`() {
        val s = state(NumTok(Complex.fromReal(10.0), "10"), OpTok("^", "^"))
        s.inputDigit(3)
        s.inputDigit(4)
        assertEquals("10^34", s.displayInput())
        assertEquals(1e34, s.evaluateValue().re, 1e24)
    }
}
