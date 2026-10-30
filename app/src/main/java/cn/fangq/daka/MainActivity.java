package cn.fangq.daka;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
public class MainActivity extends Activity {
    private Store store;
    private LinearLayout content,nav;
    private int tab;
    private EditText link,rest,work;
    private TextView clock,saveStatus,permissionSummary;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable textSave=()->saveText();
    private final Runnable clockTick=new Runnable() { public void run() { if(clock!=null) clock.setText("设备时间  "+LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))+"  ·  "+ZoneId.systemDefault().getId()); handler.postDelayed(this,1000); } };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); store=new Store(this); tab=saved==null ? 0 : saved.getInt("tab",0);
        LinearLayout root=Ui.column(this); root.setBackgroundColor(Ui.BG);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); content=Ui.column(this); content.setPadding(Ui.dp(this,22),Ui.dp(this,16),Ui.dp(this,22),Ui.dp(this,24)); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,8)); nav.setBackgroundColor(Color.WHITE); root.addView(nav,new LinearLayout.LayoutParams(-1,Ui.dp(this,72)));
        root.setOnApplyWindowInsetsListener((v,insets)-> { root.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom()); return insets; });
        setContentView(root); Ui.styleWindow(this); showTab(tab);
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt("tab",tab); super.onSaveInstanceState(state); }
    @Override protected void onResume() { super.onResume(); AlarmScheduler.reschedule(this); showTab(tab); handler.removeCallbacks(clockTick); handler.post(clockTick); }
    @Override protected void onPause() { saveText(); handler.removeCallbacks(clockTick); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    void showTab(int next) {
        saveText(); tab=next; link=rest=work=null; clock=saveStatus=permissionSummary=null; content.removeAllViews(); nav.removeAllViews();
        String[] names={"概览","任务","设置"};
        for(int i=0;i<names.length;i++) { final int index=i; Button button=Ui.button(this,nav,names[i],()->showTab(index),tab==i); Ui.weighted(button,1,this); }
        Ui.text(this,content,"FEISHU  /  REMINDERS",12,Ui.GREEN);
        Ui.heading(this,content,names[tab].equals("概览") ? "让提醒，准时发生" : tab==1 ? "我的任务" : "按你的习惯来",28);
        Ui.text(this,content,tab==0 ? "今天的安排，一眼就能看到。" : tab==1 ? "名称、时间和重复规则，都由你决定。" : "所有修改自动保存。",14,Ui.MUTED);
        if(tab==0) overview(); else if(tab==1) taskList(); else settings();
    }
    private void overview() {
        List<TaskConfig> tasks=store.tasks(); TaskConfig soon=null; long nearest=Long.MAX_VALUE; int count=0;
        for(TaskConfig task:tasks) { if(task.enabled) count++; long when=store.prefs.getLong("next_"+task.key,0); if(when>0&&when<nearest) { nearest=when; soon=task; } }
        LinearLayout hero=Ui.card(this,content,""); hero.setBackground(Ui.round(Ui.INK,Ui.dp(this,24)));
        Ui.text(this,hero,"下一次提醒",13,0xffb3d4c9);
        Ui.text(this,hero,soon==null ? "--:--" : Instant.ofEpochMilli(nearest).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),50,Color.WHITE);
        Ui.text(this,hero,soon==null ? (store.enabled() ? "添加或启用一项任务" : "提醒已暂停") : soon.title,20,Color.WHITE);
        Ui.text(this,hero,soon==null ? "设置好时间后，开启任务即可。" : Instant.ofEpochMilli(nearest).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM月dd日 E",Locale.CHINA)),13,0xffb3d4c9);
        Ui.toggle(this,content,"提醒总开关 · "+count+" 项已启用",store.enabled(),checked->{ store.prefs.edit().putBoolean("enabled",checked).apply(); AlarmScheduler.reschedule(this); if(!checked) store.stopAutomation(); showTab(0); });
        clock=Ui.text(this,content,"",12,Ui.MUTED);
        if(!AlarmScheduler.exactAllowed(this)||!getSystemService(NotificationManager.class).areNotificationsEnabled()) Ui.button(this,content,"完善通知和精准闹钟权限  ›",()->showTab(2));
        LinearLayout today=Ui.card(this,content,"今天的任务");
        if(tasks.isEmpty()) Ui.text(this,today,"还没有任务，去任务页添加一个吧。",14,Ui.MUTED);
        for(TaskConfig task:tasks) {
            LinearLayout rowTask=Ui.row(this,today);
            TextView title=Ui.text(this,rowTask,task.title+"  ·  "+task.time,15,Ui.INK); title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            Ui.text(this,rowTask,task.enabled ? stateLabel(store.state(store.today(task).id)) : "已停用",12,Ui.MUTED);
        }
        Ui.button(this,today,"管理全部任务  ›",()->showTab(1));
    }
    private void taskList() {
        LinearLayout addRow=Ui.row(this,content);
        Ui.text(this,addRow,store.tasks().size()+" 项任务",13,Ui.MUTED).setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        Button add=Ui.button(this,addRow,"＋ 新增任务",this::addTask,true); add.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this,136),Ui.dp(this,44)));
        if(store.tasks().isEmpty()) { LinearLayout empty=Ui.card(this,content,"从一项提醒开始"); Ui.text(this,empty,"日报、会议、拍照打卡…\n每项任务都有独立的时间和飞书入口。",15,Ui.MUTED); }
        List<TaskConfig> tasks=store.tasks(); tasks.sort(Comparator.comparing(task->task.time));
        for(TaskConfig task:tasks) {
            LinearLayout card=Ui.card(this,content,""); LinearLayout row=Ui.row(this,card);
            LinearLayout text=Ui.column(this); row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            Ui.heading(this,text,task.time,34); Ui.heading(this,text,task.title,18);
            Switch toggle=new Switch(this); toggle.setContentDescription("启用任务："+task.title); toggle.setChecked(task.enabled); row.addView(toggle);
            toggle.setOnCheckedChangeListener((v,checked)-> { task.enabled=checked; store.saveTask(task); if(!checked) ReminderService.cancelConfig(this,task.key); AlarmScheduler.reschedule(this); });
            Ui.text(this,card,task.repeatText()+"  ·  "+modeLabel(task.mode(store.mode())),13,Ui.MUTED);
            long next=store.prefs.getLong("next_"+task.key,0);
            Ui.text(this,card,!store.enabled() ? "总开关已暂停" : next==0 ? "暂未安排提醒" : "下次 "+Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd E HH:mm",Locale.CHINA)),12,Ui.MUTED);
            LinearLayout actions=Ui.row(this,card);
            Button edit=Ui.button(this,actions,"编辑任务",()->editTask(task.key)); Ui.weighted(edit,1,this);
            Button test=Ui.button(this,actions,"测试此任务",()->testAlarm(task.key)); Ui.weighted(test,1,this);
        }
        Ui.text(this,content,"每次安排独立记录状态。修改时间后会重新待提醒；新时间已过则安排到下一次。",12,Ui.MUTED);
    }
    void addTask() { TaskConfig task=TaskConfig.create(); task.enabled=false; store.saveTask(task); editTask(task.key); }
    void editTask(String key) { startActivity(new Intent(this,TaskEditorActivity.class).putExtra("key",key)); }
    private void settings() {
        LinearLayout defaults=Ui.card(this,content,"默认执行方式");
        Ui.select(this,defaults,"新任务默认方式",new String[]{"仅提醒，手动打开","自动打开飞书页面","辅助进入考勤打卡入口"},store.mode(),mode->{ if(mode!=store.mode()) { store.prefs.edit().putInt("mode",mode).apply(); store.stopAutomation(); } });
        link=Ui.field(this,defaults,"默认飞书入口",store.prefs.getString("draft_link",store.link()),"默认飞书入口");
        Ui.text(this,defaults,"任务未单独填写入口时使用此链接。日报与考勤均可；拍照和提交由你完成。",12,Ui.MUTED);
        LinearLayout alerts=Ui.card(this,content,"提醒偏好");
        Ui.toggle(this,alerts,"播放闹钟铃声",store.sound(),on->store.prefs.edit().putBoolean("sound",on).apply());
        Ui.toggle(this,alerts,"提醒时振动",store.vibration(),on->store.prefs.edit().putBoolean("vibration",on).apply());
        int[] seconds={15,30,60,90},minutes={1,2,5,10,15};
        Ui.select(this,alerts,"响铃时长",new String[]{"15 秒","30 秒","60 秒","90 秒"},index(seconds,store.ringSeconds()),i->store.prefs.edit().putInt("ring_seconds",seconds[i]).apply());
        Ui.select(this,alerts,"稍后提醒间隔",new String[]{"1 分钟","2 分钟","5 分钟","10 分钟","15 分钟"},index(minutes,store.snoozeMinutes()),i->store.prefs.edit().putInt("snooze_minutes",minutes[i]).apply());
        Ui.text(this,alerts,"打开飞书后保留顶部浮窗，可展开或收起。单次提醒最多保持 2 分钟，完成、跳过或稍后提醒会关闭；响铃时长只控制声音。",12,Ui.MUTED);
        LinearLayout calendar=Ui.card(this,content,"休假与补班");
        rest=Ui.field(this,calendar,"休假日期 · 逗号分隔",store.prefs.getString("draft_rest",dateText(store.dates("rest"))),"休假日期");
        work=Ui.field(this,calendar,"补班日期 · 逗号分隔",store.prefs.getString("draft_work",dateText(store.dates("work"))),"补班日期");
        Ui.text(this,calendar,"格式 2026-10-03。每项任务可选择是否遵循这些日期规则。",12,Ui.MUTED);
        saveStatus=Ui.text(this,content,"✓ 修改后自动保存",12,Ui.GREEN); watch(link,"draft_link"); watch(rest,"draft_rest"); watch(work,"draft_work");
        LinearLayout system=Ui.card(this,content,"运行权限与设备时间");
        permissionSummary=Ui.text(this,system,permissionText(),13,Ui.MUTED);
        Ui.button(this,system,"通知权限",()-> { if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1); else setting(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())); });
        Ui.button(this,system,"精准闹钟",()-> { if(Build.VERSION.SDK_INT>=31) setting(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))); });
        Ui.button(this,system,"提醒通道与锁屏通知",()->setting(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,ReminderService.CHANNEL)));
        Ui.button(this,system,"全屏与悬浮提醒",()->new AlertDialog.Builder(this).setItems(new String[]{"锁屏全屏提醒","悬浮提醒"},(dialog,which)-> { if(which==0&&Build.VERSION.SDK_INT>=34) setting(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName()))); else if(which==1) setting(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))); else setting(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())); }).show());
        Ui.button(this,system,"后台运行与电池优化",()->new AlertDialog.Builder(this).setItems(new String[]{"应用后台／自启动设置","电池优化白名单"},(d,which)->setting(which==0 ? new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())) : new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))).show());
        Ui.button(this,system,"开启息屏后台保护",()-> {
            if(getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())) { toast("系统后台保护已开启；一加仍需检查应用后台与自启动"); return; }
            new AlertDialog.Builder(this).setTitle("允许息屏后台运行").setMessage("接下来在系统提示中允许助手不受电池优化限制，以便息屏后启动提醒。一加还需在应用电池管理中允许后台活动和自启动。").setNegativeButton("返回",null).setPositiveButton("前往系统设置",(d,w)->setting(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())))).show();
        });
        Ui.button(this,system,"系统闹钟音量",()->setting(new Intent(Settings.ACTION_SOUND_SETTINGS)));
        Ui.text(this,system,"一加 13T／Ace：在助手的应用信息与电池管理中允许后台活动、自启动；如有锁屏通知、后台弹出或悬浮窗限制也需开启。不同系统版本名称可能不同，这些厂商开关需手动核对。",12,Ui.MUTED);
        Ui.button(this,system,"考勤辅助操作权限",()->new AlertDialog.Builder(this).setMessage("仅在考勤任务时段识别飞书页面，尝试一次打卡入口。不会代拍、替换照片或自动提交。").setNegativeButton("返回",null).setPositiveButton("打开无障碍设置",(d,w)->setting(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show());
        Ui.button(this,system,"系统日期与时区",()->setting(new Intent(Settings.ACTION_DATE_SETTINGS)));
        Ui.text(this,system,"当前时区："+ZoneId.systemDefault().getId()+"\n按设备时间调度。已过的时间会安排到下一次。",12,Ui.MUTED);
        Ui.button(this,content,"暂停全部提醒",this::pauseAll);
        Ui.text(this,content,"飞书提醒助手  ·  v"+appVersion()+"\n本机运行，设置自动保存。",12,Ui.MUTED);
        saveText();
    }
    private int index(int[] values,int value) { for(int i=0;i<values.length;i++) if(values[i]==value) return i; return 0; }
    private String permissionText() {
        NotificationManager nm=getSystemService(NotificationManager.class); NotificationChannel channel=nm.getNotificationChannel(ReminderService.CHANNEL);
        AudioManager audio=getSystemService(AudioManager.class);
        return flag(nm.areNotificationsEnabled(),"通知")+"  "+flag(AlarmScheduler.exactAllowed(this),"精准闹钟")+"\n"+
                flag(Settings.canDrawOverlays(this),"悬浮提醒")+"  "+flag(Build.VERSION.SDK_INT<34||nm.canUseFullScreenIntent(),"全屏提醒")+"\n"+
                flag(getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName()),"系统后台保护")+"  "+flag(channel==null||channel.getImportance()>=NotificationManager.IMPORTANCE_HIGH,"高优先级提醒")+"\n"+
                "系统闹钟音量："+audio.getStreamVolume(AudioManager.STREAM_ALARM)+" / "+audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)+(store.sound() ? "" : " · 助手铃声已关闭");
    }
    private String flag(boolean on,String label) { return (on ? "✓ " : "○ ")+label; }
    private void pauseAll() { store.prefs.edit().putBoolean("enabled",false).apply(); store.stopAutomation(); AlarmScheduler.reschedule(this); AlarmScheduler.cancelSnoozes(this); stopService(new Intent(this,ReminderService.class)); getSystemService(NotificationManager.class).cancel(ReminderService.NOTIFICATION); showTab(tab); toast("所有提醒已暂停"); }
    private void watch(EditText edit,String key) {
        edit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { store.prefs.edit().putString(key,s.toString()).apply(); if(saveStatus!=null) saveStatus.setText("输入中…稍后自动保存"); handler.removeCallbacks(textSave); handler.postDelayed(textSave,700); }
            public void afterTextChanged(Editable e) {}
        });
    }
    private void saveText() {
        handler.removeCallbacks(textSave); if(link==null) return;
        String target=link.getText().toString().trim(); boolean validLink=FeishuLauncher.validLink(target);
        link.setError(validLink ? null : "请填写 applink.feishu.cn 的完整飞书链接");
        if(validLink&&!target.equals(store.link())) { store.prefs.edit().putString("link",target).apply(); store.stopAutomation(); }
        Set<String> restDates=readDates(rest),workDates=readDates(work); boolean validDates=restDates!=null&&workDates!=null;
        if(validDates) { Set<String> overlap=new HashSet<>(restDates); overlap.retainAll(workDates); if(!overlap.isEmpty()) { validDates=false; rest.setError("同一天不能同时设为休假和补班"); work.setError("同一天不能同时设为休假和补班"); } }
        if(validDates&&(!restDates.equals(store.prefs.getStringSet("rest",Collections.emptySet()))||!workDates.equals(store.prefs.getStringSet("work",Collections.emptySet())))) {
            store.prefs.edit().putStringSet("rest",restDates).putStringSet("work",workDates).apply(); AlarmScheduler.reschedule(this);
        }
        if(saveStatus!=null) saveStatus.setText(validLink&&validDates ? "✓ 所有修改已自动保存" : "草稿已保存；无效输入暂时沿用上次有效设置。");
    }
    private Set<String> readDates(EditText edit) { try { Set<String> dates=new HashSet<>(); for(String value:edit.getText().toString().trim().split("[,，\\s]+")) if(!value.isEmpty()) dates.add(LocalDate.parse(value).toString()); edit.setError(null); return dates; } catch(RuntimeException e) { edit.setError("格式如 2026-10-03"); return null; } }
    private static String dateText(Set<LocalDate> dates) { List<LocalDate> sorted=new ArrayList<>(dates); Collections.sort(sorted); List<String> text=new ArrayList<>(); for(LocalDate day:sorted) text.add(day.toString()); return String.join(", ",text); }
    void testAlarm(String key) {
        saveText();
        long delay=10_000;
        Task task=new Task("TEST",System.currentTimeMillis()+delay,key);
        if(!FeishuLauncher.validLink(store.link(task))) { toast("请先补全飞书入口"); return; }
        if(!AlarmScheduler.exactAllowed(this)||!getSystemService(NotificationManager.class).areNotificationsEnabled()) { toast("请先开启通知和精准闹钟权限"); return; }
        try { AlarmScheduler.schedule(this,task,task.due,12); toast("已安排 "+delay/1000+" 秒测试，可息屏等待"); } catch(RuntimeException e) { toast("测试未能安排，请检查精准闹钟权限"); }
    }
    private String appVersion() { try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; } catch(PackageManager.NameNotFoundException e) { return ""; } }
    static String modeLabel(int mode) { return mode==0 ? "仅提醒" : mode==2 ? "考勤辅助" : "打开飞书页面"; }
    static String stateLabel(String state) { switch(state) { case "confirmed": case "user_confirmed": return "已完成"; case "skipped": return "本次已跳过"; case "clicked": return "等待确认"; case "uncertain": return "请核实"; case "waiting": return "已提醒"; default: return "待提醒"; } }
    private void setting(Intent intent) { try { startActivity(intent); } catch(RuntimeException e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); } }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_SHORT).show(); }
}

