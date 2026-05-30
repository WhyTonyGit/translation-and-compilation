import java.util.ArrayList;
import java.util.List;

/**
 * Узел абстрактного синтаксического дерева (AST).
 *
 * Каждый узел хранит:
 *   type  — смысловой тип узла: "ClassDecl", "MethodDecl", "VarDecl", "IfStmt", ...
 *   value — необязательное значение: имя переменной, значение литерала, оператор, ...
 *   children — список дочерних узлов.
 */
public class ASTNode {

    /** Тип узла (смысловое название конструкции). */
    public String type;

    /** Значение узла (имя, литерал, оператор — если применимо). */
    public String value;

    /** Дочерние узлы. */
    public List<ASTNode> children;

    public ASTNode(String type) {
        this.type = type;
        this.value = null;
        this.children = new ArrayList<>();
    }

    public ASTNode(String type, String value) {
        this.type = type;
        this.value = value;
        this.children = new ArrayList<>();
    }

    /** Добавляет дочерний узел (null игнорируется). */
    public void addChild(ASTNode child) {
        if (child != null) {
            children.add(child);
        }
    }

    /**
     * Выводит корень дерева и рекурсивно все дочерние узлы
     * в наглядном виде с символами ├── и └──.
     */
    public void printRoot() {
        String label = type + (value != null ? ": " + value : "");
        System.out.println(label);
        for (int i = 0; i < children.size(); i++) {
            children.get(i).printTree("", i == children.size() - 1);
        }
    }

    /** Рекурсивный вывод поддерева с заданным префиксом. */
    private void printTree(String prefix, boolean isLast) {
        String connector = isLast ? "└── " : "├── ";
        String label = type + (value != null ? ": " + value : "");
        System.out.println(prefix + connector + label);
        String childPrefix = prefix + (isLast ? "    " : "│   ");
        for (int i = 0; i < children.size(); i++) {
            children.get(i).printTree(childPrefix, i == children.size() - 1);
        }
    }

    // ==================== СЕРИАЛИЗАЦИЯ / ДЕСЕРИАЛИЗАЦИЯ ====================

    /**
     * Сериализует всё дерево в строку для сохранения в файл (ast.txt).
     *
     * Формат строки: {отступ}тип[\tзначение]
     *   — отступ: 2 пробела × уровень глубины
     *   — тип и значение разделены символом табуляции (\t)
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        serializeNode(sb, this, 0);
        return sb.toString();
    }

    private static void serializeNode(StringBuilder sb, ASTNode node, int depth) {
        for (int i = 0; i < depth * 2; i++) sb.append(' ');
        sb.append(node.type);
        if (node.value != null) {
            sb.append('\t').append(node.value);
        }
        sb.append('\n');
        for (ASTNode child : node.children) {
            serializeNode(sb, child, depth + 1);
        }
    }

    /**
     * Восстанавливает дерево из строки, записанной методом serialize().
     *
     * @param text содержимое ast.txt
     * @return корневой узел восстановленного дерева
     */
    public static ASTNode deserialize(String text) {
        String[] lines = text.split("\n", -1);
        ASTNode root = null;
        // stack[depth] — последний узел на этом уровне глубины
        ASTNode[] stack = new ASTNode[500];

        for (String line : lines) {
            if (line.isEmpty()) continue;

            // Считаем отступ (2 пробела = 1 уровень)
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            int depth = indent / 2;

            // Разбираем тип и необязательное значение (разделитель — \t)
            String content = line.trim();
            int tabIdx = content.indexOf('\t');
            ASTNode node;
            if (tabIdx >= 0) {
                node = new ASTNode(content.substring(0, tabIdx),
                                   content.substring(tabIdx + 1));
            } else {
                node = new ASTNode(content);
            }

            stack[depth] = node;
            if (depth == 0) {
                root = node;
            } else {
                stack[depth - 1].addChild(node);
            }
        }

        return root != null ? root : new ASTNode("Program");
    }
}
