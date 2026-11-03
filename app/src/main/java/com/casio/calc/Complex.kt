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

    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)
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
    fun arg() = atan2(im, re)

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
        if (e.isReal) {
            val p = e.re
            if (isReal && re >= 0.0) return Complex(re.pow(p))
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

    /** 四舍五入到 12 位有效数字，消除浮点噪声 */
    fun cleaned(): Complex {
        fun c(v: Double): Double {
            if (v.isNaN() || v.isInfinite()) return v
            if (abs(v) < 1e-14) return 0.0
            val scale = 10.0.pow(12 - floor(log10(abs(v))))
            return round(v * scale) / scale
        }
        return Complex(c(re), c(im))
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