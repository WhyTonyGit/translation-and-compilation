import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Синтаксический анализатор подмножества языка Java.
 *
 * Реализует метод рекурсивного спуска: каждая синтаксическая конструкция
 * представлена отдельным методом. Принимает список токенов из ЛР2 и строит AST.
 *
 * Поддерживаемые конструкции (на основе test.java из ЛР1):
 *   — объявление класса с модификаторами
 *   — объявление методов и параметров
 *   — объявление переменных с инициализацией
 *   — операторы: присваивание, if-else, for, while, return
 *   — выражения: арифметика, сравнения, логика, вызовы методов, цепочки полей
 *   — постфиксные операторы: ++, --
 *
 * При обнаружении синтаксической ошибки выводит сообщение с позицией
 * и ожидаемой конструкцией, затем пытается продолжить разбор.
 */
public class Parser {

    // Ключевые слова, допустимые как тип данных
    private static final Set<String> TYPE_KEYWORDS = new HashSet<>(Arrays.asList(
        "int", "double", "float", "long", "short", "byte", "char", "boolean", "void", "String"
    ));

    // Модификаторы методов и полей класса
    private static final Set<String> MODIFIERS = new HashSet<>(Arrays.asList(
        "public", "private", "protected", "static", "final", "abstract"
    ));

    private List<Token> tokens;
    private int pos;
    private int errorCount;

    /**
     * Запускает синтаксический разбор списка токенов.
     * @param tokens список токенов от лексического анализатора (ЛР2)
     * @return корень AST — узел Program
     */
    public ASTNode parse(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
        this.errorCount = 0;
        return parseProgram();
    }

    /** Возвращает количество найденных синтаксических ошибок. */
    public int getErrorCount() {
        return errorCount;
    }

    // ==================== ПРАВИЛА ГРАММАТИКИ ====================

    /**
     * program → class_decl
     */
    private ASTNode parseProgram() {
        ASTNode program = new ASTNode("Program");
        if (pos < tokens.size()) {
            ASTNode cls = parseClassDecl();
            if (cls != null) program.addChild(cls);
        }
        if (pos < tokens.size()) {
            error("Неожиданные токены после объявления класса: '" + peek().value + "'");
        }
        return program;
    }

    /**
     * class_decl → modifier* 'class' IDENTIFIER '{' member* '}'
     */
    private ASTNode parseClassDecl() {
        List<String> mods = consumeModifiers();

        expect("KEYWORD", "class");

        Token nameToken = expectType("IDENTIFIER");
        String className = (nameToken != null) ? nameToken.value : "?";

        ASTNode node = new ASTNode("ClassDecl", className);
        if (!mods.isEmpty()) {
            node.addChild(new ASTNode("Modifiers", String.join(" ", mods)));
        }

        expect("DELIMITER", "{");

        while (pos < tokens.size() && !check("DELIMITER", "}")) {
            ASTNode member = parseMember();
            if (member != null) node.addChild(member);
        }

        expect("DELIMITER", "}");
        return node;
    }

    /**
     * member → modifier* type ('[' ']')? IDENTIFIER ( method_tail | field_tail )
     * method_tail → '(' params ')' block
     * field_tail  → ('=' expr)? ';'
     */
    private ASTNode parseMember() {
        List<String> mods = consumeModifiers();

        if (!isType()) {
            error("Ожидался тип данных для объявления члена класса, встречено '" +
                    (peek() != null ? peek().value : "EOF") + "'");
            skipUntil("}");
            return null;
        }

        String typeName = consume().value;
        // Массивный тип: String[]
        if (check("DELIMITER", "[")) {
            consume();
            expect("DELIMITER", "]");
            typeName += "[]";
        }

        if (peek() == null || !peek().type.equals("IDENTIFIER")) {
            error("Ожидалось имя члена класса после типа '" + typeName + "'");
            skipUntil(";");
            return null;
        }
        String memberName = consume().value;

        if (check("DELIMITER", "(")) {
            // ── Метод ──
            ASTNode method = new ASTNode("MethodDecl", memberName);
            if (!mods.isEmpty()) {
                method.addChild(new ASTNode("Modifiers", String.join(" ", mods)));
            }
            method.addChild(new ASTNode("ReturnType", typeName));

            consume(); // (
            method.addChild(parseParams());
            expect("DELIMITER", ")");
            method.addChild(parseBlock());
            return method;
        } else {
            // ── Поле класса ──
            ASTNode field = new ASTNode("FieldDecl", memberName);
            if (!mods.isEmpty()) {
                field.addChild(new ASTNode("Modifiers", String.join(" ", mods)));
            }
            field.addChild(new ASTNode("Type", typeName));
            if (check("OPERATOR", "=")) {
                consume();
                ASTNode init = new ASTNode("Init");
                init.addChild(parseExpr());
                field.addChild(init);
            }
            expect("DELIMITER", ";");
            return field;
        }
    }

