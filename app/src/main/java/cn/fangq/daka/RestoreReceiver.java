package cn.fangq.daka;

import android.content.*;

public class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        new Store(c).stopAutomation();
        AlarmScheduler.reschedule(c);
    }
}
