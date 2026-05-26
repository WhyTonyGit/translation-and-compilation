/**
 * Запись таблицы символов — описывает один идентификатор программы.
 *
 * Хранит:
 *   name        — имя идентификатора
 *   type        — тип данных ("int", "boolean", "String", ...)
 *   scope       — область видимости (имя метода или "class")
 *   initialized — было ли присвоено значение
 *   declOrder   — порядковый номер объявления (нумерация с 1)
 */
public class SymbolRecord {

    public String  name;
    public String  type;
    public String  scope;
    public boolean initialized;
    public int     declOrder;

    public SymbolRecord(String name, String type, String scope,
                        boolean initialized, int declOrder) {
        this.name        = name;
        this.type        = type;
        this.scope       = scope;
        this.initialized = initialized;
        this.declOrder   = declOrder;
    }

    @Override
    public String toString() {
        return String.format("  %-10s | %-10s | %-10s | %-9s | %s",
                name, type, scope, "+", initialized ? "+" : "-");
    }
}
