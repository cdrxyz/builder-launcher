package xyz.cdr.builderlauncher.commands

import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.truncate

object Calculator {
    fun preview(raw: String): String? {
        if (!looksLikeMath(raw)) return null
        val tokens = tokenize(raw) ?: return null
        if (tokens.size < 2) return null
        val value = parse(tokens) ?: return null
        val formatted = formatNumber(value) ?: return null
        return formatted.takeIf { it != raw.trim() }
    }

    fun commit(raw: String): String? = preview(raw)

    private val FUNCS: Map<String, (List<Double>) -> Double?> = mapOf(
        "abs" to { args -> args.singleOrNull()?.let(::abs) },
        "acos" to { args -> args.singleOrNull()?.let(::acos) },
        "asin" to { args -> args.singleOrNull()?.let(::asin) },
        "atan" to { args -> args.singleOrNull()?.let(::atan) },
        "atan2" to { args -> if (args.size == 2) atan2(args[0], args[1]) else null },
        "cbrt" to { args -> args.singleOrNull()?.let(::cbrt) },
        "ceil" to { args -> args.singleOrNull()?.let(::ceil) },
        "cos" to { args -> args.singleOrNull()?.let(::cos) },
        "exp" to { args -> args.singleOrNull()?.let(::exp) },
        "floor" to { args -> args.singleOrNull()?.let(::floor) },
        "ln" to { args -> args.singleOrNull()?.let(::ln) },
        "log" to { args -> args.singleOrNull()?.let(::log10) },
        "log10" to { args -> args.singleOrNull()?.let(::log10) },
        "log2" to { args -> args.singleOrNull()?.let(::log2) },
        "max" to { args -> if (args.isEmpty()) null else args.reduce(::max) },
        "min" to { args -> if (args.isEmpty()) null else args.reduce(::min) },
        "pow" to { args -> if (args.size == 2) args[0].pow(args[1]) else null },
        "round" to { args -> args.singleOrNull()?.let(::round) },
        "sign" to { args -> args.singleOrNull()?.let(::sign) },
        "sin" to { args -> args.singleOrNull()?.let(::sin) },
        "sqrt" to { args -> args.singleOrNull()?.let(::sqrt) },
        "tan" to { args -> args.singleOrNull()?.let(::tan) },
        "trunc" to { args -> args.singleOrNull()?.let(::truncate) },
        "logn" to { args -> if (args.size == 2) log(args[0], args[1]) else null },
    )

    private val CONSTS = mapOf(
        "pi" to Math.PI,
        "e" to Math.E,
        "tau" to Math.PI * 2,
    )

