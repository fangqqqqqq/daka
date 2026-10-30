package cn.fangq.daka;

import java.time.*;
import java.util.Set;

/** Calendar rules independent of Android; dates are interpreted in the phone's timezone. */
public final class SchedulePolicy {
    private SchedulePolicy() {}
    public static ZonedDateTime next(ZonedDateTime now, LocalTime time, Set<DayOfWeek> weekdays,
                                     Set<LocalDate> rest, Set<LocalDate> work) {
        for (int i = 0; i < 370; i++) {
            LocalDate date = now.toLocalDate().plusDays(i);
            if (rest.contains(date) || (!work.contains(date) && !weekdays.contains(date.getDayOfWeek()))) continue;
            ZonedDateTime candidate = date.atTime(time).atZone(now.getZone());
            if (candidate.isAfter(now)) return candidate;
        }
        return null;
    }
    public static String taskId(String direction, long due, ZoneId zone) {
        return taskId(direction,due,zone,0);
    }
    public static String taskId(String direction, long due, ZoneId zone,long revision) {
        if ("TEST".equals(direction)) return "TEST:" + due;
        String legacy=Instant.ofEpochMilli(due).atZone(zone).toLocalDate() + ":" + direction;
        return revision==0 ? legacy : legacy+":r"+revision;
    }
    public static boolean sameTaskDay(long now, long due, ZoneId zone) {
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .equals(Instant.ofEpochMilli(due).atZone(zone).toLocalDate());
    }
}
