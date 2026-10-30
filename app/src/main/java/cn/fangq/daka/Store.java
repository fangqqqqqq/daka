package cn.fangq.daka;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.time.*;
import java.util.*;
final class Store {
    static final String DEFAULT_LINK="feishu://applink.feishu.cn/client/mini_program/open?appId=cli_9c21a4767c305107";
    final SharedPreferences prefs;
    private final Context context;
    Store(Context c) {
        context=c.getApplicationContext();
        prefs=c.getSharedPreferences("daka",Context.MODE_PRIVATE);
        if (!prefs.contains("task_configs")) {
            List<TaskConfig> migrated=new ArrayList<>();
            for (String key : new String[]{"IN","OUT"}) {
                TaskConfig task=new TaskConfig(key,"IN".equals(key) ? "上班打卡" : "下班打卡",time(key),weekdays());
                task.attendance=key; migrated.add(task);
            }
            saveTasks(migrated);
            SharedPreferences.Editor clean=prefs.edit().remove("log");
            for (String key : prefs.getAll().keySet()) if(key.startsWith("detail_")) clean.remove(key);
            clean.apply();
        }
    }
    boolean enabled() { return prefs.getBoolean("enabled",false); }
    int mode() { return prefs.getInt("mode",1); }
    String time(String direction) { return prefs.getString("time_"+direction,"IN".equals(direction) ? "09:00" : "18:00"); }
    String link() { return prefs.getString("link",DEFAULT_LINK); }
    List<TaskConfig> tasks() {
        List<TaskConfig> tasks=new ArrayList<>(); Set<String> keys=new HashSet<>();
        try {
            JSONArray array=new JSONArray(prefs.getString("task_configs","[]"));
            for(int i=0;i<array.length();i++) {
                try {
                    JSONObject row=array.getJSONObject(i); String key=row.getString("key");
                    if(!keys.add(key)) continue;
                    Set<DayOfWeek> days=EnumSet.noneOf(DayOfWeek.class); JSONArray repeats=row.getJSONArray("days");
                    for(int j=0;j<repeats.length();j++) days.add(DayOfWeek.of(repeats.getInt(j)));
                    TaskConfig task=new TaskConfig(key,row.getString("title"),row.getString("time"),days);
                    task.enabled=row.optBoolean("enabled",true); task.calendarRules=row.optBoolean("calendar",true);
                    task.mode=Math.max(-1,Math.min(2,row.optInt("mode",-1))); task.link=row.optString("link","");
                    task.attendance=row.optString("attendance",""); task.draftTitle=row.optString("draft_title",task.title); task.draftLink=row.optString("draft_link",task.link);
                    task.revision=Math.max(0,row.optLong("revision",0));
                    tasks.add(task);
                } catch(JSONException|RuntimeException ignored) { log("忽略无法读取的任务配置"); }
            }
        } catch(JSONException ignored) { log("任务配置无法读取"); }
        return tasks;
    }
    TaskConfig config(String key) { for(TaskConfig task:tasks()) if(task.key.equals(key)) return task; return null; }
    void saveTask(TaskConfig task) {
        List<TaskConfig> tasks=tasks(); boolean replaced=false;
        for(int i=0;i<tasks.size();i++) if(tasks.get(i).key.equals(task.key)) { tasks.set(i,task); replaced=true; break; }
        if(!replaced) tasks.add(task); saveTasks(tasks);
    }
    void deleteTask(String key) { List<TaskConfig> tasks=tasks(); tasks.removeIf(task->task.key.equals(key)); saveTasks(tasks); }
    void saveTasks(List<TaskConfig> tasks) {
        Map<String,TaskConfig> previous=new HashMap<>(); for(TaskConfig task:tasks()) previous.put(task.key,task);
        Set<String> changed=new HashSet<>();
        JSONArray array=new JSONArray();
        try {
            for(TaskConfig task:tasks) {
                TaskConfig old=previous.get(task.key);
                if(old!=null) {
                    boolean timeChanged=!LocalTime.parse(old.time).equals(LocalTime.parse(task.time));
                    task.revision=old.revision+(timeChanged ? 1 : 0);
                    if(timeChanged) changed.add(task.key);
                }
                JSONObject row=new JSONObject(); JSONArray days=new JSONArray(); for(DayOfWeek day:task.days) days.put(day.getValue());
                row.put("key",task.key).put("title",task.title).put("time",task.time).put("days",days).put("enabled",task.enabled)
                    .put("calendar",task.calendarRules).put("mode",task.mode).put("link",task.link).put("attendance",task.attendance)
                    .put("draft_title",task.draftTitle).put("draft_link",task.draftLink).put("revision",task.revision); array.put(row);
            }
        } catch(JSONException e) { throw new IllegalStateException(e); }
        prefs.edit().putString("task_configs",array.toString()).apply();
        for(String key:changed) AlarmScheduler.cancelTask(context,key);
    }
    int mode(Task task) { TaskConfig config=config(task.configKey); return config==null ? mode() : config.mode(mode()); }
    String link(Task task) { TaskConfig config=config(task.configKey); return config==null||config.link.isEmpty() ? link() : config.link; }
    String title(Task task) { TaskConfig config=config(task.configKey); return config==null ? (task.test() ? "提醒测试" : "任务提醒") : config.title+(task.test() ? " · 测试" : ""); }
    String attendance(Task task) { TaskConfig config=config(task.configKey); return config==null ? "" : config.attendance; }
    boolean active(Task task) { TaskConfig config=config(task.configKey); return task.test() ? "TEST".equals(task.configKey)||config!=null : enabled()&&config!=null&&config.enabled&&config.revision==task.revision; }
    Task today(TaskConfig config) { return new Task(config,LocalDate.now().atTime(LocalTime.parse(config.time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()); }
    boolean sound() { return prefs.getBoolean("sound",true); }
    boolean vibration() { return prefs.getBoolean("vibration",true); }
    int ringSeconds() { return Math.max(15,Math.min(90,prefs.getInt("ring_seconds",60))); }
    int snoozeMinutes() { return Math.max(1,Math.min(15,prefs.getInt("snooze_minutes",2))); }
    Set<DayOfWeek> weekdays() {
        Set<DayOfWeek> result=EnumSet.noneOf(DayOfWeek.class);
        for(String day:prefs.getStringSet("weekdays",new HashSet<>(Arrays.asList("1","2","3","4","5")))) {
            try { result.add(DayOfWeek.of(Integer.parseInt(day))); } catch(RuntimeException ignored) {}
        } return result;
    }
    Set<LocalDate> dates(String key) {
        Set<LocalDate> result=new HashSet<>();
        for(String date:prefs.getStringSet(key,Collections.emptySet())) { try { result.add(LocalDate.parse(date)); } catch(RuntimeException ignored) {} }
        return result;
    }
    String state(String id) { return prefs.getString("task_"+id,"new"); }
    void state(String id,String state,String detail) { prefs.edit().putString("task_"+id,state).commit(); log(detail); }
    void log(String message) { android.util.Log.i("FeishuReminder",message); }
    void stopAutomation() { prefs.edit().remove("session_id").remove("session_until").apply(); }
    static String label(String direction) { return "IN".equals(direction) ? "上班" : "OUT".equals(direction) ? "下班" : "TEST".equals(direction) ? "测试" : "任务"; }
}

