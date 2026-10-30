package cn.fangq.daka;
import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;
public class TaskConfigTest {
    private final ZonedDateTime now=ZonedDateTime.parse("2026-10-02T08:00:00+08:00[Asia/Shanghai]");
    private TaskConfig task(String key,String time) { return new TaskConfig(key,"日报",time,EnumSet.allOf(DayOfWeek.class)); }
    @Test public void arbitraryTasksKeepIndependentDailyIds() {
        TaskConfig a=TaskConfig.create(),b=TaskConfig.create(); assertNotEquals(a.key,b.key);
        assertNotEquals(SchedulePolicy.taskId(a.key,now.toInstant().toEpochMilli(),now.getZone()),SchedulePolicy.taskId(b.key,now.toInstant().toEpochMilli(),now.getZone()));
    }
    @Test public void disablingTaskRemovesItsNextOccurrence() { TaskConfig t=task("IN","09:00"); t.enabled=false; assertNull(t.next(now,Set.of(),Set.of())); }
    @Test public void tasksUseTheirOwnTimeAndWeekdays() {
        TaskConfig a=task("IN","09:00"),b=task("OUT","18:00"); b.days.clear(); b.days.add(DayOfWeek.SUNDAY);
        assertEquals(now.withHour(9),a.next(now,Set.of(),Set.of())); assertEquals(now.plusDays(2).withHour(18),b.next(now,Set.of(),Set.of()));
    }
    @Test public void genericTaskCannotAutomaticallyClickAttendance() { TaskConfig t=task("IN","09:00"); t.mode=2; assertFalse(t.canAutomate(2)); }
    @Test public void explicitAttendanceTypeAndConsentAreRequired() {
        TaskConfig t=task("IN","09:00"); t.attendance="OUT"; t.mode=1; assertFalse(t.canAutomate(2)); t.mode=2; assertTrue(t.canAutomate(1)); t.attendance="wrong"; assertFalse(t.canAutomate(2));
    }
    @Test public void defaultModeIsInheritedOnlyWhenRequested() { TaskConfig t=task("IN","09:00"); assertEquals(1,t.mode(1)); t.mode=0; assertEquals(0,t.mode(2)); }
    @Test public void taskCanOptOutOfCalendarExceptions() {
        TaskConfig t=task("IN","09:00"); Set<LocalDate> rest=Set.of(now.toLocalDate()); assertEquals(now.plusDays(1).withHour(9),t.next(now,rest,Set.of()));
        t.calendarRules=false; assertEquals(now.withHour(9),t.next(now,rest,Set.of()));
    }
    @Test public void invalidTaskKeysCannotEnterAlarmIdentity() { for(String key:new String[]{"TEST","../../IN","hello","t_bad"}) assertFalse(TaskConfig.validKey(key)); }
}
