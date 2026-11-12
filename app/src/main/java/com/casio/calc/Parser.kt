package com.casio.calc

import kotlin.math.*

/**
 * 递归下降求值器，消费令牌列表，输出复数结果。
 */
class Parser(private val toks: List<Tok>, private val ctx: EvalCtx) {

    private var pos = 0

    fun evaluate(): Complex {
        if (toks.isEmpty()) throw IllegalArgumentException("Syntax ERROR")
        val r = parseExpr()
        // 自动闭合未闭合的括号
        while (peek() is RpTok) next()
        // 还有剩余令牌说明式子里有多余内容（如 2)3），不能静默丢弃
        if (pos < toks.size) throw IllegalArgumentException("Syntax ERROR")
        return r.cleaned()
    }

    private fun peek(): Tok? = toks.getOrNull(pos)
    private fun next(): Tok? = toks.getOrNull(pos++)

    /** 项末尾出现过 % 时的百分数（已除以 100）；用于实现卡西欧的 100+10%=110 语义 */
    private var pctTerm: Double? = null

    private fun parseExpr(): Complex {
        var left = parseTerm()
        while (true) {
            val t = peek()
            if (t is OpTok && (t.op == "+" || t.op == "-")) {
                next()
                pctTerm = null
                val right = parseTerm()
                val pct = pctTerm.also { pctTerm = null }?.let { Complex.fromReal(it) }
                left = when {
                    // A±B% = A±A×B/100（与卡西欧一致）
                    pct != null && t.op == "+" -> left + left * pct
                    pct != null -> left - left * pct
                    t.op == "+" -> left + right
                    else -> left - right
                }
            } else break
        }
        return left
    }

    private fun parseTerm(): Complex {
        var left = parseUnary()
        while (true) {
            val t = peek()
            when {
                t is OpTok && (t.op == "×" || t.op == "÷" || t.op == "C" || t.op == "P") -> {
                    next()
                    pctTerm = null
                    val right = parseUnary()
                    left = when (t.op) {
                        "×" -> left * right
                        "÷" -> left / right
                        "C" -> nCr(left, right)
                        "P" -> nPr(left, right)
                        else -> left
                    }
                    // 右操作数末尾的 % 只属于这一项，不能让整个式子误判成 A±B%
                    // 例如 100+200×10% 应为 120，而不是 110
                    pctTerm = null
                }
                t is AngleTok -> {
                    // 极坐标：a∠θ = a * (cosθ + i·sinθ)，θ 按当前角度制
                    next()
                    pctTerm = null
                    val ang = parseUnary()
                    left = left * polarUnit(ang)
                    pctTerm = null
                }
                isImplicitStart(t) -> {
                    pctTerm = null
                    val right = parseUnary()
                    left = left * right
                    pctTerm = null
                }
                else -> break
            }
        }
        return left
    }

    private fun polarUnit(a: Complex): Complex {
        val rad = when (ctx.cfg.angle) {
            AngleMode.DEG -> realOnly(a) { it * PI / 180.0 }
            AngleMode.RAD -> realOnly(a) { it }
            AngleMode.GRA -> realOnly(a) { it * PI / 200.0 }
        }
        return Complex(cos(rad.re), sin(rad.re))
    }

    private fun isImplicitStart(t: Tok?): Boolean = when (t) {
        is NumTok, is ConstTok, is VarTok, is ImagTok, is LpTok, is FnTok -> true
        else -> false
    }

    private fun parseUnary(): Complex {
        val t = peek()
        if (t is OpTok && (t.op == "-" || t.op == "+")) {
            next()
            val v = parseUnary()
            return if (t.op == "-") -v else v
        }
        return parsePower()
    }

    private fun parsePower(): Complex {
        val base = parsePostfix()
        val t = peek()
        if (t is OpTok && t.op == "^") {
            next()
            val e = parseUnary()
            return base.pow(e)
        }
        return base
    }

    private fun parsePostfix(): Complex {
        var v = parseAtom()
        while (true) {
            val t = peek()
            if (t is PostTok) {
                next()
                v = applyPostfix(v, t.op)
            } else break
        }
        return v
    }

