import java.util.ArrayList;
import java.util.List;

/**
 * Таблица символов: хранит информацию обо всех объявленных идентификаторах.
 *
 * Идентификатор однозначно определяется парой (имя, область_видимости).
 */
public class SymbolTable {

    private final List<SymbolRecord> records = new ArrayList<>();
    private int orderCounter = 0;

    // ─── Изменяющие методы ─────────────────────────────────────────────────

    /**
     * Объявляет новую переменную / параметр.
     */
    public void declare(String name, String type, String scope, boolean initialized) {
        records.add(new SymbolRecord(name, type, scope, initialized, ++orderCounter));
    }

    /**
     * Помечает переменную как инициализированную.
     * Ничего не делает, если переменная не найдена.
     */
    public void setInitialized(String name, String scope) {
        for (SymbolRecord r : records) {
            if (r.name.equals(name) && r.scope.equals(scope)) {
                r.initialized = true;
                return;
            }
        }
    }

    // ─── Запросы ───────────────────────────────────────────────────────────

    /**
     * Проверяет, объявлен ли идентификатор в данной области видимости.
     */
    public boolean isDeclared(String name, String scope) {
        for (SymbolRecord r : records) {
            if (r.name.equals(name) && r.scope.equals(scope)) return true;
        }
        return false;
    }

    /**
     * Возвращает тип идентификатора (null, если не найден).
     */
    public String getType(String name, String scope) {
        for (SymbolRecord r : records) {
            if (r.name.equals(name) && r.scope.equals(scope)) return r.type;
        }
        return null;
    }

    /**
     * Возвращает все записи (неизменяемый вид).
     */
    public List<SymbolRecord> getRecords() {
        return records;
    }

    // ─── Вывод ─────────────────────────────────────────────────────────────

    /**
     * Печатает таблицу символов в табличном виде.
     */
    public void print() {
        String line = "  " + "-".repeat(64);
        System.out.println(line);
        System.out.printf("  %-10s | %-10s | %-10s | %-9s | %s%n",
                "Имя", "Тип", "Область", "Объявлена", "Инициализирована");
        System.out.println(line);
        for (SymbolRecord r : records) {
            System.out.println(r);
        }
        System.out.println(line);
    }
}
