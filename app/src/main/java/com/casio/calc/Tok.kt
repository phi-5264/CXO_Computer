package com.casio.calc

/** 输入令牌（模型层），d 为显示文本 */
sealed class Tok(val d: String)

class NumTok(val n: Complex, s: String) : Tok(s)
class OpTok(val op: String, s: String) : Tok(s)
class FnTok(val fn: String, s: String) : Tok(s)
class LpTok : Tok("(")
class RpTok : Tok(")")
class ConstTok(val name: String) : Tok(if (name == "pi") "π" else "e")
class VarTok(val name: String) : Tok(name)
class ImagTok : Tok("i")
class AngleTok : Tok("∠")
class PostTok(val op: String, s: String) : Tok(s)