    private fun parseAtom(): Complex {
        val t = next() ?: throw IllegalArgumentException("Syntax ERROR")
        return when (t) {
            is NumTok -> t.n
            is ImagTok -> Complex.I
            is ConstTok -> when (t.name) {
                "pi" -> Complex.PI
                "e" -> Complex.E
                else -> throw IllegalArgumentException("Syntax ERROR")
            }
            is VarTok -> ctx.vars[t.name] ?: Complex.ZERO
            is LpTok -> {
                val v = parseExpr()
                if (peek() is RpTok) next()
                v
            }
            is FnTok -> parseFunction(t)
            else -> throw IllegalArgumentException("Syntax ERROR")
        }
    }

    private fun parseFunction(f: FnTok): Complex {
        if (peek() is LpTok) next()
        val arg = parseExpr()
        if (peek() is RpTok) next()
        return apply(f.fn, arg)
    }

    /** 三角函数特殊角归零：sin(180°)、cos(90°)、tan(180°) 等理论值为 0 的角，
     *  浮点算出来是 1e-16 量级的噪声，这里按输出归零，避免显示成 1.2×10^-16。 */
    private fun snapTrig(c: Complex): Complex =
        if (c.isReal && abs(c.re) < 1e-12) Complex.ZERO else c

    private fun apply(fn: String, a: Complex): Complex = when (fn) {
        "sin" -> snapTrig(toAng(a).sin())
        "cos" -> snapTrig(toAng(a).cos())
        "tan" -> {
            // tan(90°+k180°) 分母为 0 → Math ERROR（与卡西欧一致）
            val ia = toAng(a)
            val c = ia.cos()
            if (c.isZero()) throw IllegalArgumentException("Math ERROR")
            snapTrig(ia.sin() / c)
        }
        "asin" -> fromAng(realOnly(a) { asin(it) })
        "acos" -> fromAng(realOnly(a) { acos(it) })
        "atan" -> fromAng(realOnly(a) { atan(it) })
        "sinh" -> a.sinh()
        "cosh" -> a.cosh()
        "tanh" -> a.tanh()
        "asinh" -> realOnly(a) { asinh(it) }
        "acosh" -> realOnly(a) { acosh(it) }
        "atanh" -> realOnly(a) { atanh(it) }
        "log" -> a.log10()
        "ln" -> a.ln()
        "sqrt" -> a.sqrt()
        "cbrt" -> cbrt(a)
        "ex", "exp" -> a.exp()
        "tenx" -> (Complex.fromReal(ln(10.0)) * a).exp()
        "abs" -> Complex.fromReal(a.absv())
        // Arg 是角度，必须跟角度制换算（DEG 下 Arg(i)=90，而不是 1.571）
        "arg" -> fromAng(Complex.fromReal(a.arg()))
        "conjg" -> a.conj()
        "re" -> Complex.fromReal(a.re)
        "im" -> Complex.fromReal(a.im)
        "int" -> realOnly(a) { floor(it) }
        "frac" -> realOnly(a) { it - floor(it) }
        else -> throw IllegalArgumentException("Syntax ERROR")
    }

    private fun realOnly(a: Complex, block: (Double) -> Double): Complex {
        if (!a.isReal) throw IllegalArgumentException("Math ERROR")
        val r = block(a.re)
        // 定义域外（asin(2)、acosh(0.5)）或极点（atanh(±1)）应报 Math ERROR，
        // 不能把 NaN/∞ 当作答案继续参与后续运算
        if (r.isNaN() || r.isInfinite()) throw IllegalArgumentException("Math ERROR")
        return Complex.fromReal(r)
    }

    private fun applyPostfix(v: Complex, op: String): Complex = when (op) {
        "sq" -> v * v
        "cube" -> v * v * v
        "inv" -> Complex.ONE / v
        "percent" -> {
            val r = v / Complex.fromReal(100.0)
            pctTerm = r.re   // 记录百分数，供 A±B% 的卡西欧语义使用
            r
        }
        "fact" -> Complex.fromReal(factorialGamma(v))
        else -> v
    }

