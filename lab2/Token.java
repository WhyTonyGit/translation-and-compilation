/**
 * Класс, представляющий один лексический токен.
 * Хранит тип токена и его текстовое значение.
 */
public class Token {
    // Тип токена: KEYWORD, IDENTIFIER, CONSTANT_INT, OPERATOR и т.д.
    String type;

    // Текстовое значение: "int", "main", "42" и т.д.
    String value;

    /**
     * Создаёт новый токен с указанным типом и значением.
     * @param type тип токена
     * @param value текстовое значение
     */
    public Token(String type, String value) {
        this.type = type;
        this.value = value;
    }

    /**
     * Возвращает строковое представление токена в формате (TYPE, value)
     */
    @Override
    public String toString() {
        return "(" + type + ", " + value + ")";
    }
}
