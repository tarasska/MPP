import org.jetbrains.annotations.NotNull;

/**
 * В теле класса решения разрешено использовать только финальные переменные типа RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 *
 * @author Скаженик Тарас
 */
public class Solution implements MonotonicClock {
    private final RegularInt c11 = new RegularInt(0);
    private final RegularInt c12 = new RegularInt(0);
    private final RegularInt c13 = new RegularInt(0);
    private final RegularInt c21 = new RegularInt(0);
    private final RegularInt c22 = new RegularInt(0);
    private final RegularInt c23 = new RegularInt(0);

    private void writeLeftToRight(@NotNull Time time) {
        if (c21.getValue() < time.getD1()) {
            c21.setValue(time.getD1());
            c22.setValue(time.getD2());
            c23.setValue(time.getD3());
        } else if (c21.getValue() == time.getD1()) {
            if (c22.getValue() < time.getD2()) {
                c22.setValue(time.getD2());
                c23.setValue(time.getD3());
            } else if (c22.getValue() == time.getD2()) {
                if (c23.getValue() < time.getD3()) {
                    c23.setValue(time.getD3());
                }
            }
        }
    }

    private void writeRightToLeft(@NotNull Time time) {
        c13.setValue(time.getD3());
        c12.setValue(time.getD2());
        c11.setValue(time.getD1());
    }

    private Time readLeftToRight() {
        return new Time(c11.getValue(), c12.getValue(), c13.getValue());
    }

    public Time readRightToLeft() {
        int v3 = c23.getValue();
        int v2 = c22.getValue();
        int v1 = c21.getValue();
        return new Time(v1, v2, v3);
    }

    @Override
    public void write(@NotNull Time time) {
        writeLeftToRight(time);
        writeRightToLeft(readRightToLeft());
    }

    @NotNull
    @Override
    public Time read() {
        Time time1 = readLeftToRight();
        Time time2 = readRightToLeft();
        if (time1 == time2) {
            return time1;
        } else if (time1.getD1() == time2.getD1() &&
                time1.getD2() == time2.getD2()) {
            return new Time(time2.getD1(), time2.getD2(), time2.getD3());
        } else if (time1.getD1() == time2.getD1()) {
            return new Time(time2.getD1(), time2.getD2(), 0);
        } else {
            return new Time(time2.getD1(), 0, 0);
        }
    }
}
