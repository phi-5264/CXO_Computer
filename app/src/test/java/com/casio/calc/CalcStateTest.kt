package com.casio.calc

import org.junit.Assert.assertEquals
import org.junit.Test

/** 输入层回归测试：按键序列产生的式子和求值结果必须符合预期。 */
class CalcStateTest {

    @Test
    fun `重复小数点被忽略`() {
        val s = CalcState()
        s.inputDigit(1); s.inputDot(); s.inputDigit(2)
        s.inputDot()   // 第二次小数点应被忽略
        s.inputDigit(5)

        assertEquals("1.25", s.displayInput())
        assertEquals(1.25, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `空输入连按小数点只保留一个`() {
        val s = CalcState()
        s.inputDot(); s.inputDot()

        assertEquals("0.", s.displayInput())
        assertEquals(0.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `整数后仍可输入小数点`() {
        val s = CalcState()
        s.inputDigit(1); s.inputDigit(2); s.inputDot(); s.inputDigit(5)

        assertEquals("12.5", s.displayInput())
        assertEquals(12.5, s.evaluateValue().re, 1e-12)
    }
}