    /**
     * params → ε | param (',' param)*
     */
    private ASTNode parseParams() {
        ASTNode params = new ASTNode("Params");
        if (!check("DELIMITER", ")")) {
            params.addChild(parseParam());
            while (check("DELIMITER", ",")) {
                consume();
                params.addChild(parseParam());
            }
        }
        return params;
    }

    /**
     * param → type ('[' ']')? IDENTIFIER
     */
    private ASTNode parseParam() {
        if (!isType()) {
            error("Ожидался тип параметра, встречено '" +
                    (peek() != null ? peek().value : "EOF") + "'");
            return new ASTNode("Param", "?");
        }
        String typeName = consume().value;
        if (check("DELIMITER", "[")) {
            consume();
            expect("DELIMITER", "]");
            typeName += "[]";
        }
        String paramName = "?";
        if (peek() != null && peek().type.equals("IDENTIFIER")) {
            paramName = consume().value;
        } else {
            error("Ожидалось имя параметра после типа '" + typeName + "'");
        }
        ASTNode param = new ASTNode("Param", paramName);
        param.addChild(new ASTNode("Type", typeName));
        return param;
    }

    /**
     * block → '{' stmt* '}'
     */
    private ASTNode parseBlock() {
        ASTNode block = new ASTNode("Body");
        if (!check("DELIMITER", "{")) {
            error("Ожидалась '{' для открытия блока, встречено '" +
                    (peek() != null ? peek().value : "EOF") + "'");
            return block;
        }
        consume(); // {
        while (pos < tokens.size() && !check("DELIMITER", "}")) {
            ASTNode stmt = parseStmt();
            if (stmt != null) block.addChild(stmt);
        }
        if (pos >= tokens.size()) {
            error("Незакрытый блок: ожидалась '}'");
        } else {
            consume(); // }
        }
        return block;
    }

    /**
     * stmt → varDeclStmt | ifStmt | forStmt | whileStmt | returnStmt | exprStmt
     *
     * Выбор ветки определяется по первому токену:
     *   тип-ключевое-слово → varDeclStmt
     *   'if'               → ifStmt
     *   'for'              → forStmt
     *   'while'            → whileStmt
     *   'return'           → returnStmt
     *   иначе              → exprStmt
     */
    private ASTNode parseStmt() {
        if (peek() == null) {
            error("Неожиданный конец файла внутри блока");
            return null;
        }

        if (isType())                        return parseVarDeclStmt();
        if (check("KEYWORD", "if"))          return parseIfStmt();
        if (check("KEYWORD", "for"))         return parseForStmt();
        if (check("KEYWORD", "while"))       return parseWhileStmt();
        if (check("KEYWORD", "return"))      return parseReturnStmt();

        // Оператор-выражение: присваивание, вызов метода, постфикс и т.д.
        if (peek().type.equals("IDENTIFIER")     ||
            peek().type.equals("CONSTANT_INT")   ||
            peek().type.equals("CONSTANT_REAL")  ||
            peek().type.equals("CONSTANT_BOOL")  ||
            peek().type.equals("CONSTANT_STRING")||
            check("DELIMITER", "(")              ||
            check("OPERATOR",  "++")             ||
            check("OPERATOR",  "--")             ||
            check("OPERATOR",  "!")              ||
            check("OPERATOR",  "-")) {
            return parseExprStmt();
        }

        // Неизвестный токен — сообщаем об ошибке и пропускаем
        error("Неожиданный токен '" + peek().value + "' (тип: " + peek().type + ")");
        consume();
        return null;
    }

    /**
     * varDeclStmt → type ('[' ']')? IDENTIFIER ('=' expr)? ';'
     */
    private ASTNode parseVarDeclStmt() {
        ASTNode decl = parseVarDecl();
        expect("DELIMITER", ";");
        return decl;
    }

