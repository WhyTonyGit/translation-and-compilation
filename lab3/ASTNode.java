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
}
