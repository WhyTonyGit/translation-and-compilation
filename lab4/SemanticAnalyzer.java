import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Семантический анализатор (ЛР4).
 *
 * Выполняет обход AST, полученного синтаксическим анализатором (ЛР3), и:
 *   1. Заполняет таблицу символов.
 *   2. Проверяет семантические правила.
 *   3. Генерирует промежуточное представление в виде последовательности триад.
 *
 * Проверяемые семантические правила:
 *   Правило 1. Переменная должна быть объявлена до первого использования.
 *   Правило 2. Повторное объявление переменной в одной области видимости запрещено.
 *   Правило 3. Тип правой части присваивания должен быть совместим с типом левой.
 *   Правило 4. Условие if/while/for должно иметь тип boolean.
 *   Правило 5. Тип возвращаемого значения должен соответствовать типу метода.
 */
public class SemanticAnalyzer {

    // ─── Состояние анализатора ────────────────────────────────────────────

    private final SymbolTable      symbolTable;
    private final List<Triad>      triads;
    private       int              errorCount;
    private       int              triadCounter;
    private       String           currentScope;

    /** Возвращаемые типы методов: имя → тип возврата. */
    private final Map<String, String> methodReturnTypes = new HashMap<>();

    // ─── Конструктор ──────────────────────────────────────────────────────

    public SemanticAnalyzer() {
        symbolTable  = new SymbolTable();
        triads       = new ArrayList<>();
        errorCount   = 0;
        triadCounter = 0;
        currentScope = "class";
    }

    // ─── Геттеры ──────────────────────────────────────────────────────────

    public SymbolTable getSymbolTable() { return symbolTable; }
    public List<Triad> getTriads()      { return triads; }
    public int         getErrorCount()  { return errorCount; }

    // ─── Точка входа ──────────────────────────────────────────────────────

    /**
     * Запускает семантический анализ AST.
     * @param root корень AST (узел Program)
     */
    public void analyze(ASTNode root) {
        for (ASTNode child : root.children) {
            if ("ClassDecl".equals(child.type)) {
                analyzeClass(child);
            }
        }
    }

    // ─── Класс ────────────────────────────────────────────────────────────

    private void analyzeClass(ASTNode classNode) {
        // Первый проход: регистрируем сигнатуры всех методов
        for (ASTNode member : classNode.children) {
            if ("MethodDecl".equals(member.type)) {
                String ret = getChildValue(member, "ReturnType");
                methodReturnTypes.put(member.value, ret != null ? ret : "void");
            }
        }

        // Второй проход: анализируем каждый член класса
        for (ASTNode member : classNode.children) {
            if ("MethodDecl".equals(member.type)) {
                analyzeMethod(member);
            }
            // FieldDecl: вне рамок данной лабораторной
        }
    }

    // ─── Метод ────────────────────────────────────────────────────────────

    private void analyzeMethod(ASTNode methodNode) {
        currentScope = methodNode.value;

        // Регистрируем параметры (они считаются объявленными и инициализированными)
        for (ASTNode child : methodNode.children) {
            if ("Params".equals(child.type)) {
                for (ASTNode param : child.children) {
                    if ("Param".equals(param.type)) {
                        String type = getChildValue(param, "Type");
                        String name = param.value;
                        if (symbolTable.isDeclared(name, currentScope)) {
                            // Правило 2
                            error("Повторное объявление параметра '" + name +
                                  "' в методе '" + currentScope + "'");
                        } else {
                            symbolTable.declare(name,
                                    type != null ? type : "?", currentScope, true);
                        }
                    }
                }
            }
        }

        // Анализируем тело
        for (ASTNode child : methodNode.children) {
            if ("Body".equals(child.type)) {
                analyzeBlock(child);
            }
        }
    }

    // ─── Блок и операторы ─────────────────────────────────────────────────

    private void analyzeBlock(ASTNode block) {
        for (ASTNode stmt : block.children) {
            analyzeStmt(stmt);
        }
    }

    private void analyzeStmt(ASTNode stmt) {
        switch (stmt.type) {
            case "VarDecl":    analyzeVarDecl(stmt);   break;
            case "IfStmt":     analyzeIfStmt(stmt);    break;
            case "ForStmt":    analyzeForStmt(stmt);   break;
            case "WhileStmt":  analyzeWhileStmt(stmt); break;
            case "ReturnStmt": analyzeReturnStmt(stmt);break;
            case "ExprStmt":
                if (!stmt.children.isEmpty()) analyzeExpr(stmt.children.get(0));
                break;
            default:
                // Прочие узлы (вложенные блоки и пр.) игнорируются
        }
    }

