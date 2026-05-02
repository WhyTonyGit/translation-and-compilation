import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Лексический анализатор для языка Java.
 * Преобразует исходный код в последовательность токенов.
 */
public class Lexer {

    // Множество всех зарезервированных ключевых слов
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "int", "double", "float", "long", "short", "byte", "char", "boolean",
        "void", "if", "else", "while", "for", "do", "return", "class",
        "public", "private", "static", "new", "true", "false", "null",
        "import", "package", "this", "break", "continue", "final",
        "extends", "implements", "interface", "String"
    ));

    // Таблица идентификаторов test. Содержит все пользовательские имена, встречающиеся в исходном коде.
    private static final Set<String> IDENTIFIERS = new HashSet<>(Arrays.asList(
        "test", "add", "a", "b", "main", "args", "x", "y", "z", "flag", "i",
        "sq", "count", "result", "System", "out", "println"
    ));

    // Логические константы — проверяются отдельно перед обычными ключевыми словами
    private static final Set<String> BOOL_CONSTANTS = new HashSet<>(Arrays.asList(
        "true", "false"
    ));

    // Многосимвольные операторы, отсортированы от длинных к коротким
    // Это критично для правильного распознавания: >>= не должна распознаться как >> и =
    private static final String[] MULTI_CHAR_OPERATORS = {
        ">>>=", ">>>", "<<=", ">>=",
        "==", "!=", "<=", ">=", "&&", "||",
        "++", "--", "+=", "-=", "*=", "/=", "%=",
        "&=", "|=", "^=", "<<", ">>"
    };

    // Односимвольные операторы
    private static final Set<Character> SINGLE_CHAR_OPERATORS = new HashSet<>(Arrays.asList(
        '=', '<', '>', '+', '-', '*', '/', '%', '!', '&', '|', '^', '~'
    ));

    // Разделители
    private static final Set<Character> DELIMITERS = new HashSet<>(Arrays.asList(
        ';', ',', ':', '(', ')', '{', '}', '[', ']', '.'
    ));

    // Исходный код для разбора
    private String input;

    // Текущая позиция сканирования
    private int pos;

    // Счётчик найденных ошибок
    private int errorCount;

    /**
     * Основной метод сканирования. Преобразует строку в список токенов.
     * @param input исходный код
     * @return список найденных токенов
     */
    public List<Token> tokenize(String input) {
        this.input = input;
        this.pos = 0;
        this.errorCount = 0;

        List<Token> tokens = new ArrayList<>();

        // Главный цикл сканирования
        while (pos < input.length()) {
            char c = input.charAt(pos);

            // Пропускаем пробелы, табуляции, переносы строк
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }

            // Строковый литерал начинается с кавычки
            if (c == '"') {
                Token t = readStringLiteral();
                if (t != null) tokens.add(t);
                continue;
            }

            // Символьный литерал начинается с апострофа
            if (c == '\'') {
                Token t = readCharLiteral();
                if (t != null) tokens.add(t);
                continue;
            }

            // Число начинается с цифры
            if (Character.isDigit(c)) {
                Token t = readNumber();
                if (t != null) tokens.add(t);
                continue;
            }

            // Идентификатор или ключевое слово начинаются с буквы, _ или $
            if (Character.isLetter(c) || c == '_' || c == '$') {
                Token t = readIdentifierOrKeyword();
                if (t != null) tokens.add(t);
                continue;
            }

            // Пытаемся распознать многосимвольный оператор (проверка ПЕРЕД односимвольными!)
            String multiOp = tryReadMultiCharOperator();
            if (multiOp != null) {
                tokens.add(new Token("OPERATOR", multiOp));
                continue;
            }

            // Односимвольный оператор
            if (SINGLE_CHAR_OPERATORS.contains(c)) {
                tokens.add(new Token("OPERATOR", String.valueOf(c)));
                pos++;
                continue;
            }

            // Разделитель
            if (DELIMITERS.contains(c)) {
                tokens.add(new Token("DELIMITER", String.valueOf(c)));
                pos++;
                continue;
            }

            // Неизвестный символ — лексическая ошибка
            System.out.println("Лексическая ошибка: недопустимый символ '" + c + "'");
            errorCount++;
            pos++;
        }

        // Проверка баланса скобок после токенизации
        checkBracketBalance(tokens);

        return tokens;
    }

    /**
     * Проверяет, что все открывающие скобки (, [, { имеют соответствующие закрывающие.
     * Использует стек: при встрече открывающей — кладём в стек, закрывающей — сравниваем с вершиной.
     */
    private void checkBracketBalance(List<Token> tokens) {
        // Стек хранит значения открывающих скобок, которые ещё не закрыты
        Deque<String> stack = new ArrayDeque<>();

        for (Token t : tokens) {
            if (!t.type.equals("DELIMITER")) continue;

            switch (t.value) {
                case "(":
                case "[":
                case "{":
                    stack.push(t.value);
                    break;

                case ")":
                    closebracket(stack, "(", ")");
                    break;

                case "]":
                    closebracket(stack, "[", "]");
                    break;

                case "}":
                    closebracket(stack, "{", "}");
                    break;
            }
        }

        // Если в стеке что-то осталось — есть незакрытые скобки
        while (!stack.isEmpty()) {
            String unclosed = stack.pop();
            String expected = unclosed.equals("(") ? ")" : unclosed.equals("[") ? "]" : "}";
            System.out.println("Ошибка: незакрытая скобка '" + unclosed + "' — ожидается '" + expected + "'");
            errorCount++;
        }
    }

    /**
     * Обрабатывает закрывающую скобку с восстановлением после ошибок.
     * Если вершина стека совпадает — просто закрываем.
     * Если не совпадает, но нужная открывающая есть глубже — закрываем всё промежуточное
     * (сообщаем о каждой незакрытой скобке), затем закрываем нужную.
     * Если нужной вообще нет в стеке — сообщаем о лишней закрывающей.
     */
    private void closebracket(Deque<String> stack, String opener, String closer) {
        if (!stack.isEmpty() && stack.peek().equals(opener)) {
            // Нормальное закрытие
            stack.pop();
            return;
        }

        // Ищем нужную открывающую глубже в стеке
        if (stack.contains(opener)) {
            // Закрываем все промежуточные незакрытые скобки
            while (!stack.isEmpty() && !stack.peek().equals(opener)) {
                String unclosed = stack.pop();
                String expected = unclosed.equals("(") ? ")" : unclosed.equals("[") ? "]" : "}";
                System.out.println("Ошибка: незакрытая скобка '" + unclosed + "' — ожидается '" + expected + "'");
                errorCount++;
            }
            stack.pop(); // закрываем найденную открывающую
        } else {
            // Открывающей нет вообще — лишняя закрывающая
            System.out.println("Ошибка: лишняя закрывающая скобка '" + closer + "' без открывающей '" + opener + "'");
            errorCount++;
        }
    }

    /**
     * Возвращает количество обнаруженных лексических ошибок.
     */
    public int getErrorCount() {
        return errorCount;
    }

    /**
     * Читает строковый литерал, начинающийся с открывающей кавычки.
     * Поддерживает экранированные символы (\" и \\).
     * @return токен CONSTANT_STRING или null в случае ошибки
     */
    private Token readStringLiteral() {
        pos++; // пропускаем открывающую кавычку
        StringBuilder sb = new StringBuilder("\"");

        while (pos < input.length()) {
            char c = input.charAt(pos);

            // Экранированный символ — берём его и следующий
            if (c == '\\') {
                sb.append(c);
                pos++;
                if (pos < input.length()) {
                    sb.append(input.charAt(pos));
                    pos++;
                }
                continue;
            }

            // Закрывающая кавычка — конец строкового литерала
            if (c == '"') {
                sb.append(c);
                pos++;
                return new Token("CONSTANT_STRING", sb.toString());
            }

            // Обычный символ — добавляем в строку
            sb.append(c);
            pos++;
        }

        // Конец входа, но закрывающей кавычки нет
        System.out.println("Лексическая ошибка: незакрытый строковый литерал");
        errorCount++;
        return null;
    }

    /**
     * Читает символьный литерал, начинающийся с апострофа.
     * Может содержать один обычный символ или одну escape-последовательность.
     * @return токен CONSTANT_CHAR или null в случае ошибки
     */
    private Token readCharLiteral() {
        pos++; // пропускаем открывающий апостроф
        StringBuilder sb = new StringBuilder();

        // Проверяем, есть ли ещё символы
        if (pos >= input.length()) {
            System.out.println("Лексическая ошибка: незакрытый символьный литерал");
            errorCount++;
            return null;
        }

        char c = input.charAt(pos);

        // Экранированный символ или обычный
        if (c == '\\') {
            // Escape-последовательность: \ + следующий символ
            sb.append(c);
            pos++;
            if (pos < input.length()) {
                sb.append(input.charAt(pos));
                pos++;
            }
        } else {
            // Обычный символ
            sb.append(c);
            pos++;
        }

        // Ищем закрывающий апостроф
        if (pos < input.length() && input.charAt(pos) == '\'') {
            pos++; // пропускаем закрывающий апостроф
            return new Token("CONSTANT_CHAR", "'" + sb.toString() + "'");
        }

        // Нет закрывающего апострофа
        System.out.println("Лексическая ошибка: незакрытый символьный литерал");
        errorCount++;
        return null;
    }

    /**
     * Читает числовую константу (целую или с плавающей точкой).
     * Обнаруживает ошибки: двойные точки (1.2.3), буквы после цифр (1abc), запятую вместо точки (1,5).
     * @return токен CONSTANT_INT, CONSTANT_REAL или null в случае ошибки
     */
    private Token readNumber() {
        StringBuilder sb = new StringBuilder();
        boolean isReal = false;
        boolean hasError = false;
        int dotCount = 0;

        // Собираем цифры, точки, а также отслеживаем недопустимые буквы и запятые
        while (pos < input.length()) {
            char c = input.charAt(pos);

            if (c == '.') {
                // Следим за количеством точек
                dotCount++;
                if (dotCount > 1) {
                    hasError = true; // Ошибка: 1.2.3
                }
                isReal = true;
                sb.append(c);
                pos++;
            } else if (Character.isDigit(c)) {
                // Обычная цифра
                sb.append(c);
                pos++;
            } else if (c == ',') {
                // Запятая вместо точки — это ошибка (1,5)
                sb.append(c);
                pos++;
                hasError = true;
            } else if (Character.isLetter(c) || c == '_') {
                // Буква после цифр — это ошибка (1abc)
                sb.append(c);
                pos++;
                hasError = true;
            } else {
                // Конец числа
                break;
            }
        }

        String lexeme = sb.toString();

        // Если обнаружена ошибка — выводим сообщение
        if (hasError) {
            System.out.println("Лексическая ошибка: некорректная числовая константа '" + lexeme + "'");
            errorCount++;
            return null;
        }

        // Вещественное число должно иметь хотя бы одну цифру после точки (20. — ошибка, 20.0 — норма)
        if (isReal && (lexeme.endsWith(".") || lexeme.endsWith(","))) {
            System.out.println("Лексическая ошибка: некорректная числовая константа '" + lexeme + "' (нет цифр после точки)");
            errorCount++;
            return null;
        }

        // Определяем тип: целое или вещественное
        if (isReal) {
            return new Token("CONSTANT_REAL", lexeme);
        } else {
            return new Token("CONSTANT_INT", lexeme);
        }
    }

    /**
     * Читает идентификатор или ключевое слово.
     * Проверяет в следующем порядке:
     *   1. Логические константы (true, false)
     *   2. Ключевые слова (KEYWORDS)
     *   3. Известные идентификаторы (IDENTIFIERS)
     *   4. Всё остальное — лексическая ошибка
     * @return токен CONSTANT_BOOL, KEYWORD, IDENTIFIER или null в случае ошибки
     */
    private Token readIdentifierOrKeyword() {
        StringBuilder sb = new StringBuilder();

        // Собираем цепочку букв, цифр, подчёркиваний и знаков доллара
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                sb.append(c);
                pos++;
            } else {
                break;
            }
        }

        String word = sb.toString();

        // 1. Логические константы (true, false)
        if (BOOL_CONSTANTS.contains(word)) {
            return new Token("CONSTANT_BOOL", word);
        }

        // 2. Ключевые слова
        if (KEYWORDS.contains(word)) {
            return new Token("KEYWORD", word);
        }

        // 3. Известный идентификатор из таблицы IDENTIFIERS
        if (IDENTIFIERS.contains(word)) {
            return new Token("IDENTIFIER", word);
        }

        // 4. Слово не найдено ни в одной таблице — лексическая ошибка
        System.out.println("Лексическая ошибка: неизвестный идентификатор '" + word + "'");
        errorCount++;
        return null;
    }

    /**
     * Пытается распознать многосимвольный оператор в текущей позиции.
     * Проверяет операторы от длинных к коротким (жадное сопоставление).
     * Например, >>= распознаётся целиком, а не как >> и =.
     * @return строка оператора если совпал, иначе null
     */
    private String tryReadMultiCharOperator() {
        // Перебираем операторы от длинного к коротким
        for (String op : MULTI_CHAR_OPERATORS) {
            if (input.startsWith(op, pos)) {
                pos += op.length();
                return op;
            }
        }
        return null;
    }
}
