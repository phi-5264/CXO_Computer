package com.casio.calc

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 输入层回归测试：没有左操作数时，二元运算符不应被插入。
 *
 * 开头 / 左括号后 / 函数括号后按 × ÷ ^ nCr nPr，原本会得到 "×5"、"(×5"、"sin(×5"
 * 这类按下去就必然 Syntax ERROR、只能靠 DEL 退出的死路。现在这些按键被优雅忽略，
 * "-" 仍作为一元负号正常工作。
 */
class CalcStateNoLeadOperatorTest {

    private val binaryOps = listOf("×", "÷", "^", "C", "P")

    @Test
    fun `开头按二元运算符被忽略`() {
        for (op in binaryOps) {
            val s = CalcState()
            s.inputOperator(op)
            assertEquals("开头按 $op 不应产生 token", "", s.displayInput())

            s.inputDigit(4)
            assertEquals("开头按 $op 后仍可正常输入", "4", s.displayInput())
            assertEquals(4.0, s.evaluateValue().re, 1e-12)
        }
    }

    @Test
    fun `左括号后按二元运算符被忽略`() {
        for (op in binaryOps) {
            val s = CalcState()
            s.inputLParen(); s.inputOperator(op); s.inputDigit(2); s.inputRParen()
            assertEquals("(2)", s.displayInput())
            assertEquals(2.0, s.evaluateValue().re, 1e-12)
        }
    }

    @Test
    fun `函数括号后按二元运算符被忽略`() {
        for (op in binaryOps) {
            val s = CalcState()
            s.inputFunc("sin", "sin"); s.inputOperator(op)
            s.inputDigit(0); s.inputRParen()
            assertEquals("sin(0)", s.displayInput())
            assertEquals(0.0, s.evaluateValue().re, 1e-12)
        }
    }

    @Test
    fun `开头减号仍是一元负号`() {
        val s = CalcState()
        s.inputOperator("-"); s.inputDigit(5)
        assertEquals("-5", s.displayInput())
        assertEquals(-5.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `左括号后减号仍可输入`() {
        val s = CalcState()
        s.inputLParen(); s.inputOperator("-"); s.inputDigit(2); s.inputRParen()
        assertEquals("(-2)", s.displayInput())
        assertEquals(-2.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `忽略按键后表达式仍可继续`() {
        val s = CalcState()
        s.inputOperator("×")          // 忽略
        s.inputDigit(5)
        s.inputOperator("+")
        s.inputDigit(3)
        assertEquals("5+3", s.displayInput())
        assertEquals(8.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `有左操作数时运算符照常插入`() {
        val s = CalcState()
        s.inputDigit(2); s.inputOperator("×"); s.inputDigit(3)
        assertEquals("2×3", s.displayInput())
        assertEquals(6.0, s.evaluateValue().re, 1e-12)
    }

    @Test
    fun `右括号后的乘号正常`() {
        val s = CalcState()
        s.inputLParen(); s.inputDigit(1); s.inputOperator("+"); s.inputDigit(2)
        s.inputRParen(); s.inputOperator("×"); s.inputDigit(3)
        assertEquals("(1+2)×3", s.displayInput())
        assertEquals(9.0, s.evaluateValue().re, 1e-12)
    }
}
