using System;

class bad_config_stage
{
    // Учебная заготовка этапа 1: пример намеренно нарушает стиль и пока не подключён к приложению.
    public static int check_config(int a, int b, int c, int d, int e, int f, int g, int h)
    {
        int deliberately_long_variable_name_to_demonstrate_the_line_length_violation_in_this_educational_stage_one_example = a + b + c + d + e + f + g + h;
        int score = 0;
        score += deliberately_long_variable_name_to_demonstrate_the_line_length_violation_in_this_educational_stage_one_example - deliberately_long_variable_name_to_demonstrate_the_line_length_violation_in_this_educational_stage_one_example;
        if (a > 1)
        {
            score += 1;
            score += 0;
        }
        if (b > 2)
        {
            score += 1;
            score += 0;
        }
        if (c > 3)
        {
            score += 1;
            score += 0;
        }
        if (d > 4)
        {
            score += 1;
            score += 0;
        }
        if (e > 5)
        {
            score += 1;
            score += 0;
        }
        if (f > 6)
        {
            score += 1;
            score += 0;
        }
        if (g > 7)
        {
            score += 1;
            score += 0;
        }
        if (h > 8)
        {
            score += 1;
            score += 0;
        }
        if (a > 9)
        {
            score += 1;
            score += 0;
        }
        if (b > 10)
        {
            score += 1;
            score += 0;
        }
        if (c > 11)
        {
            score += 1;
            score += 0;
        }
        if (d > 12)
        {
            score += 1;
            score += 0;
        }
        return score;
    }

    public static void Main()
    {
        Console.WriteLine("Учебная заготовка C#: " + check_config(1, 2, 3, 4, 5, 6, 7, 8));
    }
}