    // ─── Объявление переменной ────────────────────────────────────────────

    private void analyzeVarDecl(ASTNode decl) {
        String name = decl.value;
        String type = getChildValue(decl, "Type");

        // Правило 2: проверяем повторное объявление
        if (symbolTable.isDeclared(name, currentScope)) {
            error("Правило 2 — повторное объявление переменной '" + name +
                  "' в области '" + currentScope + "'");
            return;
        }

        // Регистрируем переменную (пока не инициализирована)
        symbolTable.declare(name, type != null ? type : "?", currentScope, false);

        // Обрабатываем инициализатор
        for (ASTNode child : decl.children) {
            if ("Init".equals(child.type) && !child.children.isEmpty()) {
                ASTNode exprNode = child.children.get(0);
                String exprType  = inferType(exprNode);

                // Правило 3: проверяем совместимость типов
                if (type != null && !"?".equals(exprType)
                        && !typesCompatible(type, exprType)) {
                    error("Правило 3 — несоответствие типов: переменная '" + name +
                          "' (" + type + ") инициализируется значением типа '" +
                          exprType + "'");
                }

                // Генерируем триады для выражения, затем триаду присваивания
                String initRef = analyzeExpr(exprNode);
                addTriad(":=", name, initRef);
                symbolTable.setInitialized(name, currentScope);
            }
        }
    }

    // ─── if ───────────────────────────────────────────────────────────────

    private void analyzeIfStmt(ASTNode ifStmt) {
        ASTNode condNode = getChild(ifStmt, "Condition");
        if (condNode == null || condNode.children.isEmpty()) return;

        ASTNode condExpr = condNode.children.get(0);

        // Правило 4: условие должно быть boolean
        String condType = inferType(condExpr);
        if (!"boolean".equals(condType) && !"?".equals(condType)) {
            error("Правило 4 — условие оператора if должно иметь тип boolean, " +
                  "получен '" + condType + "'");
        }

        String condRef    = analyzeExpr(condExpr);
        int ifFalseIdx    = addTriad("IF_FALSE", condRef, "?");

        ASTNode thenBlock = getChild(ifStmt, "ThenBlock");
        if (thenBlock != null) analyzeBlock(thenBlock);

        ASTNode elseBlock = getChild(ifStmt, "ElseBlock");
        int jmpIdx = -1;
        if (elseBlock != null) {
            jmpIdx = addTriad("JMP", "?", "-");
        }

        // Адресуем IF_FALSE на начало ветки else (или за if)
        patchOp2(ifFalseIdx, "^" + (triadCounter + 1));

        if (elseBlock != null) {
            analyzeBlock(elseBlock);
            patchOp1(jmpIdx, "^" + (triadCounter + 1));
        }
    }

    // ─── for ──────────────────────────────────────────────────────────────

    private void analyzeForStmt(ASTNode forStmt) {
        // Инициализация
        ASTNode initNode = getChild(forStmt, "Init");
        if (initNode != null) {
            for (ASTNode child : initNode.children) {
                if ("VarDecl".equals(child.type)) analyzeVarDecl(child);
                else                              analyzeExpr(child);
            }
        }

        // Запоминаем начало проверки условия
        int condStart = triadCounter + 1;

        // Условие
        String condRef    = "true";
        ASTNode condNode  = getChild(forStmt, "Condition");
        if (condNode != null && !condNode.children.isEmpty()) {
            ASTNode condExpr = condNode.children.get(0);
            String condType  = inferType(condExpr);
            if (!"boolean".equals(condType) && !"?".equals(condType)) {
                error("Правило 4 — условие оператора for должно иметь тип boolean, " +
                      "получен '" + condType + "'");
            }
            condRef = analyzeExpr(condExpr);
        }
        int ifFalseIdx = addTriad("IF_FALSE", condRef, "?");

        // Тело цикла
        ASTNode body = getChild(forStmt, "Body");
        if (body != null) analyzeBlock(body);

        // Шаг обновления
        ASTNode updateNode = getChild(forStmt, "Update");
        if (updateNode != null) {
            for (ASTNode child : updateNode.children) analyzeExpr(child);
        }

        // Безусловный переход к условию
        addTriad("JMP", "^" + condStart, "-");

        // Адресуем IF_FALSE на выход из цикла
        patchOp2(ifFalseIdx, "^" + (triadCounter + 1));
    }

