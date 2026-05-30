import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Главная программа итогового компилятора (ЛР4).
 *
 * Поддерживает два режима запуска:
 *
 *   java Main4 — читает ast.txt (результат ЛР3), выполняет
 *                              семантический анализ и генерацию триад
 *
 *   java Main4 source.java — запускает полный конвейер ЛР1→ЛР2→ЛР3→ЛР4
 *                              и сохраняет ast.txt для последующих запусков
 *
 * Если ast.txt не найден и аргументы не заданы — используется встроенный
 * демо-пример (содержимое test.java из ЛР1).
 */
public class Main4 {

    /** Файл с AST — результат ЛР3, вход для ЛР4. */
    private static final String AST_FILE = "ast.txt";

    public static void main(String[] args) {

        ASTNode ast;

        if (args.length > 0) {
            // ── Режим 1: исходный файл → полный конвейер ЛР1→ЛР2→ЛР3 ────────
            String source;
            try {
                source = new String(Files.readAllBytes(Paths.get(args[0])));
            } catch (IOException e) {
                System.out.println("Ошибка чтения файла: " + e.getMessage());
                return;
            }
            ast = runPipeline(source);
            if (ast == null) return;

        } else if (Files.exists(Paths.get(AST_FILE))) {
            // ── Режим 2: читаем ast.txt — результат ЛР3 ─────────────────────
            separator("Шаг 1-3: Чтение AST из " + AST_FILE + " (результат ЛР3)");
            try {
                String text = new String(Files.readAllBytes(Paths.get(AST_FILE)));
                ast = ASTNode.deserialize(text);
                System.out.println("AST успешно загружен из " + AST_FILE + ".");
                System.out.println("\nАСТ:");
                ast.printRoot();
            } catch (IOException e) {
                System.out.println("Ошибка чтения " + AST_FILE + ": " + e.getMessage());
                return;
            }

        } else {
            // ── Режим 3: fallback — встроенный демо-пример ───────────────────
            System.out.println("Файл " + AST_FILE + " не найден. Используется встроенный пример.\n");
            String source =
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
            ast = runPipeline(source);
            if (ast == null) return;
        }

        // ── Шаг 4: Семантический анализ (ЛР4) ────────────────────────────────
        separator("Шаг 4: Семантический анализ (ЛР4)");

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(ast);
        int semErrors = analyzer.getErrorCount();

        System.out.println("Таблица символов:");
        analyzer.getSymbolTable().print();

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

    // ─── Полный конвейер ЛР1 → ЛР2 → ЛР3 ────────────────────────────────────

    /**
     * Запускает препроцессор, лексер и парсер на исходном коде.
     * Сохраняет ast.txt при успехе.
     * Возвращает null при любой ошибке.
     */
    private static ASTNode runPipeline(String source) {

        separator("Шаг 1: Препроцессор (ЛР1)");
        String processed = Preprocessor.preprocess(source);
        if (processed == null) {
            System.out.println("Препроцессор обнаружил ошибки. Компиляция прервана.");
            return null;
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
            return null;
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
            return null;
        }
        System.out.println("\nСинтаксический анализ завершён успешно. Ошибок не найдено.");

        // Сохраняем ast.txt для последующих запусков без пересчёта
        try {
            java.io.PrintWriter pw = new java.io.PrintWriter(AST_FILE);
            pw.print(ast.serialize());
            pw.close();
            System.out.println("AST сохранён в " + AST_FILE);
        } catch (IOException e) {
            System.out.println("Ошибка записи " + AST_FILE + ": " + e.getMessage());
        }

        return ast;
    }

    private static void separator(String title) {
        System.out.println();
        System.out.println("─── " + title + " " + "─".repeat(Math.max(0, 56 - title.length())));
    }
}
