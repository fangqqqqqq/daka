package cn.fangq.daka;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import java.time.*;
import java.util.*;
final class AlarmScheduler {
    static boolean exactAllowed(Context c) { return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms(); }
    static void reschedule(Context c) {
        Store store=new Store(c); AlarmManager manager=c.getSystemService(AlarmManager.class);
        if(!store.prefs.getBoolean("alarm_identity_v2",false)) {
            for(int code:new int[]{10,11,20,21,22,12}) {
                PendingIntent old=PendingIntent.getBroadcast(c,code,new Intent(c,AlarmReceiver.class),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
                if(old!=null) { manager.cancel(old); old.cancel(); }
            }
            store.prefs.edit().putBoolean("alarm_identity_v2",true).apply();
        }
        List<TaskConfig> tasks=store.tasks(); Set<String> keys=new HashSet<>(); for(TaskConfig task:tasks) keys.add(task.key);
        for(String previous:store.prefs.getStringSet("scheduled_keys",Collections.emptySet())) if(!keys.contains(previous)) cancelTask(c,previous);
        store.prefs.edit().putStringSet("scheduled_keys",keys).apply();
        for(TaskConfig config:tasks) {
            manager.cancel(pending(c,new Task(config.key,System.currentTimeMillis()),10));
            store.prefs.edit().remove("next_"+config.key).apply();
            if(!store.enabled()||!config.enabled) { cancelSnooze(c,config.key); continue; }
            if(!exactAllowed(c)) continue;
            try {
                ZonedDateTime next=config.next(ZonedDateTime.now(),store.dates("rest"),store.dates("work"));
                if(next!=null) {
                    long due=next.toInstant().toEpochMilli(); schedule(c,new Task(config,due),due,10);
                    store.prefs.edit().putLong("next_"+config.key,due).apply();
                }
            } catch(RuntimeException e) { store.log("任务未能安排："+config.title+" / "+e.getClass().getSimpleName()); }
        }
    }
    static PendingIntent pending(Context c,Task task,int code) {
        Intent intent=task.put(new Intent(c,AlarmReceiver.class)).setData(Uri.parse("daka://alarm/"+task.direction+"/"+code));
        return PendingIntent.getBroadcast(c,code,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void schedule(Context c,Task task,long when,int code) {
        PendingIntent show=PendingIntent.getActivity(c,code,new Intent(c,MainActivity.class).setData(Uri.parse("daka://show/"+task.direction+"/"+code)),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(AlarmManager.class).setAlarmClock(new AlarmManager.AlarmClockInfo(when,show),pending(c,task,code));
    }
    static void snooze(Context c,Task task) {
        if(exactAllowed(c)) schedule(c,task,System.currentTimeMillis()+new Store(c).snoozeMinutes()*60_000L,20);
    }
    static void cancelSnooze(Context c,String key) { c.getSystemService(AlarmManager.class).cancel(pending(c,new Task(key,System.currentTimeMillis()),20)); }
    static void cancelTask(Context c,String key) {
        c.getSystemService(AlarmManager.class).cancel(pending(c,new Task(key,System.currentTimeMillis()),10)); cancelSnooze(c,key);
        Store store=new Store(c); store.prefs.edit().remove("next_"+key).apply(); ReminderService.cancelConfig(c,key);
        if(key.equals(store.prefs.getString("notification_config",""))) {
            c.getSystemService(NotificationManager.class).cancel(ReminderService.NOTIFICATION);
            store.prefs.edit().remove("notification_task").remove("notification_config").apply();
        }
    }
    static void cancelSnoozes(Context c) {
        for(TaskConfig config:new Store(c).tasks()) cancelSnooze(c,config.key);
        cancelSnooze(c,"TEST"); c.getSystemService(AlarmManager.class).cancel(pending(c,new Task("TEST",System.currentTimeMillis()),12));
    }
}

