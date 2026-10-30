package cn.fangq.daka;
import android.app.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
public class TaskEditorActivity extends Activity {
    private Store store;
    private TaskConfig config;
    private EditText name,link;
    private TextView status,nextText;
    private Button time;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable saveText=()->saveFields();
    private boolean deleted;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); store=new Store(this); config=store.config(getIntent().getStringExtra("key"));
        if(config==null) { finish(); return; }
        LinearLayout page=Ui.page(this);
        Ui.button(this,page,"‹ 返回任务列表",this::finish);
        Ui.heading(this,page,"编辑任务",28);
        Ui.text(this,page,"每个改动都会自动保存。",14,Ui.MUTED);
        LinearLayout basic=Ui.card(this,page,"任务信息");
        name=Ui.field(this,basic,"任务名称",config.draftTitle,"任务名称"); name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        time=Ui.button(this,basic,config.time,this::chooseTime); time.setTextSize(36); time.setContentDescription("任务提醒时间"); time.setLayoutParams(new LinearLayout.LayoutParams(-1,Ui.dp(this,76)));
        nextText=Ui.text(this,basic,"",12,Ui.MUTED);
        Ui.toggle(this,basic,"启用这项任务",config.enabled,on->{ config.enabled=on; persist(true); if(!on) ReminderService.cancelConfig(this,config.key); });
        LinearLayout repeat=Ui.card(this,page,"重复日期");
        LinearLayout days=Ui.row(this,repeat); String[] labels={"一","二","三","四","五","六","日"};
        for(int i=0;i<7;i++) {
            final DayOfWeek day=DayOfWeek.of(i+1); ToggleButton button=new ToggleButton(this);
            button.setTextOn(labels[i]); button.setTextOff(labels[i]); button.setTextSize(14); button.setChecked(config.days.contains(day)); button.setContentDescription("重复周"+labels[i]);
            button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(0,0,0,0); button.setTextColor(button.isChecked() ? android.graphics.Color.WHITE : Ui.MUTED);
            button.setBackground(Ui.round(button.isChecked() ? Ui.GREEN : Ui.BG,Ui.dp(this,9))); days.addView(button); Ui.weighted(button,1,this);
            button.setOnCheckedChangeListener((v,on)-> { if(on) config.days.add(day); else config.days.remove(day); button.setTextColor(on ? android.graphics.Color.WHITE : Ui.MUTED); button.setBackground(Ui.round(on ? Ui.GREEN : Ui.BG,Ui.dp(this,9))); persist(true); });
        }
        Ui.toggle(this,repeat,"遵循休假／补班规则",config.calendarRules,on->{ config.calendarRules=on; persist(true); });
        LinearLayout action=Ui.card(this,page,"到点执行");
        Ui.select(this,action,"执行方式",new String[]{"跟随默认设置","仅提醒","打开飞书页面","辅助进入考勤入口"},config.mode+1,index->{ if(config.mode!=index-1) { config.mode=index-1; store.stopAutomation(); persist(false); } });
        link=Ui.field(this,action,"独立飞书入口 · 留空使用默认",config.draftLink,"任务飞书入口"); link.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        Ui.select(this,action,"任务类型",new String[]{"普通提醒／日报","上班拍照打卡","下班拍照打卡"},"IN".equals(config.attendance) ? 1 : "OUT".equals(config.attendance) ? 2 : 0,index->{ String type=index==1 ? "IN" : index==2 ? "OUT" : ""; if(!type.equals(config.attendance)) { config.attendance=type; store.stopAutomation(); persist(false); } });
        Ui.text(this,action,"普通任务只打开页面。考勤辅助需选打卡类型并授权无障碍；拍照与提交由你完成。",12,Ui.MUTED);
        status=Ui.text(this,page,"✓ 已自动保存",12,Ui.GREEN); watch(name); watch(link);
        Ui.button(this,page,"10 秒后测试此任务",()-> { saveFields(); if(!FeishuLauncher.validLink(store.link(new Task("TEST",System.currentTimeMillis(),config.key)))) { Toast.makeText(this,"请先补全飞书入口",Toast.LENGTH_SHORT).show(); return; } if(!AlarmScheduler.exactAllowed(this)||!getSystemService(NotificationManager.class).areNotificationsEnabled()) { Toast.makeText(this,"请先开启通知与精准闹钟权限",Toast.LENGTH_SHORT).show(); return; } long due=System.currentTimeMillis()+10_000; try { AlarmScheduler.schedule(this,new Task("TEST",due,config.key),due,12); Toast.makeText(this,"10 秒后测试，可返回桌面等待",Toast.LENGTH_SHORT).show(); } catch(RuntimeException e) { Toast.makeText(this,"测试未能安排",Toast.LENGTH_SHORT).show(); } },true);
        Button delete=Ui.button(this,page,"删除这项任务",()->new AlertDialog.Builder(this).setTitle("删除任务？").setMessage("“"+config.title+"”的后续提醒将被取消。").setNegativeButton("保留",null).setPositiveButton("删除",(d,w)-> { deleted=true; handler.removeCallbacks(saveText); store.deleteTask(config.key); store.stopAutomation(); AlarmScheduler.cancelTask(this,config.key); AlarmScheduler.reschedule(this); finish(); }).show()); delete.setTextColor(Ui.RED);
        saveFields(); refreshNext();
    }
    private void chooseTime() {
        LocalTime selected=LocalTime.parse(config.time);
        new TimePickerDialog(this,(v,h,m)-> { config.time=String.format(Locale.ROOT,"%02d:%02d",h,m); time.setText(config.time); persist(true); },selected.getHour(),selected.getMinute(),true).show();
    }
    private void watch(EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                config.draftTitle=name.getText().toString(); config.draftLink=link.getText().toString(); store.saveTask(config);
                status.setText("输入中…稍后自动保存"); handler.removeCallbacks(saveText); handler.postDelayed(saveText,700);
            }
            public void afterTextChanged(Editable text) {}
        });
    }
    private void saveFields() {
        handler.removeCallbacks(saveText); if(deleted||link==null) return;
        config.draftTitle=name.getText().toString(); config.draftLink=link.getText().toString();
        String title=config.draftTitle.trim(),target=config.draftLink.trim(); boolean titleValid=!title.isEmpty(),linkValid=target.isEmpty()||FeishuLauncher.validLink(target);
        name.setError(titleValid ? null : "请填写任务名称"); link.setError(linkValid ? null : "请填写完整飞书链接；留空使用默认入口");
        if(titleValid) config.title=title;
        if(linkValid&&!target.equals(config.link)) { config.link=target; store.stopAutomation(); }
        persist(false); status.setText(titleValid&&linkValid ? "✓ 修改已自动保存" : "草稿已保存，无效项暂时沿用上次有效值。");
    }
    private void persist(boolean schedule) { if(deleted) return; store.saveTask(config); if(schedule) AlarmScheduler.reschedule(this); refreshNext(); }
    private void refreshNext() {
        if(nextText==null) return;
        long next=store.prefs.getLong("next_"+config.key,0);
        nextText.setText(!store.enabled() ? "提醒总开关已暂停，请在概览页开启。" : !config.enabled ? "这项任务已停用。" : next==0 ? "请选择重复日期，并检查精准闹钟权限。" : "下次 "+Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd E HH:mm",Locale.CHINA)));
    }
    @Override protected void onPause() { saveFields(); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}

