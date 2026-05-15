import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Главная программа для синтаксического анализа (ЛР3).
 *
 * Выполняет полный конвейер:
 *   1. Читает исходный файл (или использует встроенный демо-пример).
 *   2. Лексический анализ — получает поток токенов (ЛР2, класс Lexer).
 *   3. Синтаксический анализ — строит AST методом рекурсивного спуска (ЛР3, класс Parser).
 *   4. Выводит входной поток токенов и результирующее AST.
 *
 * Примеры запуска:
 *   java Main3 test.java         — анализ из файла
 *   java Main3                   — встроенный демо-пример (test.java из ЛР1)
 */
public class Main3 {

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
            // Встроенный демо-пример — содержимое test.java после препроцессора (ЛР1)
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

        // ── Шаг 1: Лексический анализ (ЛР2) ──────────────────────────────────
        Lexer lexer = new Lexer();
        List<Token> tokens = lexer.tokenize(source);
        int lexErrors = lexer.getErrorCount();

        System.out.println("Входные данные (поток токенов из ЛР2):");
        StringBuilder seq = new StringBuilder("[");
        for (int i = 0; i < tokens.size(); i++) {
            seq.append(tokens.get(i));
            if (i < tokens.size() - 1) seq.append(", ");
        }
        seq.append("]");
        System.out.println(seq);
        System.out.println();

        if (lexErrors > 0) {
            System.out.println("Лексический анализ завершён с " + lexErrors +
                    " ошибк(ами). Синтаксический анализ прерван.");
            return;
        }

        // ── Шаг 2: Синтаксический анализ (ЛР3) ───────────────────────────────
        Parser parser = new Parser();
        ASTNode ast = parser.parse(tokens);
        int syntaxErrors = parser.getErrorCount();

        System.out.println("Результат (AST):");
        ast.printRoot();
        System.out.println();

        if (syntaxErrors == 0) {
            System.out.println("Синтаксический анализ завершён успешно. Ошибок не найдено.");
        } else {
            System.out.println("Синтаксический анализ завершён. Синтаксических ошибок: " +
                    syntaxErrors + ".");
        }
    }
}
