package cn.fangq.daka;

import android.app.KeyguardManager;
import android.content.*;
import android.net.Uri;
import java.time.ZoneId;

final class FeishuLauncher {
    static final String PACKAGE = "com.ss.android.lark";
    static boolean locked(Context c) { return c.getSystemService(KeyguardManager.class).isKeyguardLocked(); }
    static boolean validLink(String link) {
        try {
            Uri uri = Uri.parse(link);
            return ("feishu".equals(uri.getScheme()) || "lark".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && "applink.feishu.cn".equals(uri.getHost());
        } catch (RuntimeException e) { return false; }
    }
    static boolean open(Context c, Task task, boolean allowAuto) {
        Store store = new Store(c);
        if(!store.active(task)) return false;
        if (locked(c)) { store.log("请先解锁，再进入飞书拍照打卡"); return false; }
        // Both a full-screen activity and the service may wake at once. Keep the existing session.
        if (task.id.equals(store.prefs.getString("last_launch_id",""))
                && System.currentTimeMillis()-store.prefs.getLong("last_launch_time",0)<3000) return true;
        TaskConfig config = store.config(task.configKey);
        boolean auto = allowAuto && SchedulePolicy.sameTaskDay(System.currentTimeMillis(),task.due,ZoneId.systemDefault())
                && AutomationPolicy.mayStart(config != null && config.canAutomate(store.mode()), task.test(), false,
                System.currentTimeMillis(), task.due, store.state(task.id));
        boolean armed = auto && FeishuAccessibilityService.connected();
        if (armed) {
            store.prefs.edit().putString("session_id", task.id).putString("session_direction", task.direction)
                    .putLong("session_due", task.due).putLong("session_revision",task.revision).putLong("session_until", System.currentTimeMillis() + 120_000).commit();
            FeishuAccessibilityService.prepareSession(task);
        } else store.stopAutomation();
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(store.link(task))).setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(intent);
            store.prefs.edit().putString("last_launch_id",task.id).putLong("last_launch_time",System.currentTimeMillis()).apply();
            store.log(store.title(task) + "：已请求打开飞书页面" + (task.test() ? "，仅跳转，不点击打卡" : armed ? "，等待识别页面" : ""));
            return true;
        } catch (RuntimeException first) {
            store.stopAutomation();
            try {
                Intent fallback = c.getPackageManager().getLaunchIntentForPackage(PACKAGE);
                if (fallback == null) throw new IllegalStateException();
                c.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                store.log("考勤直达链接不可用，已打开飞书，请从工作台进入考勤");
                return true;
            } catch (RuntimeException second) { store.log("未能打开飞书，请检查安装情况和后台弹出权限"); return false; }
        }
    }
}
