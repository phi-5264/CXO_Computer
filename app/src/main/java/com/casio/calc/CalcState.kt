package com.casio.calc

/** 当前所处模式 */
enum class Mode { COMP, STAT, EQN, MATRIX, VECTOR, BASEN, UNIT, CONST }

/**
 * 计算器“大脑”：维护输入令牌、设置、存储器，并执行求值。
 * 与 UI 无关，纯 Kotlin。
 */
class CalcState {

    val tokens = mutableListOf<Tok>()
    val cfg = CalcConfig()

    var memory = Complex.ZERO          // M
    var ans = Complex.ZERO
    var preAns = Complex.ZERO

    val vars = mutableMapOf<String, Complex>()
    var mode = Mode.COMP

    init {
        for (name in listOf("A", "B", "C", "D", "E", "F", "X", "Y")) vars[name] = Complex.ZERO
    }

    fun displayInput(): String = tokens.joinToString("") { it.d }

    fun isInputEmpty(): Boolean = tokens.isEmpty()

    // ---------- 输入编辑 ----------

    private fun last(): Tok? = tokens.lastOrNull()

    fun inputDigit(d: Int) {
        val l = last()
        if (l is NumTok && l.d != "10" && !l.d.contains('ᴱ')) {
            var s = l.d + d.toString()
            if (s == "0$d") s = d.toString() // 去掉前导 0
            if (isValidNum(s)) {
                tokens[tokens.size - 1] = NumTok(Complex.fromReal(s.toDouble()), s)
                return
            }
        }
        tokens.add(NumTok(Complex.fromReal(d.toDouble()), d.toString()))
    }

    private fun isValidNum(s: String): Boolean {
        return try { s.toDouble(); true } catch (e: Exception) { false }
    }

    fun inputDot() {
        val l = last()
        if (l is NumTok && !l.d.contains('.') && l.d != "10") {
            tokens[tokens.size - 1] = NumTok(l.n, l.d + ".")
        } else {
            tokens.add(NumTok(Complex.fromReal(0.0), "0."))
        }
    }

    fun inputOperator(opSym: String) {
        // opSym: "+" "-" "×" "÷" "^" "C" "P"
        val disp = when (opSym) {
            "C" -> "nCr"
            "P" -> "nPr"
            else -> opSym
        }
        val l = last()
        if (l is OpTok) {
            tokens[tokens.size - 1] = OpTok(opSym, disp)
        } else {
            tokens.add(OpTok(opSym, disp))
        }
    }

    fun inputFunc(fn: String, display: String) {
        tokens.add(FnTok(fn, display))
        tokens.add(LpTok())
    }

    fun inputConst(name: String) {
        tokens.add(ConstTok(name))
    }

    fun inputVar(name: String) {
        tokens.add(VarTok(name))
    }

    fun inputImag() {
        tokens.add(ImagTok())
    }

    fun inputPostfix(op: String, display: String) {
        tokens.add(PostTok(op, display))
    }

    fun inputLParen() = tokens.add(LpTok())

    fun inputRParen() {
        var open = 0
        for (t in tokens) {
            if (t is LpTok) open++
            if (t is RpTok) open--
        }
        if (open > 0) tokens.add(RpTok())
    }

    fun inputNegate() {
        val l = last()
        if (l is NumTok) {
            val v = -l.n.re
            val s = formatNum(v)
            tokens[tokens.size - 1] = NumTok(Complex.fromReal(v), s)
        } else {
            tokens.add(OpTok("-", "-"))
        }
    }

    fun inputExp() {
        tokens.add(OpTok("×", "×"))
        tokens.add(NumTok(Complex.fromReal(10.0), "10"))
        tokens.add(OpTok("^", "^"))
    }

    fun backspace() {
        if (tokens.isNotEmpty()) tokens.removeAt(tokens.size - 1)
    }

    fun clearAll() {
        tokens.clear()
    }

    // ---------- 求值 ----------

    fun buildVars(): Map<String, Complex> {
        val m = HashMap<String, Complex>()
        m.putAll(vars)
        m["A"] = vars["A"] ?: Complex.ZERO
        m["B"] = vars["B"] ?: Complex.ZERO
        m["C"] = vars["C"] ?: Complex.ZERO
        m["D"] = vars["D"] ?: Complex.ZERO
        m["E"] = vars["E"] ?: Complex.ZERO
        m["F"] = vars["F"] ?: Complex.ZERO
        m["X"] = vars["X"] ?: Complex.ZERO
        m["Y"] = vars["Y"] ?: Complex.ZERO
        m["M"] = memory
        m["Ans"] = ans
        m["PreAns"] = preAns
        return m
    }

    fun evaluate(): String {
        if (tokens.isEmpty()) return ""
        val ctx = EvalCtx(buildVars(), cfg)
        val r = Parser(tokens.toList(), ctx).evaluate()
        preAns = ans
        ans = r
        return Fmt.complex(r, cfg)
    }

    fun lastResult(): String = Fmt.complex(ans, cfg)

    // ---------- 存储 ----------

    fun memoryAdd(value: Complex) {
        memory = memory + value
    }

    fun memorySub(value: Complex) {
        memory = memory - value
    }

    fun storeVar(name: String, value: Complex) {
        if (name == "M") memory = value else vars[name] = value
    }

    companion object {
        fun formatNum(v: Double): String {
            return if (v == kotlin.math.floor(v) && !v.isInfinite()) {
                if (v == 0.0) "0" else {
                    // 用整数输出
                    v.toLong().toString()
                }
            } else v.toString()
        }
    }
}