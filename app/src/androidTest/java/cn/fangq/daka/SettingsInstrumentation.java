package cn.fangq.daka;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;
import java.lang.reflect.Field;
import java.time.*;
import java.util.*;
public class SettingsInstrumentation extends Instrumentation {
    private int checks;
    private Bundle args;
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args=args; start(); }
    @Override public void onStart() {
        Bundle result=new Bundle(); Map<String,?> backup=null; Store store=new Store(getTargetContext()); MainActivity main=null;
        try {
            String scenario=args.getString("scenario","");
            if("prepare".equals(scenario)) { prepare(store); result.putString("result","PASS prepared two simultaneous custom tasks"); finish(Activity.RESULT_OK,result); return; }
            if("restore".equals(scenario)) { restore(store); result.putString("result","PASS restored user settings"); finish(Activity.RESULT_OK,result); return; }
            if("screen".equals(scenario)||"repeat".equals(scenario)) { prepareSingle(store,"screen".equals(scenario)); result.putString("result","PASS prepared single alarm"); finish(Activity.RESULT_OK,result); return; }
            if("rearm".equals(scenario)) { rearm(store); result.putString("result","PASS completed first occurrence and rearmed same task"); finish(Activity.RESULT_OK,result); return; }
            backup=new HashMap<>(store.prefs.getAll());
            store.prefs.edit().remove("task_configs").putString("time_IN","09:17").putString("time_OUT","18:41").putStringSet("weekdays",new HashSet<>(Arrays.asList("2","6"))).putBoolean("enabled",false).apply();
            store=new Store(getTargetContext()); final Store checkedStore=store;
            check(store.tasks().size()==2,"Migrate exactly two existing tasks");
            check("09:17".equals(store.config("IN").time)&&"18:41".equals(store.config("OUT").time),"Preserve legacy times");
            check(store.config("IN").days.equals(EnumSet.of(DayOfWeek.TUESDAY,DayOfWeek.SATURDAY)),"Preserve legacy repeats");
            check(!store.prefs.contains("log"),"Remove persistent running log");
            main=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitForIdleSync(); final MainActivity activity=main;
            runOnMainSync(()-> { activity.showTab(0); Switch master=find(activity.getWindow().getDecorView(),Switch.class,"提醒总开关"); master.setChecked(true); });
            check(store.enabled(),"Master switch autosaves");
            Instrumentation.ActivityMonitor monitor=addMonitor(TaskEditorActivity.class.getName(),null,false);
            runOnMainSync(()-> { activity.showTab(1); activity.addTask(); });
            TaskEditorActivity editor=(TaskEditorActivity)monitor.waitForActivityWithTimeout(5000); removeMonitor(monitor);
            if(editor==null) throw new AssertionError("Add task did not open editor"); waitForIdleSync();
            TaskConfig config=(TaskConfig)field(editor,"config"); String key=config.key;
            check(store.tasks().size()==3&&!store.config(key).enabled,"New task persists and starts disabled");
            EditText title=(EditText)field(editor,"name"),link=(EditText)field(editor,"link");
            String custom="feishu://applink.feishu.cn/client/web_app/open?appId=qa_independent";
            runOnMainSync(()-> { title.setText("日报提醒"); link.setText(custom); });
            SystemClock.sleep(900); waitForIdleSync();
            check("日报提醒".equals(store.config(key).title),"Name autosaves");
            check(custom.equals(store.config(key).link),"Independent entry autosaves");
            runOnMainSync(()-> { link.setText("feishu://"); Switch enabled=find(editor.getWindow().getDecorView(),Switch.class,"启用这项任务"); enabled.setChecked(true); });
            check(store.config(key).enabled,"Invalid link does not block enable switch");
            check(store.prefs.getLong("next_"+key,0)>System.currentTimeMillis(),"Enabling custom task schedules it");
            SystemClock.sleep(900); waitForIdleSync();
            check(custom.equals(store.config(key).link)&&"feishu://".equals(store.config(key).draftLink),"Invalid draft does not replace effective entry");
            TaskConfig second=TaskConfig.create(); second.title="日报提醒"; checkedStore.saveTask(second); AlarmScheduler.reschedule(getTargetContext());
            check(!AlarmScheduler.pending(getTargetContext(),new Task(key,1000),10).equals(AlarmScheduler.pending(getTargetContext(),new Task(second.key,1000),10)),"Equal names and request codes must not collide");
            runOnMainSync(()-> { Switch enabled=find(editor.getWindow().getDecorView(),Switch.class,"启用这项任务"); enabled.setChecked(false); editor.finish(); }); waitForIdleSync();
            check(store.prefs.getLong("next_"+key,0)==0,"Disabling removes custom scheduled occurrence");
            runOnMainSync(()->activity.showTab(2));
            EditText defaultLink=(EditText)field(activity,"link"),rest=(EditText)field(activity,"rest");
            runOnMainSync(()-> { defaultLink.setText(custom); rest.setText("2026-10-03"); });
            SystemClock.sleep(900); waitForIdleSync();
            check(custom.equals(store.link()),"Default link autosaves");
            check(store.prefs.getStringSet("rest",Collections.emptySet()).contains("2026-10-03"),"Calendar rules autosave");
            runOnMainSync(()-> { rest.setText("2026-10-"); Switch sound=find(activity.getWindow().getDecorView(),Switch.class,"播放闹钟铃声"); sound.setChecked(false); });
            check(!store.sound(),"Other setting saves despite invalid date");
            runOnMainSync(()-> { activity.showTab(1); activity.showTab(2); });
            check("2026-10-".equals(((EditText)field(activity,"rest")).getText().toString()),"Invalid draft survives page changes");
            check(store.prefs.getStringSet("rest",Collections.emptySet()).contains("2026-10-03"),"Effective calendar remains valid");
            // Storage/API seam for deletion and absence of recreation on upgrade.
            checkedStore.deleteTask(second.key); AlarmScheduler.cancelTask(getTargetContext(),second.key); AlarmScheduler.reschedule(getTargetContext());
            check(store.config(second.key)==null&&store.prefs.getLong("next_"+second.key,0)==0,"Deleting cancels the task");
            check(store.tasks().size()==3,"Deleting one task preserves its peers");
            // Reproduce editing a task after completing today's first occurrence.
            TaskConfig changed=store.config(key); changed.enabled=true; long firstDue=System.currentTimeMillis();
            store.state(new Task(key,firstDue).id,"user_confirmed","QA first occurrence completed");
            changed.time=LocalTime.now().plusMinutes(2).withSecond(0).withNano(0).toString();
            checkedStore.saveTask(changed); AlarmScheduler.reschedule(getTargetContext());
            check(!AutomationPolicy.terminal(store.state(new Task(changed,firstDue+120_000).id)),"Changed time must rearm a completed task on the same day");
            check("user_confirmed".equals(store.state(new Task(key,firstDue).id)),"Preserve the previous occurrence's completed state");
            check(!store.active(new Task(key,firstDue)),"Reject stale alarms after time changes");
            check(store.active(new Task(changed,firstDue+120_000)),"Accept rearmed occurrence after time changes");
            long revision=changed.revision; checkedStore.saveTask(changed);
            check(store.config(key).revision==revision,"Autosave without changing time must not rearm");
            check(new Task(changed,firstDue+120_000).id.equals(Task.from(new Task(changed,firstDue+120_000).put(new Intent())).id),"Intent preserves revised occurrence identity");
            runOnMainSync(()->activity.showTab(0));
            TextView today=find(activity.getWindow().getDecorView(),TextView.class,changed.title+"  ·  "+changed.time);
            check(today!=null&&"待提醒".contentEquals(((TextView)((ViewGroup)today.getParent()).getChildAt(1)).getText()),"Overview shows the rearmed task as pending");
            Task revised=new Task(changed,firstDue+120_000);
            store.state(revised.id,"skipped","QA revised occurrence skipped");
            changed.time=LocalTime.parse(changed.time).plusMinutes(1).toString(); checkedStore.saveTask(changed); AlarmScheduler.reschedule(getTargetContext());
            check("new".equals(store.state(new Task(changed,firstDue+180_000).id)),"Changing a skipped occurrence rearms it too");
            ActionReceiver.handle(getTargetContext(),"confirmed",revised);
            check("new".equals(store.state(new Task(changed,firstDue+180_000).id)),"Stale notification actions cannot finish the new occurrence");
            if("qa-only".equals(getTargetContext().getPackageManager().getPackageInfo(FeishuLauncher.PACKAGE,0).versionName)) {
                // A short ringtone must not cut off a photo task's observation window.
                store.prefs.edit().putBoolean("sound",false).putBoolean("vibration",false).putInt("ring_seconds",15).apply();
                TaskConfig photo=TaskConfig.create(); photo.title="QA photo queue"; photo.mode=2; photo.attendance="IN"; photo.link=custom;
                TaskConfig queued=TaskConfig.create(); queued.title="QA queued ordinary"; queued.mode=1; queued.link=custom;
                store.saveTask(photo); store.saveTask(queued);
                Task photoTask=new Task(photo,System.currentTimeMillis()),queuedTask=new Task(queued,System.currentTimeMillis());
                runOnMainSync(()-> {
                    getTargetContext().startForegroundService(photoTask.put(new Intent(getTargetContext(),ReminderService.class)));
                    getTargetContext().startForegroundService(queuedTask.put(new Intent(getTargetContext(),ReminderService.class)));
                });
                waitForIdleSync(); SystemClock.sleep(17_000);
                check(ReminderService.isCurrent(photoTask.id)&&!ReminderService.isCurrent(queuedTask.id),"Photo observation remains active after the short ringtone with another task queued");
                runOnMainSync(()->ActionReceiver.handle(getTargetContext(),"confirmed",photoTask)); waitForIdleSync();
                check(ReminderService.isCurrent(queuedTask.id),"Completing photo task advances its queued peer");
            }
            result.putString("result","PASS: "+checks+" task/settings checks");
        } catch(Throwable e) { result.putString("result","FAIL after "+checks+": "+android.util.Log.getStackTraceString(e)); }
        finally {
            if(backup!=null) {
                if(main!=null) { final MainActivity closing=main; runOnMainSync(closing::finish); waitForIdleSync(); }
                getTargetContext().stopService(new Intent(getTargetContext(),ReminderService.class));
                for(TaskConfig task:store.tasks()) AlarmScheduler.cancelTask(getTargetContext(),task.key);
                SharedPreferences.Editor edit=store.prefs.edit().clear();
                for(Map.Entry<String,?> row:backup.entrySet()) {
                    Object value=row.getValue(); String key=row.getKey();
                    if(value instanceof String) edit.putString(key,(String)value); else if(value instanceof Boolean) edit.putBoolean(key,(Boolean)value);
                    else if(value instanceof Integer) edit.putInt(key,(Integer)value); else if(value instanceof Long) edit.putLong(key,(Long)value);
                    else if(value instanceof Float) edit.putFloat(key,(Float)value); else if(value instanceof Set) edit.putStringSet(key,new HashSet<>((Set<String>)value));
                }
                edit.commit(); new Store(getTargetContext()); AlarmScheduler.reschedule(getTargetContext());
            }
        }
        finish(result.getString("result","").startsWith("PASS") ? Activity.RESULT_OK : Activity.RESULT_CANCELED,result);
    }
    private void prepare(Store store) throws JSONException {
        if(store.prefs.contains("qa_backup")) throw new IllegalStateException("Restore previous QA run first");
        JSONObject backup=new JSONObject().put("configs",store.prefs.getString("task_configs","[]")).put("enabled",store.enabled()).put("sound",store.sound()).put("vibration",store.vibration()).put("ring",store.ringSeconds());
        store.prefs.edit().putString("qa_backup",backup.toString()).putBoolean("enabled",true).putBoolean("sound",false).putBoolean("vibration",false).putInt("ring_seconds",15).apply();
        List<TaskConfig> tasks=store.tasks(); for(TaskConfig task:tasks) task.enabled=false; store.saveTasks(tasks);
        long due=System.currentTimeMillis()+10_000;
        for(String label:new String[]{"alpha","beta"}) {
            TaskConfig task=TaskConfig.create(); task.title="QA "+label; task.enabled=true; task.mode=1; task.link="feishu://applink.feishu.cn/client/web_app/open?appId=qa_"+label; task.draftLink=task.link;
            store.saveTask(task);
        }
        AlarmScheduler.reschedule(getTargetContext());
        for(TaskConfig task:store.tasks()) if(task.title.startsWith("QA ")) AlarmScheduler.schedule(getTargetContext(),new Task(task.key,due),due,10);
        // Instrumentation can terminate its process without Activity lifecycle flushing.
        store.prefs.edit().putLong("qa_due",due).commit();
    }
    private void restore(Store store) throws JSONException {
        JSONObject backup=new JSONObject(store.prefs.getString("qa_backup",""));
        getTargetContext().stopService(new Intent(getTargetContext(),ReminderService.class));
        for(TaskConfig task:store.tasks()) if(task.title.startsWith("QA ")) { AlarmScheduler.cancelTask(getTargetContext(),task.key); }
        store.prefs.edit().putString("task_configs",backup.getString("configs")).putBoolean("enabled",backup.getBoolean("enabled"))
                .putBoolean("sound",backup.getBoolean("sound")).putBoolean("vibration",backup.getBoolean("vibration")).putInt("ring_seconds",backup.getInt("ring")).remove("qa_backup").remove("qa_due").commit();
        AlarmScheduler.reschedule(getTargetContext());
    }
    private void prepareSingle(Store store,boolean sound) throws JSONException {
        prepare(store);
        TaskConfig alpha=null;
        for(TaskConfig config:store.tasks()) {
            if(config.title.equals("QA beta")) { config.enabled=false; store.saveTask(config); AlarmScheduler.cancelTask(getTargetContext(),config.key); }
            if(config.title.equals("QA alpha")) alpha=config;
        }
        long due=System.currentTimeMillis()+25_000;
        AlarmScheduler.schedule(getTargetContext(),new Task(alpha,due),due,10);
        store.prefs.edit().putBoolean("sound",sound).putBoolean("vibration",sound).putInt("ring_seconds",15).putLong("qa_due",due).commit();
    }
    private void rearm(Store store) {
        TaskConfig config=null; for(TaskConfig candidate:store.tasks()) if(candidate.title.equals("QA alpha")) config=candidate;
        if(config==null) throw new AssertionError("Missing first occurrence");
        Task first=new Task(config,store.prefs.getLong("qa_due",0));
        if(!"waiting".equals(store.state(first.id))) throw new AssertionError("First alarm did not trigger");
        ActionReceiver.handle(getTargetContext(),"confirmed",first);
        config.time=LocalTime.parse(config.time).plusMinutes(1).toString();
        config.link="feishu://applink.feishu.cn/client/web_app/open?appId=qa_repeat_second"; config.draftLink=config.link;
        store.saveTask(config); AlarmScheduler.reschedule(getTargetContext());
        long due=System.currentTimeMillis()+15_000; Task second=new Task(config,due);
        if(!AutomationPolicy.terminal(store.state(first.id))||!"new".equals(store.state(second.id))||store.active(first)) throw new AssertionError("Rearmed occurrence is not independent");
        AlarmScheduler.schedule(getTargetContext(),second,due,10); store.prefs.edit().putLong("qa_due",due).commit();
    }
    private static Object field(Object target,String name) { try { Field f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); } catch(Exception e) { throw new IllegalStateException(e); } }
    private static <T extends TextView> T find(View view,Class<T> type,String text) {
        if(type.isInstance(view)&&((TextView)view).getText().toString().startsWith(text)) return type.cast(view);
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { T found=find(((ViewGroup)view).getChildAt(i),type,text); if(found!=null) return found; }
        return null;
    }
    private void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
}

