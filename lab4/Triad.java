/**
 * Триада — единица промежуточного представления программы.
 *
 * Формат записи: N) (операция, операнд1, операнд2)
 *
 * Соглашения об операндах:
 *   "-"  — операнд отсутствует
 *   "^N" — ссылка на результат триады с номером N
 *   иное — имя переменной или значение литерала
 *
 * Примеры операций:
 *   :=           присваивание
 *   +, -, *, /   арифметика
 *   <, >, <=, >= сравнение
 *   ==, !=       проверка равенства
 *   &&, ||, !    логика
 *   ++, --       инкремент / декремент (постфикс/префикс)
 *   IF_FALSE     условный переход (если операнд1 ложь → перейти к операнд2)
 *   JMP          безусловный переход к операнд1
 *   CALL         вызов метода: (CALL, имя, аргумент) или (CALL, имя, -)
 *   ARG          передача аргумента многоарного вызова
 *   RETURN       возврат из метода
 */
public class Triad {

    public int    number;
    public String op;
    public String operand1;
    public String operand2;

    public Triad(int number, String op, String operand1, String operand2) {
        this.number   = number;
        this.op       = op;
        this.operand1 = operand1;
        this.operand2 = operand2;
    }

    @Override
    public String toString() {
        return number + ") (" + op + ", " + operand1 + ", " + operand2 + ")";
    }
}
