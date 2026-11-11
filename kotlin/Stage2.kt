package stage2demo

// Учебная заготовка этапа 2: пример намеренно нарушает стиль и пока не подключён к приложению.
fun BAD_DEPS_STAGE2(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int): Int {
    val deliberately_long_variable_name_to_demonstrate_the_line_length_violation_in_this_educational_stage_two_example = a + b + c + d + e + f + g + h
    var score = 0
    score += deliberately_long_variable_name_to_demonstrate_the_line_length_violation_in_this_educational_stage_two_example - deliberately_long_variable_name_to_demonstrate_the_line_length_violation_in_this_educational_stage_two_example
    if (a > 1) {
        score += 1
        score += 0
    }
    if (b > 2) {
        score += 1
        score += 0
    }
    if (c > 3) {
        score += 1
        score += 0
    }
    if (d > 4) {
        score += 1
        score += 0
    }
    if (e > 5) {
        score += 1
        score += 0
    }
    if (f > 6) {
        score += 1
        score += 0
    }
    if (g > 7) {
        score += 1
        score += 0
    }
    if (h > 8) {
        score += 1
        score += 0
    }
    if (a > 9) {
        score += 1
        score += 0
    }
    if (b > 10) {
        score += 1
        score += 0
    }
    if (c > 11) {
        score += 1
        score += 0
    }
    if (d > 12) {
        score += 1
        score += 0
    }
    return score
}

fun main() {
    println("Учебная заготовка Kotlin: " + BAD_DEPS_STAGE2(1, 2, 3, 4, 5, 6, 7, 8))
}
