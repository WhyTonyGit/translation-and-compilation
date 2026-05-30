import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Главная программа для синтаксического анализа (ЛР3).
 *
 * Поддерживает три режима запуска:
 *
 *   java Main3 — читает tokens.txt (результат ЛР2), строит AST,
 *                               сохраняет AST в ast.txt
 *
 *   java Main3 source.java — запускает лексер на исходном файле, строит AST,
 *                               сохраняет tokens.txt и ast.txt
 *
 *   (fallback) — если tokens.txt не найден и аргументы не заданы,
 *                               использует встроенный демо-пример
 */
public class Main3 {

    /** Файл с токенами — результат ЛР2, вход для ЛР3. */
    private static final String TOKENS_FILE = "tokens.txt";
    /** Файл с AST — результат ЛР3, вход для ЛР4. */
    private static final String AST_FILE    = "ast.txt";

    public static void main(String[] args) {

        List<Token> tokens;

        if (args.length > 0) {
            // ── Режим 1: читаем исходный файл, запускаем лексер ─────────────
            String source;
            try {
                source = new String(Files.readAllBytes(Paths.get(args[0])));
            } catch (IOException e) {
                System.out.println("Ошибка чтения файла: " + e.getMessage());
                return;
            }

            Lexer lexer = new Lexer();
            tokens = lexer.tokenize(source);
            int lexErrors = lexer.getErrorCount();

            System.out.println("Входные данные (поток токенов из ЛР2):");
            printTokenSeq(tokens);
            System.out.println();

            if (lexErrors > 0) {
                System.out.println("Лексический анализ завершён с " + lexErrors +
                        " ошибк(ами). Синтаксический анализ прерван.");
                return;
            }
            System.out.println("Лексический анализ завершён успешно.\n");
            saveTokens(tokens);

        } else if (Files.exists(Paths.get(TOKENS_FILE))) {
            // ── Режим 2: читаем tokens.txt — результат ЛР2 ──────────────────
            System.out.println("Читаем токены из " + TOKENS_FILE + " (результат ЛР2)...");
            tokens = loadTokens(TOKENS_FILE);
            if (tokens == null) return;

            System.out.println("Входные данные (поток токенов из ЛР2):");
            printTokenSeq(tokens);
            System.out.println();

        } else {
            // ── Режим 3: fallback — встроенный демо-пример ──────────────────
            System.out.println("Файл " + TOKENS_FILE + " не найден. Используется встроенный пример.\n");
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
            Lexer lexer = new Lexer();
            tokens = lexer.tokenize(source);

            System.out.println("Входные данные (поток токенов из ЛР2):");
            printTokenSeq(tokens);
            System.out.println();

            if (lexer.getErrorCount() > 0) {
                System.out.println("Лексический анализ завершён с ошибками. Прерываем.");
                return;
            }
            saveTokens(tokens);
        }

        // ── Синтаксический анализ (ЛР3) ──────────────────────────────────────
        Parser parser = new Parser();
        ASTNode ast = parser.parse(tokens);
        int syntaxErrors = parser.getErrorCount();

        System.out.println("Результат (AST):");
        ast.printRoot();
        System.out.println();

        if (syntaxErrors == 0) {
            System.out.println("Синтаксический анализ завершён успешно. Ошибок не найдено.");
            saveAst(ast);
        } else {
            System.out.println("Синтаксический анализ завершён. Синтаксических ошибок: " +
                    syntaxErrors + ".");
        }
    }

    // ─── Вспомогательные методы ──────────────────────────────────────────────

    /** Выводит токены в консоль в формате [(TYPE, value), ...]. */
    private static void printTokenSeq(List<Token> tokens) {
        StringBuilder seq = new StringBuilder("[");
        for (int i = 0; i < tokens.size(); i++) {
            seq.append(tokens.get(i));
            if (i < tokens.size() - 1) seq.append(", ");
        }
        seq.append("]");
        System.out.println(seq);
    }

    /**
     * Сохраняет токены в tokens.txt.
     * Формат строки: TYPE\tvalue
     */
    private static void saveTokens(List<Token> tokens) {
        try (PrintWriter pw = new PrintWriter(TOKENS_FILE)) {
            for (Token t : tokens) {
                pw.println(t.type + "\t" + t.value);
            }
            System.out.println("Токены сохранены в " + TOKENS_FILE);
        } catch (IOException e) {
            System.out.println("Ошибка записи " + TOKENS_FILE + ": " + e.getMessage());
        }
    }

    /**
     * Читает tokens.txt и возвращает список токенов.
     * Формат строки: TYPE\tvalue
     * Возвращает null при ошибке.
     */
    private static List<Token> loadTokens(String path) {
        List<Token> result = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get(path));
            for (String line : lines) {
                if (line.isEmpty()) continue;
                int tab = line.indexOf('\t');
                if (tab < 0) {
                    System.out.println("Ошибка формата в " + path + ": \"" + line + "\"");
                    return null;
                }
                result.add(new Token(line.substring(0, tab), line.substring(tab + 1)));
            }
        } catch (IOException e) {
            System.out.println("Ошибка чтения " + path + ": " + e.getMessage());
            return null;
        }
        return result;
    }

    /**
     * Сохраняет AST в ast.txt (для ЛР4).
     * Использует ASTNode.serialize().
     */
    private static void saveAst(ASTNode ast) {
        try (PrintWriter pw = new PrintWriter(AST_FILE)) {
            pw.print(ast.serialize());
            System.out.println("AST сохранён в " + AST_FILE);
        } catch (IOException e) {
            System.out.println("Ошибка записи " + AST_FILE + ": " + e.getMessage());
        }
    }
}