    private fun cbrt(a: Complex): Complex {
        if (a.isReal) return Complex.fromReal(cbrt(a.re))
        return a.pow(Complex.fromReal(1.0 / 3.0))
    }

    private fun toAng(a: Complex): Complex = when (ctx.cfg.angle) {
        AngleMode.DEG -> a * Complex.fromReal(PI / 180.0)
        AngleMode.RAD -> a
        AngleMode.GRA -> a * Complex.fromReal(PI / 200.0)
    }

    private fun fromAng(a: Complex): Complex = when (ctx.cfg.angle) {
        AngleMode.DEG -> a * Complex.fromReal(180.0 / PI)
        AngleMode.RAD -> a
        AngleMode.GRA -> a * Complex.fromReal(200.0 / PI)
    }

    private fun nCr(a: Complex, b: Complex): Complex {
        val n = intVal(a); val r = intVal(b)
        if (n < 0 || r < 0 || r > n) throw IllegalArgumentException("Math ERROR")
        var res = 1.0
        var rr = min(r, n - r)
        var i = 1
        while (i <= rr) {
            res = res * (n - rr + i) / i
            // 后续因子均 >=1，一旦溢出最终结果也必然溢出；不能继续空转数亿次
            if (res.isInfinite()) return Complex.fromReal(Double.POSITIVE_INFINITY)
            i++
        }
        return Complex.fromReal(res)
    }

    private fun nPr(a: Complex, b: Complex): Complex {
        val n = intVal(a); val r = intVal(b)
        if (n < 0 || r < 0 || r > n) throw IllegalArgumentException("Math ERROR")
        var res = 1.0
        var i = 0
        while (i < r) {
            res *= (n - i)
            if (res.isInfinite()) return Complex.fromReal(Double.POSITIVE_INFINITY)
            i++
        }
        return Complex.fromReal(res)
    }

    private fun intVal(c: Complex): Int {
        if (!c.isReal) throw IllegalArgumentException("Math ERROR")
        val v = round(c.re)
        if (abs(v - c.re) > 1e-9) throw IllegalArgumentException("Math ERROR")
        // toInt() 对 1e20 会饱和成 Int.MAX_VALUE，nCr/nPr 会算出假结果
        if (abs(v) > 1e9) throw IllegalArgumentException("Math ERROR")
        return v.toInt()
    }

    private fun factorialGamma(c: Complex): Double {
        if (!c.isReal) throw IllegalArgumentException("Math ERROR")
        val x = c.re
        if (x < 0 && abs(x - floor(x)) < 1e-12) throw IllegalArgumentException("Math ERROR")
        return Calc.gamma(x + 1.0)
    }
}

/** 求值上下文 */
class EvalCtx(
    val vars: Map<String, Complex>,
    val cfg: CalcConfig,
)

/** 数学工具 */
object Calc {
    fun gamma(x: Double): Double {
        if (x < 0.5) return PI / (sin(PI * x) * gamma(1.0 - x))
        // Lanczos approximation g=7
        val g = 7
        val c = doubleArrayOf(
            0.99999999999980993, 676.5203681218851, -1259.1392167224028,
            771.32342877765313, -176.61502916214059, 12.507343278686905,
            -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7
        )
        // 大参数时直接算幂会先溢出（170! 的中间量 177.5^170 ≈ 1e382），改走对数域
        if (x > 150.0) {
            var z = x - 1.0
            var la = c[0]
            for (i in 1 until g + 2) la += c[i] / (z + i)
            val t = z + g + 0.5
            return exp(0.5 * ln(2.0 * PI) + (z + 0.5) * ln(t) - t + ln(la))
        }
        var z = x - 1.0
        var a = c[0]
        val t = z + g + 0.5
        for (i in 1 until g + 2) {
            a += c[i] / (z + i)
        }
        return sqrt(2.0 * PI) * t.pow(z + 0.5) * exp(-t) * a
    }
}