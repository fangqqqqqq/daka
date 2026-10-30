package cn.fangq.daka;

import android.content.*;
import android.app.*;
import android.media.*;
import android.net.Uri;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        Task task = Task.from(intent);
        if (task == null) return;
        Store store = new Store(c);
        if (!store.active(task) || AutomationPolicy.terminal(store.state(task.id))) { AlarmScheduler.reschedule(c); return; }
        AlarmWakeLock.acquire(c);
        try { c.startForegroundService(task.put(new Intent(c, ReminderService.class))); }
        catch (RuntimeException e) {
            AlarmWakeLock.release(); store.log("无法启动提醒服务："+e.getClass().getSimpleName()+"；改用系统闹钟通知");
            fallback(c,task,store);
        }
        // Request service startup before parsing and arranging every future task.
        AlarmScheduler.reschedule(c);
    }
    private void fallback(Context c,Task task,Store store) {
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        String channelId=store.sound() ? "alarm_fallback_sound" : "alarm_fallback_quiet";
        NotificationChannel channel=new NotificationChannel(channelId,"后台提醒备用通道",NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(store.sound() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : null,
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        channel.enableVibration(store.vibration()); manager.createNotificationChannel(channel);
        Intent open=task.put(new Intent(c,AlarmActivity.class)).setData(Uri.parse("daka://fallback/"+task.id));
        PendingIntent activity=PendingIntent.getActivity(c,100,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(c,channelId).setSmallIcon(R.drawable.ic_alarm).setContentTitle(store.title(task))
                .setContentText("到点了。点击打开提醒；请在设置中允许后台运行。").setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(activity).setFullScreenIntent(activity,true).build();
        store.prefs.edit().putString("notification_task",task.id).putString("notification_config",task.configKey).apply();
        manager.notify(ReminderService.NOTIFICATION,notification);
    }
}
