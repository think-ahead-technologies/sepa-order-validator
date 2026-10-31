// ABOUTME: Unit tests for the TARGET (T2) calendar: weekends and the six standing closing days are not business days.
// ABOUTME: Easter dates are checked against published calendars; national holidays do not close TARGET.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TargetCalendarTest {

    @ParameterizedTest
    @CsvSource({"2024, 2024-03-31", "2025, 2025-04-20", "2026, 2026-04-05", "2027, 2027-03-28", "2038, 2038-04-25"})
    void computesEasterSunday(int year, LocalDate easterSunday) {
        assertThat(TargetCalendar.easterSunday(year)).isEqualTo(easterSunday);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2027-01-01",   // New Year's Day
            "2026-04-03",   // Good Friday
            "2026-04-06",   // Easter Monday
            "2027-03-26",   // Good Friday
            "2027-03-29",   // Easter Monday
            "2026-05-01",   // Labour Day
            "2026-12-25",   // Christmas Day
            "2025-12-26",   // 26 December, Friday
            "2026-11-07",   // Saturday
            "2026-11-08"})  // Sunday
    void closingDaysAndWeekendsAreNoBusinessDays(LocalDate date) {
        assertThat(TargetCalendar.isBusinessDay(date)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-11-02",   // Monday
            "2025-10-03",   // German Unity Day, Friday
            "2026-12-24",   // Christmas Eve, Thursday
            "2026-12-31",   // New Year's Eve, Thursday
            "2026-05-14",   // Ascension Day, Thursday
            "2026-04-02"})  // Maundy Thursday
    void nationalHolidaysAndOtherWeekdaysAreBusinessDays(LocalDate date) {
        assertThat(TargetCalendar.isBusinessDay(date)).isTrue();
    }
}
