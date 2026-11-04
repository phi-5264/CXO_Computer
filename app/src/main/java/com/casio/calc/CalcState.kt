package com.casio.calc

/** 当前所处模式 */
enum class Mode { COMP, STAT, EQN, MATRIX, VECTOR, BASEN, UNIT, CONST, GRAPH }

/**
 * 计算器"大脑"：维护输入令牌、光标、设置、存储器，并执行求值。
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

    /** 光标位置，表示在 tokens[cursor] 之前插入。
     *  cursor == 0 → 在第一个 token 前
     *  cursor == tokens.size → 在最后一个 token 后  */
    var cursor: Int = 0
        set(v) { field = v.coerceIn(0, tokens.size) }

    init {
        for (name in listOf("A", "B", "C", "D", "E", "F", "X", "Y")) vars[name] = Complex.ZERO
    }

    /** 插入新 token 到光标处 */
    private fun insert(tok: Tok) {
        tokens.add(cursor, tok)
        cursor++
    }

    /** 删除光标前一个 token */
    fun backspace() {
        if (cursor > 0) {
            tokens.removeAt(cursor - 1)
            cursor--
        } else if (tokens.isNotEmpty()) {
            // 光标在首位，删除第一个
            tokens.removeAt(0)
        }
    }

    /** 清除全部 */
    fun clearAll() {
        tokens.clear()
        cursor = 0
    }

    fun displayInput(): String = tokens.joinToString("") { it.d }

    fun isInputEmpty(): Boolean = tokens.isEmpty()

    /** 移动光标 */
    fun moveCursorLeft() { if (cursor > 0) cursor-- }
    fun moveCursorRight() { if (cursor < tokens.size) cursor++ }
    fun cursorHome() { cursor = 0 }
    fun cursorEnd() { cursor = tokens.size }

    // ---------- 输入编辑（全部在光标处插入）----------

    fun inputDigit(d: Int) {
        // 如果光标前是数字，合并
        if (cursor > 0) {
            val l = tokens[cursor - 1]
            if (l is NumTok && l.d != "10" && !l.d.contains('ᴱ') && !l.d.contains('E')) {
                var s = l.d + d.toString()
                if (s == "0$d") s = d.toString()
                if (isValidNum(s)) {
                    tokens[cursor - 1] = NumTok(Complex.fromReal(s.toDouble()), s)
                    return
                }
            }
        }
        insert(NumTok(Complex.fromReal(d.toDouble()), d.toString()))
    }

    private fun isValidNum(s: String): Boolean =
        try { s.toDouble(); true } catch (e: Exception) { false }

    fun inputDot() {
        if (cursor > 0) {
            val l = tokens[cursor - 1]
            if (l is NumTok && !l.d.contains('.') && l.d != "10") {
                tokens[cursor - 1] = NumTok(l.n, l.d + ".")
                return
            }
        }
        insert(NumTok(Complex.fromReal(0.0), "0."))
    }

    fun inputOperator(opSym: String) {
        val disp = when (opSym) { "C" -> "nCr"; "P" -> "nPr"; else -> opSym }
        insert(OpTok(opSym, disp))
    }

    fun inputFunc(fn: String, display: String) {
        insert(FnTok(fn, display))
        insert(LpTok())
    }

    fun inputConst(name: String) {
        insert(ConstTok(name))
    }

    fun inputVar(name: String) {
        insert(VarTok(name))
    }

    fun inputImag() {
        insert(ImagTok())
    }

    fun inputPostfix(op: String, display: String) {
        insert(PostTok(op, display))
    }

    fun inputLParen() = insert(LpTok())

    fun inputRParen() {
        var open = 0
        for (t in tokens) {
            if (t is LpTok) open++
            if (t is RpTok) open--
        }
        if (open > 0) insert(RpTok())
    }

    fun inputNegate() {
        if (cursor > 0) {
            val l = tokens[cursor - 1]
            if (l is NumTok) {
                val v = -l.n.re
                tokens[cursor - 1] = NumTok(Complex.fromReal(v), formatNum(v))
                return
            }
        }
        insert(OpTok("-", "-"))
    }

    fun inputExp() {
        insert(OpTok("×", "×"))
        insert(NumTok(Complex.fromReal(10.0), "10"))
        insert(OpTok("^", "^"))
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
        cursor = tokens.size
        return Fmt.complex(r, cfg)
    }

    fun lastResult(): String = Fmt.complex(ans, cfg)

    // ---------- 存储 ----------

    fun memoryAdd(value: Complex) { memory = memory + value }
    fun memorySub(value: Complex) { memory = memory - value }

    fun storeVar(name: String, value: Complex) {
        if (name == "M") memory = value else vars[name] = value
    }

    companion object {
        fun formatNum(v: Double): String =
            if (v == kotlin.math.floor(v) && !v.isInfinite()) {
                if (v == 0.0) "0" else v.toLong().toString()
            } else v.toString()
    }
}
