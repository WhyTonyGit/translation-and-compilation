import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Лексический анализатор (лексер) для языка Java.
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

        return tokens;
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
     * Обнаруживает ошибки: двойные точки (1.2.3) и буквы после цифр (1abc).
     * @return токен CONSTANT_INT, CONSTANT_REAL или null в случае ошибки
     */
    private Token readNumber() {
        StringBuilder sb = new StringBuilder();
        boolean isReal = false;
        boolean hasError = false;
        int dotCount = 0;

        // Собираем цифры, точки, а также отслеживаем недопустимые буквы
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

        // Определяем тип: целое или вещественное
        if (isReal) {
            return new Token("CONSTANT_REAL", lexeme);
        } else {
            return new Token("CONSTANT_INT", lexeme);
        }
    }

    /**
     * Читает идентификатор или ключевое слово.
     * Проверяет в следующем порядке: логические константы, ключевые слова, иначе идентификатор.
     * @return токен CONSTANT_BOOL, KEYWORD или IDENTIFIER
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

        // Проверяем в порядке специфичности:
        // 1. Логические константы (true, false)
        if (BOOL_CONSTANTS.contains(word)) {
            return new Token("CONSTANT_BOOL", word);
        }

        // 2. Ключевые слова
        if (KEYWORDS.contains(word)) {
            return new Token("KEYWORD", word);
        }

        // 3. Остальное — идентификатор
        return new Token("IDENTIFIER", word);
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
