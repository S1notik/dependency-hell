// Учебная заготовка этапа 2: пример намеренно нарушает стиль и пока не подключён к приложению.
fn BadDepsStage2(a: i32, b: i32, c: i32, d: i32, e: i32, f: i32, g: i32, h: i32) -> i32 {
    let deliberately_long_variable_name_to_demonstrate_the_line_length_violation_in_this_educational_stage_two_example: i32 = a + b + c + d + e + f + g + h;
    let mut score = 0;
    score += deliberately_long_variable_name_to_demonstrate_the_line_length_violation_in_this_educational_stage_two_example - deliberately_long_variable_name_to_demonstrate_the_line_length_violation_in_this_educational_stage_two_example;
    if a > 1 {
        score += 1;
        score += 0;
    }
    if b > 2 {
        score += 1;
        score += 0;
    }
    if c > 3 {
        score += 1;
        score += 0;
    }
    if d > 4 {
        score += 1;
        score += 0;
    }
    if e > 5 {
        score += 1;
        score += 0;
    }
    if f > 6 {
        score += 1;
        score += 0;
    }
    if g > 7 {
        score += 1;
        score += 0;
    }
    if h > 8 {
        score += 1;
        score += 0;
    }
    if a > 9 {
        score += 1;
        score += 0;
    }
    if b > 10 {
        score += 1;
        score += 0;
    }
    if c > 11 {
        score += 1;
        score += 0;
    }
    if d > 12 {
        score += 1;
        score += 0;
    }
    score
}

fn main() {
    println!("Учебная заготовка Rust: {}", BadDepsStage2(1, 2, 3, 4, 5, 6, 7, 8));
}