    /**
     * varDecl → type ('[' ']')? IDENTIFIER ('=' expr)?
     * (без точки с запятой — используется также в инициализаторе for)
     */
    private ASTNode parseVarDecl() {
        String typeName = consume().value;
        if (check("DELIMITER", "[")) {
            consume();
            expect("DELIMITER", "]");
            typeName += "[]";
        }

        String name = "?";
        if (peek() != null && peek().type.equals("IDENTIFIER")) {
            name = consume().value;
        } else {
            error("Ожидалось имя переменной после типа '" + typeName + "'");
        }

        ASTNode decl = new ASTNode("VarDecl", name);
        decl.addChild(new ASTNode("Type", typeName));

        if (check("OPERATOR", "=")) {
            consume();
            ASTNode init = new ASTNode("Init");
            init.addChild(parseExpr());
            decl.addChild(init);
        }

        return decl;
    }

    /**
     * ifStmt → 'if' '(' expr ')' block ('else' block)?
     */
    private ASTNode parseIfStmt() {
        consume(); // if
        ASTNode node = new ASTNode("IfStmt");

        expect("DELIMITER", "(");
        ASTNode cond = new ASTNode("Condition");
        cond.addChild(parseExpr());
        node.addChild(cond);
        expect("DELIMITER", ")");

        ASTNode then = parseBlock();
        then.type = "ThenBlock";
        node.addChild(then);

        if (check("KEYWORD", "else")) {
            consume();
            ASTNode els = parseBlock();
            els.type = "ElseBlock";
            node.addChild(els);
        }

        return node;
    }

    /**
     * forStmt → 'for' '(' forInit ';' expr? ';' expr? ')' block
     * forInit → varDecl | expr | ε
     */
    private ASTNode parseForStmt() {
        consume(); // for
        ASTNode node = new ASTNode("ForStmt");

        expect("DELIMITER", "(");

        // Инициализация
        ASTNode init = new ASTNode("Init");
        if (!check("DELIMITER", ";")) {
            if (isType()) {
                init.addChild(parseVarDecl()); // varDecl без ';'
            } else {
                init.addChild(parseExpr());
            }
        }
        node.addChild(init);
        expect("DELIMITER", ";");

        // Условие продолжения
        ASTNode cond = new ASTNode("Condition");
        if (!check("DELIMITER", ";")) {
            cond.addChild(parseExpr());
        }
        node.addChild(cond);
        expect("DELIMITER", ";");

        // Шаг обновления
        ASTNode update = new ASTNode("Update");
        if (!check("DELIMITER", ")")) {
            update.addChild(parseExpr());
        }
        node.addChild(update);

        expect("DELIMITER", ")");
        node.addChild(parseBlock());
        return node;
    }

    /**
     * whileStmt → 'while' '(' expr ')' block
     */
    private ASTNode parseWhileStmt() {
        consume(); // while
        ASTNode node = new ASTNode("WhileStmt");

        expect("DELIMITER", "(");
        ASTNode cond = new ASTNode("Condition");
        cond.addChild(parseExpr());
        node.addChild(cond);
        expect("DELIMITER", ")");

        node.addChild(parseBlock());
        return node;
    }

    /**
     * returnStmt → 'return' expr? ';'
     */
    private ASTNode parseReturnStmt() {
        consume(); // return
        ASTNode node = new ASTNode("ReturnStmt");
        if (!check("DELIMITER", ";")) {
            node.addChild(parseExpr());
        }
        expect("DELIMITER", ";");
        return node;
    }

    /**
     * exprStmt → expr ';'
     */
    private ASTNode parseExprStmt() {
        ASTNode expr = parseExpr();
        ASTNode stmt = new ASTNode("ExprStmt");
        stmt.addChild(expr);
        expect("DELIMITER", ";");
        return stmt;
    }

    // ==================== ВЫРАЖЕНИЯ (рекурсивный спуск) ====================

    /**
     * expr → logicalOr ('=' expr)?
     *
     * Присваивание правоассоциативно: a = b = c разбирается как a = (b = c).
     */
    private ASTNode parseExpr() {
        ASTNode left = parseLogicalOr();
        if (check("OPERATOR", "=")) {
            consume();
            ASTNode right = parseExpr();
            ASTNode assign = new ASTNode("Assign");
            assign.addChild(left);
            assign.addChild(right);
            return assign;
        }
        return left;
    }

    /**
     * logicalOr → logicalAnd ('||' logicalAnd)*
     */
    private ASTNode parseLogicalOr() {
        ASTNode left = parseLogicalAnd();
        while (check("OPERATOR", "||")) {
            String op = consume().value;
            ASTNode right = parseLogicalAnd();
            ASTNode node = new ASTNode("BinaryExpr", op);
            node.addChild(left);
            node.addChild(right);
            left = node;
        }
        return left;
    }

