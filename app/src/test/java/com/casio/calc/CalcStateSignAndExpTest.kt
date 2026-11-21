package com.casio.calc

import org.junit.Assert.assertEquals
import org.junit.Test

/** 输入层回归：± 的显示/取值一致性，以及 ×10ˣ 的前缀乘号。纯 JVM，不依赖 Android。 */
class CalcStateSignAndExpTest {

    private fun value(s: CalcState): Double = s.evaluateValue().re

    @Test
    fun `零取反后输入数字仍为负`() {
        val s = CalcState()
        s.inputDigit(0)
        s.inputNegate()
        s.inputDigit(5)
        assertEquals("-5", s.displayInput())
        assertEquals(-5.0, value(s), 0.0)
    }

    @Test
    fun `零点取反后输入小数位不丢符号和小数点`() {
        val s = CalcState()
        s.inputDigit(0)
        s.inputDot()
        s.inputNegate()
        s.inputDigit(5)
        assertEquals("-0.5", s.displayInput())
        assertEquals(-0.5, value(s), 0.0)
    }

    @Test
    fun `再次取反恢复正数并保留末尾小数点`() {
        val s = CalcState()
        s.inputDigit(0)
        s.inputDot()
        s.inputNegate()
        s.inputNegate()
        assertEquals("0.", s.displayInput())
        s.inputDigit(5)
        assertEquals("0.5", s.displayInput())
        assertEquals(0.5, value(s), 0.0)
    }

    @Test
    fun `多位数取反后继续输入数字`() {
        val s = CalcState()
        s.inputDigit(1)
        s.inputDigit(2)
        s.inputNegate()
        s.inputDigit(3)
        assertEquals("-123", s.displayInput())
        assertEquals(-123.0, value(s), 0.0)
    }

    @Test
    fun `科学计数键在开头不产生多余乘号`() {
        val s = CalcState()
        s.inputExp()
        s.inputDigit(3)
        assertEquals("10^3", s.displayInput())
        assertEquals(1000.0, value(s), 1e-9)
    }

    @Test
    fun `科学计数键在运算符后不产生连续运算符`() {
        val s = CalcState()
        s.inputDigit(2)
        s.inputOperator("+")
        s.inputExp()
        s.inputDigit(3)
        assertEquals("2+10^3", s.displayInput())
        assertEquals(1002.0, value(s), 1e-9)
    }

    @Test
    fun `科学计数键在左括号后不产生连续运算符`() {
        val s = CalcState()
        s.inputLParen()
        s.inputExp()
        s.inputDigit(3)
        s.inputRParen()
        assertEquals("(10^3)", s.displayInput())
        assertEquals(1000.0, value(s), 1e-9)
    }

    @Test
    fun `科学计数键在已有值后仍保留乘号`() {
        val s = CalcState()
        s.inputDigit(2)
        s.inputExp()
        s.inputDigit(3)
        assertEquals("2×10^3", s.displayInput())
        assertEquals(2000.0, value(s), 1e-9)
    }
}
