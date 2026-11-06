package com.casio.calc

/** 按键定义。primary=主功能，shift=黄色上档，alpha=红色字母 */
class Key(
    val primary: String,
    val action: String,
    val shift: String = "",
    val shiftAction: String = "",
    val alpha: String = "",
    val alphaAction: String = ""
)

object Keypad {

    fun k(primary: String, action: String, shift: String = "", shiftAction: String = "",
          alpha: String = "", alphaAction: String = "") =
        Key(primary, action, shift, shiftAction, alpha, alphaAction)

    /** 行布局：每行若干按键，宽度均分 */
    val rows: List<List<Key>> = listOf(
        listOf(
            k("SHIFT", "shift"),
            k("ALPHA", "alpha"),
            k("菜单", "mode"),
            k("字母", "abc"),
            k("X", "X"),
            k("Y", "Y"),
            k("AC", "ac")
        ),
        listOf(
            k("x⁻¹", "inv"),
            k("x²", "sq", alpha = "C", alphaAction = "C"),
            k("x³", "cube", alpha = "D", alphaAction = "D"),
            k("x^□", "pow"),
            k("√", "sqrt", "³√", "cbrt"),
            k("DEL", "del")
        ),
        listOf(
            k("log", "log", "10ˣ", "tenx", "A", "A"),
            k("ln", "ln", "eˣ", "ex", "B", "B"),
            k("10ˣ", "tenx"),
            k("eˣ", "ex"),
            k("sin", "sin", "sin⁻¹", "asin", "sinh", "sinh"),
            k("cos", "cos", "cos⁻¹", "acos", "cosh", "cosh")
        ),
        listOf(
            k("tan", "tan", "tan⁻¹", "atan", "tanh", "tanh"),
            k("π", "pi", alpha = "X", alphaAction = "X"),
            k("e", "e", alpha = "Y", alphaAction = "Y"),
            k("Abs", "abs", "Arg", "arg", "Conjg", "conjg"),
            k("(", "lp"),
            k(")", "rp")
        ),
        listOf(
            k("nCr", "ncr"),
            k("nPr", "npr"),
            k("%", "percent", "x!", "fact", "M", "M"),
            k("i", "i", "∠", "angle"),
            k("(−)", "neg", alpha = "F", alphaAction = "F"),
            k("×10ˣ", "exp")
        ),
        listOf(
            k("7", "7"),
            k("8", "8"),
            k("9", "9"),
            k("×", "mul"),
            k("÷", "div")
        ),
        listOf(
            k("4", "4"),
            k("5", "5"),
            k("6", "6"),
            k("+", "add"),
            k("−", "sub")
        ),
        listOf(
            k("1", "1"),
            k("2", "2"),
            k("3", "3"),
            k("M+", "mplus"),
            k("M−", "mminus")
        ),
        listOf(
            k("0", "0"),
            k(".", "dot"),
            k("Ans", "Ans", "PreAns", "PreAns"),
            k("STO", "sto"),
            k("RCL", "rcl"),
            k("=", "eq")
        )
    )
}