    /**
     * logicalAnd → equality ('&&' equality)*
     */
    private ASTNode parseLogicalAnd() {
        ASTNode left = parseEquality();
        while (check("OPERATOR", "&&")) {
            String op = consume().value;
            ASTNode right = parseEquality();
            ASTNode node = new ASTNode("BinaryExpr", op);
            node.addChild(left);
            node.addChild(right);
            left = node;
        }
        return left;
    }

    /**
     * equality → relational (('==' | '!=') relational)*
     */
    private ASTNode parseEquality() {
        ASTNode left = parseRelational();
        while (check("OPERATOR", "==") || check("OPERATOR", "!=")) {
            String op = consume().value;
            ASTNode right = parseRelational();
            ASTNode node = new ASTNode("BinaryExpr", op);
            node.addChild(left);
            node.addChild(right);
            left = node;
        }
        return left;
    }

    /**
     * relational → additive (('<' | '>' | '<=' | '>=') additive)*
     */
    private ASTNode parseRelational() {
        ASTNode left = parseAdditive();
        while (check("OPERATOR", "<") || check("OPERATOR", ">") ||
               check("OPERATOR", "<=") || check("OPERATOR", ">=")) {
            String op = consume().value;
            ASTNode right = parseAdditive();
            ASTNode node = new ASTNode("BinaryExpr", op);
            node.addChild(left);
            node.addChild(right);
            left = node;
        }
        return left;
    }

    /**
     * additive → multiplicative (('+' | '-') multiplicative)*
     */
    private ASTNode parseAdditive() {
        ASTNode left = parseMultiplicative();
        while (check("OPERATOR", "+") || check("OPERATOR", "-")) {
            String op = consume().value;
            ASTNode right = parseMultiplicative();
            ASTNode node = new ASTNode("BinaryExpr", op);
            node.addChild(left);
            node.addChild(right);
            left = node;
        }
        return left;
    }

    /**
     * multiplicative → unary (('*' | '/' | '%') unary)*
     */
    private ASTNode parseMultiplicative() {
        ASTNode left = parseUnary();
        while (check("OPERATOR", "*") || check("OPERATOR", "/") || check("OPERATOR", "%")) {
            String op = consume().value;
            ASTNode right = parseUnary();
            ASTNode node = new ASTNode("BinaryExpr", op);
            node.addChild(left);
            node.addChild(right);
            left = node;
        }
        return left;
    }

    /**
     * unary → ('!' | '-' | '++' | '--') unary | postfix
     */
    private ASTNode parseUnary() {
        if (check("OPERATOR", "!") || check("OPERATOR", "-") ||
            check("OPERATOR", "++") || check("OPERATOR", "--")) {
            String op = consume().value;
            ASTNode operand = parseUnary();
            ASTNode node = new ASTNode("UnaryExpr", op);
            node.addChild(operand);
            return node;
        }
        return parsePostfix();
    }

    /**
     * postfix → primary ('++' | '--')?
     */
    private ASTNode parsePostfix() {
        ASTNode node = parsePrimary();
        if (check("OPERATOR", "++") || check("OPERATOR", "--")) {
            String op = consume().value;
            ASTNode postfix = new ASTNode("PostfixExpr", op);
            postfix.addChild(node);
            return postfix;
        }
        return node;
    }