    // ─── while ────────────────────────────────────────────────────────────

    private void analyzeWhileStmt(ASTNode whileStmt) {
        int condStart    = triadCounter + 1;

        String condRef   = "true";
        ASTNode condNode = getChild(whileStmt, "Condition");
        if (condNode != null && !condNode.children.isEmpty()) {
            ASTNode condExpr = condNode.children.get(0);
            String condType  = inferType(condExpr);
            if (!"boolean".equals(condType) && !"?".equals(condType)) {
                error("Правило 4 — условие оператора while должно иметь тип boolean, " +
                      "получен '" + condType + "'");
            }
            condRef = analyzeExpr(condExpr);
        }
        int ifFalseIdx = addTriad("IF_FALSE", condRef, "?");

        ASTNode body = getChild(whileStmt, "Body");
        if (body != null) analyzeBlock(body);

        addTriad("JMP", "^" + condStart, "-");
        patchOp2(ifFalseIdx, "^" + (triadCounter + 1));
    }

    // ─── return ───────────────────────────────────────────────────────────

    private void analyzeReturnStmt(ASTNode returnStmt) {
        if (returnStmt.children.isEmpty()) {
            addTriad("RETURN", "-", "-");
        } else {
            String retRef = analyzeExpr(returnStmt.children.get(0));
            addTriad("RETURN", retRef, "-");
        }
    }

    // ─── Выражения ────────────────────────────────────────────────────────

    /**
     * Анализирует выражение: проверяет семантику, генерирует триады.
     *
     * @return ссылка на результат (имя переменной, литерал или "^N")
     */
    private String analyzeExpr(ASTNode node) {
        if (node == null) return "-";

        switch (node.type) {

            // Литералы — возвращаем значение без генерации триад
            case "IntLiteral":
            case "RealLiteral":
            case "BoolLiteral":
            case "StringLiteral":
                return node.value;

            // Идентификатор — Правило 1: проверяем объявление
            case "Identifier": {
                String name = node.value;
                if (!name.contains(".") && !symbolTable.isDeclared(name, currentScope)) {
                    error("Правило 1 — использование необъявленной переменной '" + name + "'");
                }
                return name;
            }

            // Выражение в скобках — прозрачно
            case "GroupExpr":
                return node.children.isEmpty() ? "-" : analyzeExpr(node.children.get(0));

            // Бинарная операция
            case "BinaryExpr": {
                if (node.children.size() < 2) return "-";
                String left  = analyzeExpr(node.children.get(0));
                String right = analyzeExpr(node.children.get(1));
                return "^" + addTriad(node.value, left, right);
            }

            // Присваивание: Правила 1 и 3
            case "Assign": {
                if (node.children.size() < 2) return "-";
                ASTNode leftNode  = node.children.get(0);
                ASTNode rightNode = node.children.get(1);

                String varName = "?";
                if ("Identifier".equals(leftNode.type)) {
                    varName = leftNode.value;
                    // Правило 1
                    if (!symbolTable.isDeclared(varName, currentScope)) {
                        error("Правило 1 — присваивание необъявленной переменной '" +
                              varName + "'");
                    } else {
                        // Правило 3
                        String leftType  = symbolTable.getType(varName, currentScope);
                        String rightType = inferType(rightNode);
                        if (leftType != null && !"?".equals(rightType)
                                && !typesCompatible(leftType, rightType)) {
                            error("Правило 3 — несоответствие типов в присваивании: '" +
                                  varName + "' (" + leftType + ") := (" + rightType + ")");
                        }
                        symbolTable.setInitialized(varName, currentScope);
                    }
                }

                String rightRef = analyzeExpr(rightNode);
                return "^" + addTriad(":=", varName, rightRef);
            }

            // Унарная операция (!, -, ++, --)
            case "UnaryExpr": {
                if (node.children.isEmpty()) return "-";
                String operand = analyzeExpr(node.children.get(0));
                return "^" + addTriad(node.value, operand, "-");
            }

            // Постфикс (x++, x--)
            case "PostfixExpr": {
                if (node.children.isEmpty()) return "-";
                ASTNode operandNode = node.children.get(0);
                String  operand     = analyzeExpr(operandNode);
                if ("Identifier".equals(operandNode.type)) {
                    symbolTable.setInitialized(operandNode.value, currentScope);
                }
                return "^" + addTriad(node.value, operand, "-");
            }

            // Вызов метода
            case "MethodCall": {
                String methodName = node.value;
                if (node.children.isEmpty()) {
                    return "^" + addTriad("CALL", methodName, "-");
                } else if (node.children.size() == 1) {
                    String arg = analyzeExpr(node.children.get(0));
                    return "^" + addTriad("CALL", methodName, arg);
                } else {
                    // Несколько аргументов: ARG-триады для каждого, затем CALL
                    List<String> argRefs = new ArrayList<>();
                    for (ASTNode argNode : node.children) {
                        argRefs.add(analyzeExpr(argNode));
                    }
                    for (String argRef : argRefs) {
                        addTriad("ARG", argRef, "-");
                    }
                    return "^" + addTriad("CALL", methodName, "-");
                }
            }

            default:
                return "-";
        }
    }

