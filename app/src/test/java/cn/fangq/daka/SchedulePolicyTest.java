package cn.fangq.daka;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class SchedulePolicyTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private final Set<DayOfWeek> weekdays = EnumSet.range(DayOfWeek.MONDAY,DayOfWeek.FRIDAY);
    private ZonedDateTime at(String time) { return LocalDateTime.parse(time).atZone(zone); }
    private ZonedDateTime next(String now, Set<LocalDate> rest, Set<LocalDate> work) {
        return SchedulePolicy.next(at(now),LocalTime.of(9,0),weekdays,rest,work);
    }
    @Test public void beforeShiftSchedulesSameDay() { assertEquals(at("2026-10-02T09:00"),next("2026-10-02T08:59",Set.of(),Set.of())); }
    @Test public void atShiftDoesNotScheduleDuplicate() { assertEquals(at("2026-10-05T09:00"),next("2026-10-02T09:00",Set.of(),Set.of())); }
    @Test public void weekendIsSkipped() { assertEquals(at("2026-10-05T09:00"),next("2026-10-03T08:00",Set.of(),Set.of())); }
    @Test public void explicitWorkdayOverridesWeekend() { assertEquals(at("2026-10-03T09:00"),next("2026-10-03T08:00",Set.of(),Set.of(LocalDate.parse("2026-10-03")))); }
    @Test public void holidayOverridesWeekday() { assertEquals(at("2026-10-06T09:00"),next("2026-10-05T08:00",Set.of(LocalDate.parse("2026-10-05")),Set.of())); }
    @Test public void restWinsIfAnInvalidOverlapIsLoaded() { assertEquals(at("2026-10-05T09:00"),next("2026-10-03T08:00",Set.of(LocalDate.parse("2026-10-03")),Set.of(LocalDate.parse("2026-10-03")))); }
    @Test public void disabledAllDaysYieldsNoAlarm() { assertNull(SchedulePolicy.next(at("2026-10-02T08:00"),LocalTime.NOON,Set.of(),Set.of(),Set.of())); }
    @Test public void inAndOutAreSeparateTasks() {
        long due = at("2026-10-02T09:00").toInstant().toEpochMilli();
        assertNotEquals(SchedulePolicy.taskId("IN",due,zone),SchedulePolicy.taskId("OUT",due,zone));
    }
    @Test public void snoozeAcrossMidnightKeepsOriginalTaskDate() {
        long due = at("2026-10-02T23:59").toInstant().toEpochMilli();
        assertEquals("2026-10-02:OUT",SchedulePolicy.taskId("OUT",due,zone));
    }
    @Test public void testAlarmsCanBeRepeatedOnSameDay() {
        long due = at("2026-10-02T09:00").toInstant().toEpochMilli();
        assertNotEquals(SchedulePolicy.taskId("TEST",due,zone),SchedulePolicy.taskId("TEST",due+10_000,zone));
    }
    @Test public void phoneTimezoneDeterminesDay() {
        long due = Instant.parse("2026-10-01T18:00:00Z").toEpochMilli();
        assertEquals("2026-10-02:IN",SchedulePolicy.taskId("IN",due,zone));
        assertEquals("2026-10-01:IN",SchedulePolicy.taskId("IN",due,ZoneId.of("UTC")));
    }
    @Test public void delayedAlarmCannotClickNextDaysAttendance() {
        long due = at("2026-10-02T23:59").toInstant().toEpochMilli();
        assertFalse(SchedulePolicy.sameTaskDay(at("2026-10-03T00:01").toInstant().toEpochMilli(),due,zone));
        assertTrue(SchedulePolicy.sameTaskDay(at("2026-10-02T23:59:30").toInstant().toEpochMilli(),due,zone));
    }
    @Test public void rescheduledTimeHasIndependentDailyState() {
        long due=at("2026-10-03T09:00").toInstant().toEpochMilli();
        assertNotEquals(SchedulePolicy.taskId("IN",due,zone,0),SchedulePolicy.taskId("IN",due+60_000,zone,1));
        assertNotEquals(SchedulePolicy.taskId("IN",due,zone,1),SchedulePolicy.taskId("IN",due,zone,2));
    }
    @Test public void sameRevisionKeepsIdentityForSnoozeAndDuplicateDelivery() {
        long due=at("2026-10-03T09:00").toInstant().toEpochMilli();
        assertEquals(SchedulePolicy.taskId("IN",due,zone,3),SchedulePolicy.taskId("IN",due,zone,3));
        assertEquals("2026-10-03:IN",SchedulePolicy.taskId("IN",due,zone,0));
        assertEquals("2026-10-03:IN:r3",SchedulePolicy.taskId("IN",due,zone,3));
    }
}