    private val NAMED = Regex(
        """\b(sqrt|cbrt|atan2|floor|ceil|trunc|round|abs|sin|cos|tan|log2|log10|asin|acos|atan|exp|log|ln|max|min|pow|sign)\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun looksLikeMath(raw: String): Boolean {
        val s = raw.trim()
        if (s.length < 2 || s.length > 240) return false
        if ('\n' in s) return false
        if (s.first() in "/@#*?\$") return false
        if (s.first() == '-' && s.getOrNull(1)?.isDigit() != true && s.getOrNull(1) != '(' && s.getOrNull(1) != '.') {
            return false
        }
        if (s.first() == '+' && s.drop(1).any { it.isLetter() }) return false
        if (Regex("""[?!:;@#$&={}\[\]\\|<>"'`]""").containsMatchIn(s)) return false
        if (Regex("[A-Za-z]{4,}").containsMatchIn(s) && !NAMED.containsMatchIn(s)) return false
        return Regex("""[+\-*/^×÷∙⋅()]""").containsMatchIn(s) || NAMED.containsMatchIn(s)
    }

    private sealed class Tok {
        data class Num(val value: Double) : Tok()
        data class Id(val name: String) : Tok()
        data class Sym(val ch: Char) : Tok()
    }

    private fun tokenize(raw: String): List<Tok>? {
        val s = raw.trim()
            .replace('×', '*')
            .replace('∙', '*')
            .replace('⋅', '*')
            .replace('÷', '/')
            .replace("π", "pi")
            .replace("√", "sqrt")
        val out = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == ',' || "+-*/^()".contains(c) -> {
                    out += Tok.Sym(c)
                    i++
                }
                c.isDigit() || (c == '.' && i + 1 < s.length && s[i + 1].isDigit()) -> {
                    val start = i
                    while (i < s.length && s[i].isDigit()) i++
                    if (i < s.length && s[i] == '.') {
                        i++
                        while (i < s.length && s[i].isDigit()) i++
                    }
                    if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                        val e = i
                        i++
                        if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                        if (i >= s.length || !s[i].isDigit()) return null
                        while (i < s.length && s[i].isDigit()) i++
                        if (i == e + 1) return null
                    }
                    val n = s.substring(start, i).toDoubleOrNull() ?: return null
                    if (!n.isFinite()) return null
                    out += Tok.Num(n)
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                    out += Tok.Id(s.substring(start, i).lowercase(Locale.US))
                }
                else -> return null
            }
        }
        return out
    }

    private class Parser(
        private val tokens: List<Tok>,
        private val funcs: Map<String, (List<Double>) -> Double?>,
        private val consts: Map<String, Double>,
    ) {
        private var i = 0

        fun value(): Double? {
            val n = add() ?: return null
            if (i != tokens.size) return null
            return n.takeIf { it.isFinite() }
        }

        private fun peek(): Tok? = tokens.getOrNull(i)

        private fun eatSym(ch: Char): Boolean {
            val t = peek()
            return if (t is Tok.Sym && t.ch == ch) {
                i++
                true
            } else {
                false
            }
        }

        private fun primary(): Double? {
            when (val t = peek()) {
                is Tok.Num -> {
                    i++
                    return t.value
                }
                is Tok.Id -> {
                    i++
                    val next = peek()
                    if (next !is Tok.Sym || next.ch != '(') {
                        return consts[t.name]
                    }
                    val fn = funcs[t.name] ?: return null
                    if (!eatSym('(')) return null
                    val args = ArrayList<Double>()
                    if (peek() !is Tok.Sym || (peek() as Tok.Sym).ch != ')') {
                        val first = add() ?: return null
                        args += first
                        while (eatSym(',')) {
                            val nextArg = add() ?: return null
                            args += nextArg
                        }
                    }
                    if (!eatSym(')')) return null
                    return fn(args)
                }
                is Tok.Sym -> {
                    if (t.ch != '(') return null
                    i++
                    val inner = add() ?: return null
                    if (!eatSym(')')) return null
                    return inner
                }
                null -> return null
            }
        }

        private fun unary(): Double? {
            val t = peek()
            if (t is Tok.Sym && t.ch == '+') {
                i++
                return unary()
            }
            if (t is Tok.Sym && t.ch == '-') {
                i++
                val v = unary() ?: return null
                return -v
            }
            return primary()
        }

        private fun pow(): Double? {
            val base = unary() ?: return null
            return if (eatSym('^')) {
                val exp = pow() ?: return null
                base.pow(exp)
            } else {
                base
            }
        }

        private fun mul(): Double? {
            var v = pow() ?: return null
            while (true) {
                val t = peek()
                if (t !is Tok.Sym || (t.ch != '*' && t.ch != '/')) break
                i++
                val r = pow() ?: return null
                v = if (t.ch == '*') v * r else v / r
            }
            return v
        }

        private fun add(): Double? {
            var v = mul() ?: return null
            while (true) {
                val t = peek()
                if (t !is Tok.Sym || (t.ch != '+' && t.ch != '-')) break
                i++
                val r = mul() ?: return null
                v = if (t.ch == '+') v + r else v - r
            }
            return v
        }
    }

    private fun parse(tokens: List<Tok>): Double? = Parser(tokens, FUNCS, CONSTS).value()

    private fun formatNumber(n: Double): String? {
        if (!n.isFinite()) return null
        if (n == 0.0) return "0"
        val mag = abs(n)
        val asLong = n.toLong()
        if (asLong.toDouble() == n && mag < 1e15) return asLong.toString()
        if (mag < 1e-6 || mag >= 1e12) {
            return String.format(Locale.US, "%.8e", n)
                .replace(Regex("""\.?0+e"""), "e")
                .replace("e+", "e")
        }
        return String.format(Locale.US, "%.12g", n)
    }
}
