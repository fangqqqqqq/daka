package cn.fangq.daka;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
public class ReminderService extends Service {
    static final int NOTIFICATION=100;
    static final String CHANNEL="attendance_alarm";
    private static ReminderService current;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayDeque<Task> pending=new ArrayDeque<>();
    private Task task;
    private Ringtone ringtone;
    private ToneGenerator fallbackTone;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocus;
    private Vibrator vibrator;
    private View overlay;
    private LinearLayout overlayActions;
    private Button overlayToggle;
    private TextView overlayDetail;
    private boolean overlayExpanded;
    private boolean launchAttempted;
    private long end,audioEnd;
    private PowerManager.WakeLock wake;
    @Override public void onCreate() {
        super.onCreate(); current=this;
        NotificationChannel channel=new NotificationChannel(CHANNEL,"任务提醒",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("按设定时间提醒并打开飞书页面"); channel.setSound(null,null); channel.enableVibration(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        Task incoming=Task.from(intent); Store store=new Store(this);
        if(incoming==null||!store.active(incoming)||AutomationPolicy.terminal(store.state(incoming.id))) { AlarmWakeLock.release(); if(task==null) stopSelf(); return START_NOT_STICKY; }
        if(task!=null) {
            if(task.id.equals(incoming.id)) { AlarmWakeLock.release(); return START_NOT_STICKY; }
            for(Task waiting:pending) if(waiting.id.equals(incoming.id)) { AlarmWakeLock.release(); return START_NOT_STICKY; }
            pending.add(incoming); store.state(incoming.id,"waiting",store.title(incoming)+"等待提醒");
            getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(detail(),true));
        } else begin(incoming);
        AlarmWakeLock.release();
        return START_NOT_STICKY;
    }
    private void begin(Task next) {
        stopAudio(); releaseWake(); removeOverlay(); handler.removeCallbacksAndMessages(null);
        task=next; launchAttempted=false; Store store=new Store(this); audioEnd=System.currentTimeMillis()+store.ringSeconds()*1000L;
        // Ring duration controls sound; keep the reminder available while the user unlocks.
        end=System.currentTimeMillis()+120_000;
        if("new".equals(store.state(task.id))) store.state(task.id,"waiting",store.title(task)+"提醒已触发");
        store.prefs.edit().putString("notification_task",task.id).putString("notification_config",task.configKey).apply();
        startForeground(NOTIFICATION,notification(detail(),true));
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"daka:alarm"); wake.acquire(125_000);
        try {
            if(store.sound()) {
                audioManager=getSystemService(AudioManager.class);
                AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
                audioFocus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).setOnAudioFocusChangeListener(change->{},handler).build();
                audioManager.requestAudioFocus(audioFocus);
                ringtone=RingtoneManager.getRingtone(this,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
                if(ringtone!=null) {
                    ringtone.setAudioAttributes(attributes);
                    if(Build.VERSION.SDK_INT>=28) { ringtone.setLooping(true); ringtone.setVolume(1f); } ringtone.play();
                }
                if(ringtone==null||!ringtone.isPlaying()) startFallbackTone();
            }
        } catch(RuntimeException e) { store.log("系统铃声不可用，尝试备用提示音"); if(store.sound()) startFallbackTone(); }
        try { if(store.vibration()) { vibrator=getSystemService(Vibrator.class); if(vibrator!=null) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,500,700},0)); } }
        catch(RuntimeException e) { store.log("振动未能启动"); }
        showOverlay(); handler.post(tick);
    }
    private void startFallbackTone() {
        if(fallbackTone!=null) return;
        try { fallbackTone=new ToneGenerator(AudioManager.STREAM_ALARM,90); handler.post(beep); }
        catch(RuntimeException e) { new Store(this).log("备用提示音不可用，请检查系统闹钟音量"); }
    }
    private final Runnable beep=new Runnable() { public void run() { if(fallbackTone!=null) { fallbackTone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,650); handler.postDelayed(this,1300); } } };
    private String detail() { return (task.test() ? "测试只打开页面，不点击打卡" : "到点了，请打开飞书完成任务")+(pending.isEmpty() ? "" : " · 还有 "+pending.size()+" 项等待"); }
    private final Runnable tick=new Runnable() {
        public void run() {
            if(task==null) return; Store store=new Store(ReminderService.this);
            if(!store.active(task)||AutomationPolicy.terminal(store.state(task.id))) { advance(false); return; }
            if(System.currentTimeMillis()>=audioEnd) {
                stopAudio();
                boolean photoObservation=!task.test()&&store.mode(task)==2&&!store.attendance(task).isEmpty();
                if(!pending.isEmpty()&&!photoObservation) { advance(false); return; }
            }
            if(System.currentTimeMillis()>=end) {
                getSystemService(NotificationManager.class).notify(NOTIFICATION,notification("提醒已结束，可打开页面或确认完成",false));
                advance(true); return;
            }
            if(!launchAttempted&&!FeishuLauncher.locked(ReminderService.this)&&store.mode(task)>=1) {
                launchAttempted=true; if(overlay==null) showOverlay();
                Context launcher=FeishuAccessibilityService.launchContext();
                if(FeishuLauncher.open(launcher==null ? ReminderService.this : launcher,task,!task.test())) collapseOverlay();
            }
            handler.postDelayed(this,1000);
        }
    };
    private void advance(boolean timeout) {
        stopAudio(); releaseWake(); removeOverlay(); handler.removeCallbacksAndMessages(null);
        if(!timeout || !pending.isEmpty()) new Store(this).stopAutomation();
        task=null;
        Store store=new Store(this);
        while(!pending.isEmpty()) {
            Task next=pending.remove(); if(store.active(next)&&!AutomationPolicy.terminal(store.state(next.id))) { begin(next); return; }
        }
        stopForeground(timeout ? STOP_FOREGROUND_DETACH : STOP_FOREGROUND_REMOVE); stopSelf();
    }
    Notification notification(String detail,boolean fullscreen) {
        Store store=new Store(this);
        Intent open=task.put(new Intent(this,AlarmActivity.class)).setData(Uri.parse("daka://notification/"+task.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent activity=PendingIntent.getActivity(this,100,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_alarm).setContentTitle(store.title(task))
                .setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail)).setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(activity).setOngoing(fullscreen).setOnlyAlertOnce(true);
        if(fullscreen) b.setFullScreenIntent(activity,true);
        b.addAction(new Notification.Action.Builder(null,"稍后 "+store.snoozeMinutes()+" 分钟",action("snooze",101)).build());
        b.addAction(new Notification.Action.Builder(null,"跳过本次",action("skip",102)).build());
        b.addAction(new Notification.Action.Builder(null,"已完成",action("confirmed",103)).build()); return b.build();
    }
    private PendingIntent action(String action,int code) {
        Intent intent=task.put(new Intent(this,ActionReceiver.class).setAction(action)).setData(Uri.parse("daka://action/"+task.id+"/"+action));
        return PendingIntent.getBroadcast(this,code,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private void showOverlay() {
        if(overlay!=null||FeishuLauncher.locked(this)||!Settings.canDrawOverlays(this)) return;
        Store store=new Store(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,8)); box.setBackground(Ui.round(Ui.INK,Ui.dp(this,18)));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); box.addView(top);
        TextView title=new TextView(this); title.setText(store.title(task)); title.setTextColor(Color.WHITE); title.setTextSize(18); top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        overlayToggle=new Button(this); overlayToggle.setText("收起"); overlayToggle.setOnClickListener(v->setOverlayExpanded(!overlayExpanded)); top.addView(overlayToggle);
        overlayActions=new LinearLayout(this); overlayActions.setOrientation(LinearLayout.VERTICAL); box.addView(overlayActions);
        overlayDetail=new TextView(this); overlayDetail.setText(detail()); overlayDetail.setTextColor(0xffb3d4c9); overlayDetail.setPadding(0,8,0,8); overlayActions.addView(overlayDetail);
        Button open=new Button(this); open.setText("打开飞书页面"); open.setOnClickListener(v->{ startActivity(task.put(new Intent(this,AlarmActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); collapseOverlay(); }); overlayActions.addView(open);
        Button later=new Button(this); later.setText("稍后 "+store.snoozeMinutes()+" 分钟"); later.setOnClickListener(v->ActionReceiver.handle(this,"snooze",task)); overlayActions.addView(later);
        Button done=new Button(this); done.setText("我已在飞书完成"); done.setOnClickListener(v->ActionReceiver.handle(this,"confirmed",task)); overlayActions.addView(done);
        Button skip=new Button(this); skip.setText("跳过本次提醒"); skip.setOnClickListener(v->ActionReceiver.handle(this,"skip",task)); overlayActions.addView(skip);
        overlayExpanded=true;
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP; params.y=Ui.dp(this,16);
        try { getSystemService(WindowManager.class).addView(box,params); overlay=box; } catch(RuntimeException e) { store.log("悬浮提醒不可用"); }
    }
    private void setOverlayExpanded(boolean expanded) { overlayExpanded=expanded; if(overlayActions!=null) overlayActions.setVisibility(expanded ? View.VISIBLE : View.GONE); if(overlayToggle!=null) overlayToggle.setText(expanded ? "收起" : "展开"); }
    private void collapseOverlay() { setOverlayExpanded(false); }
    void removeOverlay() { if(overlay!=null) { try { getSystemService(WindowManager.class).removeView(overlay); } catch(RuntimeException ignored) {} overlay=null; } overlayActions=null; overlayToggle=null; overlayDetail=null; }
    void stopAudio() {
        handler.removeCallbacks(beep);
        if(fallbackTone!=null) { fallbackTone.release(); fallbackTone=null; }
        if(ringtone!=null) { ringtone.stop(); ringtone=null; }
        if(vibrator!=null) { vibrator.cancel(); vibrator=null; }
        if(audioManager!=null&&audioFocus!=null) audioManager.abandonAudioFocusRequest(audioFocus);
        audioFocus=null; audioManager=null;
    }
    private void releaseWake() { if(wake!=null&&wake.isHeld()) wake.release(); wake=null; }
    static boolean isCurrent(String id) { return current!=null&&current.task!=null&&current.task.id.equals(id); }
    static void quiet(Context c,Task task) { if(isCurrent(task.id)) { current.launchAttempted=true; current.stopAudio(); current.showOverlay(); current.collapseOverlay(); } }
    static void cancelConfig(Context c,String key) {
        if(current==null) return;
        if(Looper.myLooper()!=Looper.getMainLooper()) { current.handler.post(()->cancelConfig(c,key)); return; }
        current.pending.removeIf(task->task.configKey.equals(key));
        if(current.task!=null&&current.task.configKey.equals(key)) current.advance(false);
    }
    static void finishTask(Context c,Task task) {
        if(current!=null) current.pending.removeIf(queued->queued.id.equals(task.id));
        if(isCurrent(task.id)) current.advance(false);
        if(task.id.equals(new Store(c).prefs.getString("notification_task",""))) c.getSystemService(NotificationManager.class).cancel(NOTIFICATION);
    }
    static void information(Context c,Task task,String detail) {
        if(isCurrent(task.id)) { current.stopAudio(); current.collapseOverlay(); if(current.overlayDetail!=null) current.overlayDetail.setText(detail); c.getSystemService(NotificationManager.class).notify(NOTIFICATION,current.notification(detail,false)); }
    }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); stopAudio(); releaseWake(); AlarmWakeLock.release(); removeOverlay(); pending.clear(); if(current==this) current=null; super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}

