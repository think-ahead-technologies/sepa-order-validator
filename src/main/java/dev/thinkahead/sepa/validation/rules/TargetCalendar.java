// ABOUTME: TARGET (T2) business days: closed on weekends, 1 January, Good Friday, Easter Monday, 1 May, 25/26 December.
// ABOUTME: Standing ECB calendar since 2002; national holidays such as German Unity Day do not close TARGET.
package dev.thinkahead.sepa.validation.rules;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Set;

public final class TargetCalendar {

    private static final Set<MonthDay> FIXED_CLOSING_DAYS =
            Set.of(MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(12, 25), MonthDay.of(12, 26));

    private TargetCalendar() {}

    public static boolean isBusinessDay(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        if (FIXED_CLOSING_DAYS.contains(MonthDay.from(date))) {
            return false;
        }
        LocalDate easterSunday = easterSunday(date.getYear());
        return !date.equals(easterSunday.minusDays(2)) && !date.equals(easterSunday.plusDays(1));
    }

    /**
     * Western (Gregorian) Easter Sunday by the anonymous Gregorian algorithm (Meeus/Jones/Butcher).
     */
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }
}
