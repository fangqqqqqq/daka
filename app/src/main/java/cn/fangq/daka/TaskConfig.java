package cn.fangq.daka;
import java.time.*;
import java.util.*;
final class TaskConfig {
    final String key;
    String title, time, link = "", draftTitle, draftLink = "", attendance = "";
    boolean enabled = true, calendarRules = true;
    int mode = -1;
    long revision;
    final Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    TaskConfig(String key,String title,String time,Set<DayOfWeek> days) {
        if (!validKey(key)) throw new IllegalArgumentException("Invalid key");
        LocalTime.parse(time); this.key=key; this.title=title; this.draftTitle=title; this.time=time; this.days.addAll(days);
    }
    static boolean validKey(String key) { return key!=null && ("IN".equals(key)||"OUT".equals(key)||key.matches("t_[a-f0-9]{32}")); }
    static TaskConfig create() { return new TaskConfig("t_"+UUID.randomUUID().toString().replace("-",""),"新任务",LocalTime.now().plusMinutes(5).withSecond(0).withNano(0).toString(),EnumSet.allOf(DayOfWeek.class)); }
    int mode(int defaultMode) { return mode<0 ? defaultMode : mode; }
    boolean canAutomate(int defaultMode) { return mode(defaultMode)==2 && ("IN".equals(attendance)||"OUT".equals(attendance)); }
    ZonedDateTime next(ZonedDateTime now,Set<LocalDate> rest,Set<LocalDate> work) {
        return enabled ? SchedulePolicy.next(now,LocalTime.parse(time),days,calendarRules ? rest : Collections.emptySet(),calendarRules ? work : Collections.emptySet()) : null;
    }
    String repeatText() {
        if (days.size()==7) return "每天";
        if (days.equals(EnumSet.range(DayOfWeek.MONDAY,DayOfWeek.FRIDAY))) return "周一至周五";
        if (days.isEmpty()) return calendarRules ? "仅补班日期" : "未选择重复日期";
        String[] names={"一","二","三","四","五","六","日"}; List<String> labels=new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) if (days.contains(day)) labels.add(names[day.getValue()-1]);
        return "周"+String.join("、",labels);
    }
}
