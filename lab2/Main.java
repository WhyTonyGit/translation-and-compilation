import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Главная программа для лексического анализа.
 * Читает исходный код из файла (или использует встроенный демо-пример),
 * запускает лексер и выводит результаты в виде таблицы и последовательности.
 *
 * Примеры запуска:
 *   java Main test.txt
 *   java Main                              (без аргументов — встроенный пример)
 */
public class Main {

    public static void main(String[] args) {
        String source;

        if (args.length > 0) {
            // Читаем файл из аргумента командной строки
            try {
                source = new String(Files.readAllBytes(Paths.get(args[0])));
            } catch (IOException e) {
                System.out.println("Ошибка чтения файла: " + e.getMessage());
                return;
            }
        } else {

            source = """
                   
                    """;
        }

        // Запускаем лексический анализ
        Lexer lexer = new Lexer();
        List<Token> tokens = lexer.tokenize(source);
        int errors = lexer.getErrorCount();

        // === ФОРМИРУЕМ И ВЫВОДИМ ТАБЛИЦУ ТОКЕНОВ ===
        String col1Header = "Лексема";
        String col2Header = "Тип";

        // Вычисляем ширину колонок для красивого выравнивания
        int col1Width = Math.max(col1Header.length(), maxValueLength(tokens));
        int col2Width = Math.max(col2Header.length(), maxTypeLength(tokens));

        // Печатаем шапку таблицы
        String headerLine = padRight(col1Header, col1Width) + " | " + col2Header;
        System.out.println(headerLine);
        System.out.println(repeat("-", col1Width) + "-+-" + repeat("-", col2Width));

        // Печатаем каждый токен как строку таблицы
        for (Token t : tokens) {
            System.out.println(padRight(t.value, col1Width) + " | " + t.type);
        }

        System.out.println();

        // === ВЫВОДИМ ПОСЛЕДОВАТЕЛЬНОСТЬ ТОКЕНОВ ===
        StringBuilder seq = new StringBuilder("[");
        for (int i = 0; i < tokens.size(); i++) {
            seq.append(tokens.get(i).toString());
            if (i < tokens.size() - 1) {
                seq.append(", ");
            }
        }
        seq.append("]");
        System.out.println(seq.toString());
        System.out.println();

        // === ИТОГОВОЕ СООБЩЕНИЕ ===
        if (errors == 0) {
            System.out.println("Лексический анализ завершён успешно. Обнаружено " + tokens.size()
                    + " токенов. Ошибок не найдено.");
        } else {
            System.out.println("Лексический анализ завершён. Обнаружено " + tokens.size()
                    + " токенов. Ошибок: " + errors + ".");
        }
    }

    /**
     * Находит максимальную длину значения среди всех токенов.
     * Нужна для выравнивания первой колонки таблицы.
     */
    private static int maxValueLength(List<Token> tokens) {
        int max = 0;
        for (Token t : tokens) {
            if (t.value.length() > max) max = t.value.length();
        }
        return max;
    }

    /**
     * Находит максимальную длину типа среди всех токенов.
     * Нужна для выравнивания второй колонки таблицы.
     */
    private static int maxTypeLength(List<Token> tokens) {
        int max = 0;
        for (Token t : tokens) {
            if (t.type.length() > max) max = t.type.length();
        }
        return max;
    }

    /**
     * Добавляет пробелы справа к строке, чтобы она имела нужную ширину.
     * Используется для выравнивания колонок таблицы.
     */
    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    /**
     * Создаёт строку, состоящую из символа s, повторённого times раз.
     * Используется для рисования линий разделения в таблице.
     */
    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }
}
