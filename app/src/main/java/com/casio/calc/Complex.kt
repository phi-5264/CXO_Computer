package com.casio.calc

import kotlin.math.*

/**
 * 复数与实数的统一数值载体。实数用 im == 0.0 表示。
 */
class Complex(val re: Double, val im: Double = 0.0) {

    val isReal: Boolean get() = abs(im) < 1e-12

    fun isZero(): Boolean = abs(re) < 1e-12 && abs(im) < 1e-12

    val real: Double get() = re
    val imag: Double get() = im

    operator fun plus(o: Complex): Complex {
        val r = re + o.re; val i = im + o.im
        val mr = max(abs(re), abs(o.re)); val mi = max(abs(im), abs(o.im))
        // 只抹掉"完全由浮点噪声造成"的抵消（约 4 ulp），
        // 不能用 1e-13 这种宽松阈值，否则 10^15+3-10^15 会变成 0。
        return Complex(
            if (mr > 0.0 && abs(r) < 1e-15 * mr) 0.0 else r,
            if (mi > 0.0 && abs(i) < 1e-15 * mi) 0.0 else i
        )
    }
    operator fun minus(o: Complex): Complex {
        val r = re - o.re; val i = im - o.im
        val mr = max(abs(re), abs(o.re)); val mi = max(abs(im), abs(o.im))
        return Complex(
            if (mr > 0.0 && abs(r) < 1e-15 * mr) 0.0 else r,
            if (mi > 0.0 && abs(i) < 1e-15 * mi) 0.0 else i
        )
    }
    operator fun times(o: Complex) =
        Complex(re * o.re - im * o.im, re * o.im + im * o.re)

    operator fun div(o: Complex): Complex {
        val d = o.re * o.re + o.im * o.im
        if (d == 0.0) throw ArithmeticException("Math ERROR")
        return Complex((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }

    operator fun unaryMinus() = Complex(-re, -im)

    fun conj() = Complex(re, -im)
    fun absv() = hypot(re, im)
    // 注意 -0.0：负实数的虚部若是 -0.0，atan2 会返回 -π，导致 √(-1) 算成 -i。
    // 这里把 -0.0 规范成 +0.0。
    fun arg() = atan2(if (im == 0.0) 0.0 else im, re)

    fun exp(): Complex {
        val m = kotlin.math.exp(re)
        return Complex(m * cos(im), m * sin(im))
    }

    fun ln(): Complex {
        if (isReal && re > 0.0) return Complex(kotlin.math.ln(re))
        return Complex(kotlin.math.ln(absv()), arg())
    }

    fun log10(): Complex = ln() / Complex(kotlin.math.ln(10.0))

    fun sqrt(): Complex {
        if (isReal && re >= 0.0) return Complex(kotlin.math.sqrt(re))
        val r = kotlin.math.sqrt(absv())
        val a = arg() / 2.0
        return Complex(r * cos(a), r * sin(a))
    }

    fun pow(e: Complex): Complex {
        // 实数底数：负数的整数次幂也应走实数路线（Math.pow 对负底的整数指数是正确的），
        // 否则会被 exp(ln) 复数路线带入虚假虚部，导致结果变成复数或 NaN。
        if (e.isReal && isReal) {
            // 0 的负数次幂 = 1/0，应与 1÷0 一样报 Math ERROR，而不是给出 ∞
            if (re == 0.0 && e.re < 0) throw ArithmeticException("Math ERROR")
            val r = re.pow(e.re)
            if (!r.isNaN()) return Complex(r)
        }
        return (e * ln()).exp()
    }

    fun sin(): Complex = Complex(kotlin.math.sin(re) * kotlin.math.cosh(im), kotlin.math.cos(re) * kotlin.math.sinh(im))
    fun cos(): Complex = Complex(kotlin.math.cos(re) * kotlin.math.cosh(im), -kotlin.math.sin(re) * kotlin.math.sinh(im))
    fun tan(): Complex = sin() / cos()
    fun sinh(): Complex = Complex(kotlin.math.sinh(re) * kotlin.math.cos(im), kotlin.math.cosh(re) * kotlin.math.sin(im))
    fun cosh(): Complex = Complex(kotlin.math.cosh(re) * kotlin.math.cos(im), kotlin.math.sinh(re) * kotlin.math.sin(im))
    fun tanh(): Complex = sinh() / cosh()

    fun equalsReal(v: Double): Boolean = abs(re - v) < 1e-12 && isReal

    /** 四舍五入到 12 位有效数字，消除浮点噪声。
     *  归零判断按"相对量级"：只抹掉比整体小 1e-12 以下的成分，
     *  这样 2^-50、1e-15、i×1e-15 这类真实小值不会被抹成 0。
     *  （复数路线算出的实数值通常带 1e-16 量级虚部噪声，相对判断能正确清掉。） */
    fun cleaned(): Complex {
        fun round12(v: Double): Double {
            if (v.isNaN() || v.isInfinite() || v == 0.0) return v
            val k = 12 - floor(log10(abs(v)))
            // 缩放因子超出 double 安全范围（极大/极小值）时不做舍入，避免溢出成 Inf/NaN
            if (k < 0 || k > 300) return v
            val scale = 10.0.pow(k)
            return round(v * scale) / scale
        }
        val mag = max(abs(re), abs(im))
        if (mag == 0.0 || mag.isNaN() || mag.isInfinite()) return Complex(round12(re), round12(im))
        val eps = 1e-12 * mag
        val r = if (abs(re) < eps) 0.0 else re
        val i = if (abs(im) < eps) 0.0 else im
        return Complex(round12(r), round12(i))
    }

    companion object {
        val ZERO = Complex(0.0)
        val ONE = Complex(1.0)
        val I = Complex(0.0, 1.0)
        val PI = Complex(kotlin.math.PI)
        val E = Complex(kotlin.math.E)
        fun fromReal(v: Double) = Complex(v)
    }
}