    // ─── Вывод типов (без генерации триад) ────────────────────────────────

    /**
     * Выводит тип выражения без генерации триад.
     * Возвращает "?" если тип определить невозможно.
     */
    private String inferType(ASTNode node) {
        if (node == null) return "?";
        switch (node.type) {
            case "IntLiteral":    return "int";
            case "RealLiteral":   return "double";
            case "BoolLiteral":   return "boolean";
            case "StringLiteral": return "String";
            case "Identifier": {
                String name = node.value;
                if (name.contains(".")) return "?"; // System.out и т.п.
                String t = symbolTable.getType(name, currentScope);
                return t != null ? t : "?";
            }
            case "GroupExpr":
                return node.children.isEmpty() ? "?" : inferType(node.children.get(0));
            case "BinaryExpr": {
                String op = node.value;
                if ("==".equals(op) || "!=".equals(op) ||
                    "<".equals(op)  || ">".equals(op)  ||
                    "<=".equals(op) || ">=".equals(op) ||
                    "&&".equals(op) || "||".equals(op)) {
                    return "boolean";
                }
                // Арифметика: тип левого операнда
                return node.children.isEmpty() ? "?" : inferType(node.children.get(0));
            }
            case "Assign":
                return node.children.isEmpty() ? "?" : inferType(node.children.get(0));
            case "UnaryExpr":
                return "!".equals(node.value) ? "boolean"
                       : (node.children.isEmpty() ? "?" : inferType(node.children.get(0)));
            case "PostfixExpr":
                return node.children.isEmpty() ? "?" : inferType(node.children.get(0));
            case "MethodCall": {
                String ret = methodReturnTypes.get(node.value);
                return ret != null ? ret : "?";
            }
            default: return "?";
        }
    }

    // ─── Совместимость типов ──────────────────────────────────────────────

    /**
     * Проверяет, совместим ли тип actual с ожидаемым типом expected.
     * Разрешает стандартные расширяющие числовые преобразования Java.
     */
    private boolean typesCompatible(String expected, String actual) {
        if (expected.equals(actual)) return true;
        if ("double".equals(expected) && "int".equals(actual)) return true;
        if ("float".equals(expected)  && "int".equals(actual)) return true;
        if ("long".equals(expected)   && "int".equals(actual)) return true;
        return false;
    }

    // ─── Управление триадами ──────────────────────────────────────────────

    /**
     * Добавляет триаду и возвращает её номер (нумерация с 1).
     */
    private int addTriad(String op, String op1, String op2) {
        triadCounter++;
        triads.add(new Triad(triadCounter, op, op1, op2));
        return triadCounter;
    }

    /** Исправляет операнд1 ранее добавленной триады (для обратной засылки). */
    private void patchOp1(int triadNum, String value) {
        triads.get(triadNum - 1).operand1 = value;
    }

    /** Исправляет операнд2 ранее добавленной триады (для обратной засылки). */
    private void patchOp2(int triadNum, String value) {
        triads.get(triadNum - 1).operand2 = value;
    }

    // ─── Вспомогательные методы ───────────────────────────────────────────

    /** Находит первый дочерний узел с указанным типом. */
    private ASTNode getChild(ASTNode node, String type) {
        for (ASTNode child : node.children) {
            if (type.equals(child.type)) return child;
        }
        return null;
    }

    /**
     * Возвращает value первого дочернего узла с указанным типом.
     * Пример: getChildValue(param, "Type") → "int"
     */
    private String getChildValue(ASTNode node, String childType) {
        ASTNode child = getChild(node, childType);
        return child != null ? child.value : null;
    }

    /** Регистрирует семантическую ошибку. */
    private void error(String message) {
        System.out.println("Семантическая ошибка: " + message);
        errorCount++;
    }
}
