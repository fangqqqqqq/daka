package cn.fangq.daka;

import android.content.Context;
import android.os.PowerManager;

/** Bridge the receiver-to-service gap; the service owns its own bounded wake lock. */
final class AlarmWakeLock {
    private static PowerManager.WakeLock handoff;
    static synchronized void acquire(Context context) {
        if(handoff==null) {
            handoff=context.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"daka:alarm-handoff");
            handoff.setReferenceCounted(false);
        }
        handoff.acquire(10_000);
    }
    static synchronized void release() {
        if(handoff!=null&&handoff.isHeld()) handoff.release();
        handoff=null;
    }
}