    /**
     * primary → literal | '(' expr ')' | identifier_chain
     *
     * identifier_chain → IDENTIFIER ('.' IDENTIFIER)* ('(' args ')')?
     *   Примеры: x, add(a,b), System.out.println(s)
     */
    private ASTNode parsePrimary() {
        Token t = peek();
        if (t == null) {
            error("Неожиданный конец выражения");
            return new ASTNode("Error");
        }

        // Целочисленный литерал
        if (t.type.equals("CONSTANT_INT")) {
            return new ASTNode("IntLiteral", consume().value);
        }
        // Вещественный литерал
        if (t.type.equals("CONSTANT_REAL")) {
            return new ASTNode("RealLiteral", consume().value);
        }
        // Строковый литерал
        if (t.type.equals("CONSTANT_STRING")) {
            return new ASTNode("StringLiteral", consume().value);
        }
        // Булевый литерал
        if (t.type.equals("CONSTANT_BOOL")) {
            return new ASTNode("BoolLiteral", consume().value);
        }

        // Выражение в скобках: '(' expr ')'
        if (check("DELIMITER", "(")) {
            consume(); // (
            ASTNode inner = parseExpr();
            expect("DELIMITER", ")");
            ASTNode group = new ASTNode("GroupExpr");
            group.addChild(inner);
            return group;
        }

        // Идентификатор, возможно с цепочкой полей/методов: System.out.println(...)
        if (t.type.equals("IDENTIFIER")) {
            consume();
            StringBuilder name = new StringBuilder(t.value);

            // Поглощаем цепочку .field или .method
            while (check("DELIMITER", ".")) {
                consume(); // .
                if (peek() == null || !peek().type.equals("IDENTIFIER")) {
                    error("Ожидался идентификатор после '.'");
                    break;
                }
                name.append(".").append(consume().value);
            }

            // Если за цепочкой стоит '(' — это вызов метода
            if (check("DELIMITER", "(")) {
                consume(); // (
                ASTNode call = new ASTNode("MethodCall", name.toString());
                // Аргументы вызова
                if (!check("DELIMITER", ")")) {
                    call.addChild(parseExpr());
                    while (check("DELIMITER", ",")) {
                        consume();
                        call.addChild(parseExpr());
                    }
                }
                expect("DELIMITER", ")");
                return call;
            }

            // Просто идентификатор или обращение к полю
            return new ASTNode("Identifier", name.toString());
        }

        // Неожиданный токен в позиции первичного выражения
        error("Неожиданный токен '" + t.value + "' (тип: " + t.type + ") в выражении");
        consume(); // пропускаем, чтобы не зациклиться
        return new ASTNode("Error", t.value);
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    /** Возвращает текущий токен без потребления (null если конец). */
    private Token peek() {
        return (pos < tokens.size()) ? tokens.get(pos) : null;
    }

    /** Потребляет и возвращает текущий токен. */
    private Token consume() {
        return tokens.get(pos++);
    }

    /** true, если текущий токен совпадает по типу и значению. */
    private boolean check(String type, String value) {
        Token t = peek();
        return t != null && t.type.equals(type) && t.value.equals(value);
    }

    /**
     * Потребляет токен с ожидаемым типом и значением.
     * При несовпадении регистрирует ошибку и НЕ продвигает позицию
     * (чтобы вышестоящий код мог попытаться продолжить разбор).
     */
    private Token expect(String type, String value) {
        Token t = peek();
        if (t == null) {
            error("Ожидался '" + value + "', достигнут конец файла");
            return null;
        }
        if (!t.type.equals(type) || !t.value.equals(value)) {
            error("Ожидался '" + value + "' (" + type + "), встречено '" +
                    t.value + "' (" + t.type + ")");
            return null;
        }
        return consume();
    }

    /**
     * Потребляет токен с ожидаемым типом (значение не проверяется).
     * При несовпадении регистрирует ошибку.
     */
    private Token expectType(String type) {
        Token t = peek();
        if (t == null) {
            error("Ожидался токен типа " + type + ", достигнут конец файла");
            return null;
        }
        if (!t.type.equals(type)) {
            error("Ожидался токен типа " + type + ", встречено '" +
                    t.value + "' (" + t.type + ")");
            return null;
        }
        return consume();
    }

    /** true, если текущий токен является ключевым словом-типом данных. */
    private boolean isType() {
        Token t = peek();
        return t != null && t.type.equals("KEYWORD") && TYPE_KEYWORDS.contains(t.value);
    }

    /** true, если текущий токен является модификатором. */
    private boolean isModifier() {
        Token t = peek();
        return t != null && t.type.equals("KEYWORD") && MODIFIERS.contains(t.value);
    }

    /** Потребляет подряд идущие модификаторы и возвращает их в виде списка. */
    private List<String> consumeModifiers() {
        List<String> mods = new ArrayList<>();
        while (isModifier()) {
            mods.add(consume().value);
        }
        return mods;
    }

    /** Пропускает токены вплоть до первого вхождения токена с данным значением. */
    private void skipUntil(String value) {
        while (pos < tokens.size() && !tokens.get(pos).value.equals(value)) {
            pos++;
        }
    }

    /** Выводит сообщение о синтаксической ошибке с позицией в потоке токенов. */
    private void error(String message) {
        String tokenInfo = (pos < tokens.size())
            ? "позиция " + pos + ", токен '" + tokens.get(pos).value + "'"
            : "конец файла";
        System.out.println("Синтаксическая ошибка [" + tokenInfo + "]: " + message);
        errorCount++;
    }
}
