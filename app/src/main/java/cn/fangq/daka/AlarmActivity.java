package cn.fangq.daka;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.*;

public class AlarmActivity extends Activity {
    private Task task;
    private boolean opened;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long shownAt;
    private final Runnable watch=new Runnable() { public void run() {
        Store store=new Store(AlarmActivity.this);
        if(task==null||!store.active(task)||AutomationPolicy.terminal(store.state(task.id))||SystemClock.elapsedRealtime()-shownAt>=120_000) { finish(); return; }
        handler.postDelayed(this,1000);
    } };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); task = Task.from(getIntent()); if (task == null) { finish(); return; }
        Store store = new Store(this); if (!store.active(task)||AutomationPolicy.terminal(store.state(task.id))) { finish(); return; }
        shownAt=SystemClock.elapsedRealtime(); handler.post(watch);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout page = Ui.page(this);
        Ui.text(this,page,"FEISHU · 任务提醒",14,Ui.GREEN);
        Ui.heading(this,page,store.title(task),32);
        Ui.text(this,page,task.test() ? "这是提醒与跳转测试，不点击打卡。" : store.attendance(task).isEmpty() ? "到时间了，打开飞书完成这项任务吧。" : "请现场拍照，并在飞书中完成提交。",17,Ui.MUTED);
        LinearLayout card = Ui.card(this,page,"现在完成");
        Ui.button(this,card,"打开飞书页面",() -> open(true),true);
        Ui.button(this,card,"稍后 "+store.snoozeMinutes()+" 分钟",() -> { ActionReceiver.handle(this,"snooze",task); finish(); });
        Ui.button(this,card,"标记为已完成",() -> new AlertDialog.Builder(this).setMessage("请确认已在飞书完成任务。这只记录你的人工确认。").setNegativeButton("返回",null).setPositiveButton("确认完成",(d,w) -> { ActionReceiver.handle(this,"confirmed",task); finish(); }).show());
        Ui.button(this,card,"跳过本次提醒",() -> { ActionReceiver.handle(this,"skip",task); finish(); });
        Ui.text(this,page,"密码锁屏需要你解锁；助手不会代拍、替换照片或自动提交照片。",14,Ui.MUTED);
    }
    @Override protected void onResume() {
        super.onResume();
        if (!opened && task != null && new Store(this).mode(task) >= 1 && !FeishuLauncher.locked(this)) open(false);
    }
    private void open(boolean manual) {
        if (FeishuLauncher.locked(this)) {
            getSystemService(KeyguardManager.class).requestDismissKeyguard(this,new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { open(manual); }
            }); return;
        }
        opened = true;
        if (FeishuLauncher.open(this,task,!task.test())) {
            if (manual) ReminderService.quiet(this,task);
            finish();
        } else { opened = false; Toast.makeText(this,"未能打开飞书，请检查权限或安装情况",Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
