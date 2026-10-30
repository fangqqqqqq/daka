package cn.fangq.daka;

import android.content.Intent;
import java.time.ZoneId;

final class Task {
    final String direction, id, configKey;
    final long due, revision;
    Task(String direction, long due) {
        this(direction,due,direction);
    }
    Task(String direction,long due,String configKey) {
        this(direction,due,configKey,0);
    }
    Task(TaskConfig config,long due) { this(config.key,due,config.key,config.revision); }
    Task(String direction,long due,String configKey,long revision) {
        this.direction = direction; this.due = due;
        this.configKey = configKey; this.revision=revision;
        id = SchedulePolicy.taskId(direction, due, ZoneId.systemDefault(),revision);
    }
    boolean test() { return "TEST".equals(direction); }
    Intent put(Intent intent) { return intent.putExtra("direction",direction).putExtra("due",due).putExtra("config_key",configKey).putExtra("revision",revision); }
    static Task from(Intent intent) {
        if (intent == null) return null;
        String d = intent.getStringExtra("direction");
        long due = intent.getLongExtra("due", 0);
        String key = intent.getStringExtra("config_key"); if (key==null) key=d;
        long revision=intent.getLongExtra("revision",0);
        return due>0 && revision>=0 && (TaskConfig.validKey(d)||"TEST".equals(d)) && (TaskConfig.validKey(key)||"TEST".equals(key)) ? new Task(d,due,key,revision) : null;
    }
}
