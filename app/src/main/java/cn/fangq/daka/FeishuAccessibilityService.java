package cn.fangq.daka;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.app.Notification;
import android.graphics.Rect;
import android.os.*;
import android.view.accessibility.*;
import java.time.*;
import java.util.*;

/** Only handles the official Feishu package, within a short, explicitly armed task session. */
public class FeishuAccessibilityService extends AccessibilityService {
    private static FeishuAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String sessionId = "", baselineSuccess = "", lastCandidate = "";
    private long stableSince;
    private boolean handoff, observing;
    private long observedAt;
    static boolean connected() { return instance != null; }
    static Context launchContext() { return instance; }
    static void prepareSession(Task task) {
        if (instance == null) return;
        instance.sessionId = task.id; instance.baselineSuccess = ""; instance.lastCandidate = ""; instance.handoff = false; instance.observing = false;
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root != null && FeishuLauncher.PACKAGE.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
            instance.baselineSuccess = snapshot(root).success();
        }
    }
    @Override protected void onServiceConnected() {
        instance = this;
        // A service reconnection must not resume an old click session.
        new Store(this).stopAutomation();
        handler.post(poll);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        Store store = new Store(this);
        Task task = active(store);
        if (task == null || event.getPackageName() == null || !FeishuLauncher.PACKAGE.contentEquals(event.getPackageName())) return;
        // A new Feishu toast is stronger evidence than an old success label already on screen.
        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                && !(event.getParcelableData() instanceof Notification)
                && "android.widget.Toast".contentEquals(event.getClassName() == null ? "" : event.getClassName())
                && ("clicked".equals(store.state(task.id)) || (observing && handoff))
                && System.currentTimeMillis() - observedAt > 1000) {
            String text = event.getText().toString();
            if (text.contains("打卡成功") && !text.contains("未成功") && !text.contains("不成功")) confirmed(store,task);
        }
    }
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            try { inspect(); } catch (RuntimeException e) {
                Store store = new Store(FeishuAccessibilityService.this);
                Task task = active(store);
                if (task != null) fail(store,task,"页面读取失败，请手动拍照打卡");
            } finally { handler.postDelayed(this,400); }
        }
    };
    private Task active(Store store) {
        String id = store.prefs.getString("session_id", "");
        if (id.isEmpty()) return null;
        String direction=store.prefs.getString("session_direction", "TEST");
        Task task = new Task(direction,store.prefs.getLong("session_due",0),direction,store.prefs.getLong("session_revision",0));
        if (!task.id.equals(id) || task.test() || AutomationPolicy.terminal(store.state(id))) { store.stopAutomation(); return null; }
        if (!SchedulePolicy.sameTaskDay(System.currentTimeMillis(),task.due,ZoneId.systemDefault())) {
            fail(store,task,"日期已变化，请人工核实，不自动补打前一天任务"); return null;
        }
        if (System.currentTimeMillis() > store.prefs.getLong("session_until",0)) {
            fail(store,task,"未确认打卡结果，请在飞书核实照片是否已提交"); return null;
        }
        return task;
    }
    private void inspect() {
        Store store = new Store(this);
        Task task = active(store);
        if (task == null) return;
        if (store.mode(task) != 2 || !store.active(task) || store.attendance(task).isEmpty()) { store.stopAutomation(); return; }
        if (!task.id.equals(sessionId)) { fail(store,task,"自动操作会话已失效，请手动拍照打卡"); return; }
        if (FeishuLauncher.locked(this)) { lastCandidate = ""; return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !FeishuLauncher.PACKAGE.contentEquals(root.getPackageName())) { lastCandidate = ""; return; }
        Snapshot page = snapshot(root);
        if (!observing && page.attendance() && page.today(LocalDate.now())) {
            observing = true; observedAt = System.currentTimeMillis(); baselineSuccess = page.success();
        }
        String state = store.state(task.id);
        if (observing && ("clicked".equals(state) || handoff) && System.currentTimeMillis()-observedAt > 1000
                && AutomationPolicy.newSuccess(baselineSuccess, page.success())) {
            confirmed(store,task); return;
        }
        if (page.blocked()) {
            if (!handoff) {
                handoff = true; lastCandidate = "";
                store.log("需要你现场拍照／验证并提交；助手已停止自动点击，继续等待新的成功提示");
                ReminderService.information(this,task,"请现场拍照并提交；助手不会代拍或自动提交照片");
            }
            return;
        }
        if (handoff || "clicked".equals(state) || "uncertain".equals(state)) return;
        String target = "IN".equals(store.attendance(task)) ? "上班打卡" : "下班打卡";
        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        for (AccessibilityNodeInfo node : page.nodes) if (target.equals(text(node)) && node.isVisibleToUser() && node.isEnabled()) candidates.add(node);
        boolean today = page.today(LocalDate.now());
        if (!page.attendance() || !today || candidates.size() != 1) { lastCandidate = ""; return; }
        AccessibilityNodeInfo targetNode = candidates.get(0);
        AccessibilityNodeInfo button = targetNode;
        for (int i = 0; i < 4 && button != null && !button.isClickable(); i++) button = button.getParent();
        if (button == null || !button.isVisibleToUser() || !button.isEnabled() || !button.isClickable()) { lastCandidate = ""; return; }
        Rect bounds = new Rect(); button.getBoundsInScreen(bounds);
        String signature = target + bounds.toShortString() + page.text;
        if (!signature.equals(lastCandidate)) { lastCandidate = signature; stableSince = SystemClock.elapsedRealtime(); return; }
        if (!AutomationPolicy.mayClick(true,true,page.attendance(),today,page.blocked(),candidates.size(),false,SystemClock.elapsedRealtime()-stableSince >= 700)) return;
        // Persist before issuing the action: even a crash cannot cause a second automatic click.
        baselineSuccess = page.success();
        boolean saved = store.prefs.edit().putString("task_" + task.id,"clicked")
                .putLong("click_" + task.id,System.currentTimeMillis()).commit();
        if (!saved) { fail(store,task,"无法保存操作记录，已停止自动点击"); return; }
        store.log(Store.label(task.direction) + "：已发出一次打卡入口点击，等待拍照或成功提示");
        if (!button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) fail(store,task,"飞书没有接受点击，请手动完成拍照打卡");
    }
    private void confirmed(Store store, Task task) {
        store.state(task.id,"confirmed",Store.label(task.direction) + "：观察到本次操作后的新打卡成功提示（以飞书记录为准）");
        store.stopAutomation(); ReminderService.finishTask(this,task);
    }
    private void fail(Store store, Task task, String reason) {
        store.state(task.id,"uncertain",Store.label(task.direction) + "：" + reason);
        store.stopAutomation(); ReminderService.information(this,task,reason);
    }
    static String text(AccessibilityNodeInfo node) {
        CharSequence t = node.getText();
        if (t == null || t.toString().trim().isEmpty()) t = node.getContentDescription();
        return t == null ? "" : t.toString().trim();
    }
    static Snapshot snapshot(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>(); ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>(); queue.add(root);
        StringBuilder text = new StringBuilder();
        while (!queue.isEmpty() && nodes.size() < 800) {
            AccessibilityNodeInfo node = queue.remove(); nodes.add(node);
            if (node.isVisibleToUser()) { String value = text(node); if (!value.isEmpty()) text.append(value).append('\n'); }
            for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) queue.add(child); }
        }
        return new Snapshot(nodes,text.toString());
    }
    static final class Snapshot extends PageEvidence {
        final List<AccessibilityNodeInfo> nodes; final String text;
        Snapshot(List<AccessibilityNodeInfo> nodes,String text) { super(text); this.nodes = nodes; this.text = text; }
    }
    @Override public void onInterrupt() { new Store(this).stopAutomation(); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); new Store(this).stopAutomation(); if (instance == this) instance = null; super.onDestroy(); }
}
