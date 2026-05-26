import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Главная программа итогового компилятора (ЛР4).
 *
 * Выполняет полный конвейер обработки тестовой программы:
 *   1. Препроцессор      (ЛР1) — удаление комментариев, нормализация пробелов
 *   2. Лексический анализ (ЛР2) — токенизация
 *   3. Синтаксический анализ (ЛР3) — построение AST
 *   4. Семантический анализ (ЛР4) — таблица символов, проверки, триады
 *
 * Примеры запуска:
 *   java Main4 test.java   — анализ из файла
 *   java Main4             — встроенный демо-пример (test.java из ЛР1)
 */
public class Main4 {

    public static void main(String[] args) {
        String source;

        if (args.length > 0) {
            try {
                source = new String(Files.readAllBytes(Paths.get(args[0])));
            } catch (IOException e) {
                System.out.println("Ошибка чтения файла: " + e.getMessage());
                return;
            }
        } else {
            // Встроенный демо-пример (содержимое test.java из ЛР1)
            source =
                "public class test {\n" +
                "static int add(int a, int b) {\n" +
                "return a + b;\n" +
                "}\n" +
                "public static void main(String[] args) {\n" +
                "int x = 10;\n" +
                "int y = 20;\n" +
                "int z = 0;\n" +
                "z = x + y;\n" +
                "boolean flag = (x < y) && (z != 0);\n" +
                "if (flag) {\n" +
                "System.out.println(\"flag is true\");\n" +
                "} else {\n" +
                "System.out.println(\"flag is false\");\n" +
                "}\n" +
                "for (int i = 0; i < 5; i++) {\n" +
                "int sq = i * i;\n" +
                "System.out.println(sq);\n" +
                "}\n" +
                "int count = 0;\n" +
                "while (count < 3) {\n" +
                "count++;\n" +
                "}\n" +
                "int result = add(x, y);\n" +
                "System.out.println(result);\n" +
                "}\n" +
                "}";
        }

        separator("Шаг 1: Препроцессор (ЛР1)");

        String processed = Preprocessor.preprocess(source);
        if (processed == null) {
            System.out.println("Препроцессор обнаружил ошибки. Компиляция прервана.");
            return;
        }
        System.out.println("Препроцессор завершён успешно. Ошибок не найдено.");

        separator("Шаг 2: Лексический анализ (ЛР2)");

        Lexer lexer = new Lexer();
        List<Token> tokens = lexer.tokenize(processed);
        int lexErrors = lexer.getErrorCount();

        System.out.println("Поток токенов:");
        StringBuilder seq = new StringBuilder("[");
        for (int i = 0; i < tokens.size(); i++) {
            seq.append(tokens.get(i));
            if (i < tokens.size() - 1) seq.append(", ");
        }
        seq.append("]");
        System.out.println(seq);

        if (lexErrors > 0) {
            System.out.println("\nЛексический анализ завершён с " + lexErrors +
                    " ошибк(ами). Синтаксический анализ прерван.");
            return;
        }
        System.out.println("\nЛексический анализ завершён успешно. Ошибок не найдено.");

        separator("Шаг 3: Синтаксический анализ (ЛР3)");

        Parser parser = new Parser();
        ASTNode ast = parser.parse(tokens);
        int syntaxErrors = parser.getErrorCount();

        System.out.println("AST:");
        ast.printRoot();

        if (syntaxErrors > 0) {
            System.out.println("\nСинтаксический анализ завершён с " + syntaxErrors +
                    " ошибк(ами). Семантический анализ прерван.");
            return;
        }
        System.out.println("\nСинтаксический анализ завершён успешно. Ошибок не найдено.");

        separator("Шаг 4: Семантический анализ (ЛР4)");

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(ast);
        int semErrors = analyzer.getErrorCount();

        // Таблица символов
        System.out.println("Таблица символов:");
        analyzer.getSymbolTable().print();

        // Последовательность триад
        System.out.println("\nПромежуточное представление (триады):");
        for (Triad t : analyzer.getTriads()) {
            System.out.println("  " + t);
        }

        System.out.println();
        if (semErrors == 0) {
            System.out.println("Семантический анализ завершён успешно. Ошибок не найдено.");
        } else {
            System.out.println("Семантический анализ завершён. Семантических ошибок: " +
                    semErrors + ".");
        }
    }

    private static void separator(String title) {
        System.out.println();
        System.out.println("─── " + title + " " + "─".repeat(Math.max(0, 56 - title.length())));
    }
}
