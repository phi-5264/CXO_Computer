package com.casio.calc

import org.junit.Assert.assertEquals
import org.junit.Test

/** 输入层回归测试：按键序列本身应当保持可继续编辑，不产生必然报错的式子。 */
class CalcStateInputTest {

    @Test
    fun `二元减号后按乘号被替换`() {
        val s = CalcState()
        s.inputDigit(2); s.inputOperator("-")
        s.inputOperator("×"); s.inputDigit(3)

        assertEquals("2×3", s.displayInput())
        assertEquals(6.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `二元减号后按加号被替换`() {
        val s = CalcState()
        s.inputDigit(2); s.inputOperator("-")
        s.inputOperator("+"); s.inputDigit(3)

        assertEquals("2+3", s.displayInput())
        assertEquals(5.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `一元负号后按乘号被清掉`() {
        val s = CalcState()
        s.inputDigit(2); s.inputOperator("×")
        s.inputOperator("-")          // 2×-，此时减号是一元负号
        s.inputOperator("×"); s.inputDigit(3)

        assertEquals("2×3", s.displayInput())
        assertEquals(6.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `幂后的一元负号按加号时被清掉`() {
        val s = CalcState()
        s.inputDigit(2); s.inputOperator("^")
        s.inputOperator("-")
        s.inputOperator("+"); s.inputDigit(3)

        assertEquals("2+3", s.displayInput())
        assertEquals(5.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `减号作为一元负号时仍可输入`() {
        val s = CalcState()
        s.inputDigit(2); s.inputOperator("+")
        s.inputOperator("-"); s.inputDigit(3)

        assertEquals("2+-3", s.displayInput())
        assertEquals(-1.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `右括号不能紧跟左括号`() {
        val s = CalcState()
        s.inputLParen(); s.inputRParen()

        assertEquals("(", s.displayInput())
        s.inputDigit(5)
        assertEquals("(5", s.displayInput())
        assertEquals(5.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `右括号不能跟在运算符后`() {
        val s = CalcState()
        s.inputDigit(1); s.inputOperator("+"); s.inputLParen()
        s.inputOperator("-"); s.inputRParen()
        s.inputDigit(2)

        assertEquals("1+(-2", s.displayInput())
        assertEquals(-1.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `正常的右括号仍可输入`() {
        val s = CalcState()
        s.inputLParen(); s.inputDigit(1); s.inputOperator("+"); s.inputDigit(2)
        s.inputRParen(); s.inputOperator("×"); s.inputDigit(3)

        assertEquals("(1+2)×3", s.displayInput())
        assertEquals(9.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `函数括号内不能直接输入右括号`() {
        val s = CalcState()
        s.inputFunc("sin", "sin"); s.inputRParen()

        assertEquals("sin(", s.displayInput())
        s.inputDigit(0); s.inputRParen()
        assertEquals("sin(0)", s.displayInput())
        assertEquals(0.0, s.evaluateValue().re, 1e-12)
    }
}
