import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Preprocessor {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Preprocessor <source file>");
            return;
        }

        String source = new String(Files.readAllBytes(Paths.get(args[0])));

        // Проверка недопустимых символов (до обработки комментариев)
        Pattern forbidden = Pattern.compile("[^\\x09\\x0A\\x0D\\x20-\\x7E\\u0400-\\u04FF]");
        Matcher forbiddenMatcher = forbidden.matcher(source);
        if (forbiddenMatcher.find()) {
            System.err.println("Error: forbidden characters detected");
            return;
        }

        // Посимвольный разбор: удаляем комментарии, сохраняем строковые литералы
        StringBuilder output = new StringBuilder();
        int i = 0;
        boolean hasError = false;

        while (i < source.length()) {
            char c = source.charAt(i);

            // Строковый литерал — копируем дословно, не трогаем содержимое
            if (c == '"') {
                output.append(c);
                i++;
                while (i < source.length() && source.charAt(i) != '"') {
                    // Обработка escape-последовательностей внутри строки
                    if (source.charAt(i) == '\\' && i + 1 < source.length()) {
                        output.append(source.charAt(i));
                        output.append(source.charAt(i + 1));
                        i += 2;
                    } else {
                        output.append(source.charAt(i));
                        i++;
                    }
                }
                if (i < source.length()) {
                    output.append(source.charAt(i)); // закрывающая кавычка
                    i++;
                }
                continue;
            }

            // Символьный литерал — копируем дословно
            if (c == '\'') {
                output.append(c);
                i++;
                while (i < source.length() && source.charAt(i) != '\'') {
                    if (source.charAt(i) == '\\' && i + 1 < source.length()) {
                        output.append(source.charAt(i));
                        output.append(source.charAt(i + 1));
                        i += 2;
                    } else {
                        output.append(source.charAt(i));
                        i++;
                    }
                }
                if (i < source.length()) {
                    output.append(source.charAt(i)); // закрывающий апостроф
                    i++;
                }
                continue;
            }

            // Однострочный комментарий // — пропускаем до конца строки
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                i += 2;
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }

            // Многострочный комментарий /* ... */
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                i += 2;
                boolean closed = false;
                while (i < source.length()) {
                    if (source.charAt(i) == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                        i += 2; // пропускаем */
                        closed = true;
                        break;
                    }
                    // Вложенный /* внутри комментария — ошибка
                    if (source.charAt(i) == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                        System.err.println("Error: nested '/*' inside a block comment is not allowed");
                        hasError = true;
                        // продолжаем искать */ чтобы не зацикливаться
                    }
                    i++;
                }
                if (!closed) {
                    System.err.println("Error: unclosed multi-line comment detected");
                    hasError = true;
                }
                continue;
            }

            // Одиночный */ без открывающего /* — ошибка
            if (c == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                System.err.println("Error: unexpected end of comment '*/' without opening '/*'");
                hasError = true;
                i += 2;
                continue;
            }

            // Обычный символ — копируем
            output.append(c);
            i++;
        }

        if (hasError) {
            return;
        }

        System.err.println("No errors found");

        // Нормализация строк: убираем пробелы в начале/конце, схлопываем внутренние пробелы,
        // пропускаем пустые строки. Содержимое строковых литералов не трогаем.
        String[] lines = output.toString().split("\\n", -1);
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String normalized = normalizeSpaces(line);
            if (!normalized.isEmpty()) {
                result.append(normalized).append("\n");
            }
        }

        System.out.print(result);

        try (PrintWriter pw = new PrintWriter("out.txt")) {
            pw.print(result);
        }
        System.err.println("Result written to out.txt");
    }

    /**
     * Нормализует пробелы в строке:
     *   — обрезает пробелы слева и справа;
     *   — схлопывает последовательности пробелов/табуляций внутри строки в один пробел;
     *   — не трогает содержимое строковых литералов (внутри двойных кавычек).
     */
    private static String normalizeSpaces(String line) {
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean lastWasSpace = false;

        for (int j = 0; j < line.length(); j++) {
            char ch = line.charAt(j);

            if (inString) {
                // Внутри строкового литерала — копируем дословно
                sb.append(ch);
                if (ch == '\\' && j + 1 < line.length()) {
                    // Экранированный символ: берём следующий тоже без проверки
                    j++;
                    sb.append(line.charAt(j));
                } else if (ch == '"') {
                    inString = false;
                }
                lastWasSpace = false;
            } else {
                if (ch == '"') {
                    // Начало строкового литерала
                    inString = true;
                    lastWasSpace = false;
                    sb.append(ch);
                } else if (ch == ' ' || ch == '\t') {
                    // Пробел вне строки — запоминаем, но не добавляем сразу
                    lastWasSpace = true;
                } else {
                    // Обычный символ: если перед ним был пробел и sb не пустой — вставляем один пробел
                    if (lastWasSpace && sb.length() > 0) {
                        sb.append(' ');
                    }
                    lastWasSpace = false;
                    sb.append(ch);
                }
            }
        }

        return sb.toString();
    }
}
