package com.example.assistant

import kotlin.math.pow
import kotlin.math.sqrt

object MathEvaluator {
    fun tryExtractAndEvaluate(rawQuery: String): Double? {
        var normalized = rawQuery.lowercase()
            .replace("×", "*")
            .replace("x", "*")
            .replace("÷", "/")
            .replace("का", "*")
            .replace("percent of", "/ 100 *")
            .replace("प्रतिशत", "/ 100")
            .replace("%", "/ 100")
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("times", "*")
            .replace("divided by", "/")
            .replace("into", "*")

        // Handle "sqrt(n)"
        val sqrtRegex = Regex("""sqrt\(\s*([0-9]+(?:\.[0-9]+)?)\s*\)""")
        normalized = sqrtRegex.replace(normalized) { match ->
            val v = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            sqrt(v).toString()
        }

        // Strip common leading words while keeping math characters
        val expr = normalized.replace(Regex("""[^0-9+\-*/().^ ]"""), " ").trim()
        if (expr.isEmpty() || !expr.any { it.isDigit() }) return null
        if (!expr.any { it in charArrayOf('+', '-', '*', '/', '^') }) return null

        return try {
            Parser(expr.replace(" ", "")).parse()
        } catch (_: Exception) {
            null
        }
    }

    private class Parser(private val s: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < s.length) s[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < s.length) throw IllegalArgumentException("Unexpected: ${ch.toChar()}")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                x = when {
                    eat('+'.code) -> x + parseTerm()
                    eat('-'.code) -> x - parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                x = when {
                    eat('*'.code) -> x * parseFactor()
                    eat('/'.code) -> {
                        val denom = parseFactor()
                        if (denom == 0.0) throw ArithmeticException("Division by zero")
                        x / denom
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()
            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = s.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("Unexpected char: ${ch.toChar()}")
            }
            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
