package cn.fangq.daka;

import android.content.*;

public class ActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) { handle(c, intent.getAction(), Task.from(intent)); }
    static void handle(Context c, String action, Task task) {
        if (task == null) return;
        Store store = new Store(c);
        if(!store.active(task)) return;
        if ("snooze".equals(action)) {
            if (!AlarmScheduler.exactAllowed(c)) { ReminderService.information(c, task, "缺少精准闹钟权限，请打开助手设置"); return; }
            store.stopAutomation(); AlarmScheduler.snooze(c,task);
        } else if ("skip".equals(action)) {
            store.stopAutomation(); store.state(task.id, "skipped", Store.label(task.direction) + "：你已选择跳过本次提醒");
        } else if ("confirmed".equals(action)) {
            store.stopAutomation(); store.state(task.id, "user_confirmed", Store.label(task.direction) + "：你已手动确认完成（未读取服务器结果）");
        } else return;
        ReminderService.finishTask(c,task);
    }
}
