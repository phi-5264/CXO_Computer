package com.casio.calc

import kotlin.math.*

/** 显示格式 */
enum class DispMode { NORM, FIX, SCI, ENG }

/** 复数显示形式 */
enum class ComplexForm { RECT, POLAR }

class CalcConfig {
    var dispMode: DispMode = DispMode.NORM
    var digits: Int = 2          // FIX: 小数位, SCI: 有效位
    var angle: AngleMode = AngleMode.DEG
    var complexForm: ComplexForm = ComplexForm.RECT
}

enum class AngleMode { DEG, RAD, GRA }

object Fmt {

    /** 普通双精度数格式化（用于矩阵/方程等模式结果） */
    fun num(v: Double): String {
        if (v.isNaN()) return "ERROR"
        val t = if (abs(v) < 1e-12) 0.0 else v
        val rounded = round(t * 1e10) / 1e10
        return if (rounded == floor(rounded) && abs(rounded) < 1e15) {
            rounded.toLong().toString()
        } else rounded.toString()
    }

    /** 归一化很小的浮点误差（只处理真正的下溢，保留真实小值） */
    fun tidy(v: Double): Double {
        if (v.isNaN() || v.isInfinite()) return v
        if (abs(v) < 1e-300) return 0.0
        return v
    }

    fun real(v: Double, cfg: CalcConfig, eng: Boolean = false): String {
        val x = tidy(v)
        if (x.isNaN()) return "Math ERROR"
        if (x.isInfinite()) return if (x > 0) "∞" else "-∞"

        val neg = x < 0
        val a = abs(x)

        when (cfg.dispMode) {
            DispMode.FIX -> {
                val s = String.format("%.${cfg.digits}f", a)
                return (if (neg) "-" else "") + trim(s)
            }
            DispMode.SCI -> {
                if (a == 0.0) return "0"
                val exp = floor(log10(a)).toInt()
                val man = a / 10.0.pow(exp)
                val s = String.format("%.${max(0, cfg.digits - 1)}f", man)
                return (if (neg) "-" else "") + trim(s) + "×10^" + exp
            }
            DispMode.ENG -> {
                if (a == 0.0) return "0"
                val e = floor(log10(a)).toInt()
                val e3 = e - ((e % 3) + 3) % 3
                val man = a / 10.0.pow(e3)
                val s = String.format("%.3f", man)
                return (if (neg) "-" else "") + trim(s) + "×10^" + e3
            }
            else -> {
                // NORM
                if (a == 0.0) return "0"
                val e = floor(log10(a)).toInt()
                val s = if (a >= 1e12 || a < 1e-9) {
                    val man = a / 10.0.pow(e)
                    trim(String.format("%.9f", man)) + "×10^" + e
                } else {
                    val dec = max(0, 9 - e)
                    trim(String.format("%.${dec}f", a))
                }
                val out = if (neg) "-$s" else s
                // 只把真正的 -0 归成 0；不能按前缀判断，否则 -0.5 之类会整段变成 0
                return if (out == "-0") "0" else out
            }
        }
    }

    private fun trim(s: String): String {
        if (!s.contains('.')) return s
        var t = s.trimEnd('0')
        if (t.endsWith('.')) t = t.dropLast(1)
        return t
    }

    /** 极坐标显示：r∠θ（θ 跟随当前角度制） */
    fun polar(c: Complex, cfg: CalcConfig): String {
        val r = c.absv()
        val a = c.arg()
        val th = when (cfg.angle) {
            AngleMode.DEG -> Math.toDegrees(a)
            AngleMode.RAD -> a
            AngleMode.GRA -> a * 200.0 / PI
        }
        val unit = when (cfg.angle) {
            AngleMode.DEG -> "°"
            AngleMode.RAD -> " rad"
            AngleMode.GRA -> "g"
        }
        return real(r, cfg) + "∠" + real(th, cfg) + unit
    }

    /** 直角坐标形式：a+bi, a-bi, a, bi
     *  实/虚部是否显示按相对量级判断，避免 i×1e-15 这类真实小虚部被当成 0。 */
    fun rect(c: Complex, cfg: CalcConfig): String {
        val re = c.re
        val im = tidy(c.im)
        // 非有限值直接按实部显示（Math ERROR / ∞），避免 Inf 参与量级比较后被误判成 0
        if (!re.isFinite() || im.isNaN() || im.isInfinite()) return real(re, cfg)
        val mag = max(abs(re), abs(im))
        val eps = if (mag == 0.0) 0.0 else 1e-12 * mag
        val reZero = abs(re) <= eps
        val imZero = abs(im) <= eps
        if (imZero) return real(if (reZero) 0.0 else re, cfg)
        val imStr = real(abs(im), cfg)
        val coeff = if (imStr == "1") "" else imStr
        if (reZero) {
            return (if (im < 0) "-" else "") + coeff + "i"
        }
        val r = real(re, cfg)
        val sign = if (im < 0) "-" else "+"
        return "$r$sign${coeff}i"
    }

    /** 复数显示（按 cfg.complexForm 选主形式） */
    fun complex(c: Complex, cfg: CalcConfig): String {
        if (cfg.complexForm == ComplexForm.POLAR && !isRealPart(c)) return polar(c, cfg)
        return rect(c, cfg)
    }

    /** 虚部是否可忽略（相对量级） */
    private fun isRealPart(c: Complex): Boolean {
        if (!c.re.isFinite() || !c.im.isFinite()) return true
        val mag = max(abs(c.re), abs(c.im))
        return mag == 0.0 || abs(c.im) <= 1e-12 * mag
    }

    /** 双形式显示：主形式 + 括号内附另一种形式（仅含虚部时） */
    fun both(c: Complex, cfg: CalcConfig): String {
        if (isRealPart(c)) return rect(c, cfg)
        return if (cfg.complexForm == ComplexForm.POLAR)
            "${polar(c, cfg)}  (${rect(c, cfg)})"
        else
            "${rect(c, cfg)}  (${polar(c, cfg)})"
    }
}