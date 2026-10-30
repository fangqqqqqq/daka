package cn.fangq.qa.feishu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/** An offline receiver; never install on a phone containing real Feishu. */
public class TargetActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        record();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); record();
    }
    private void record() {
        android.content.SharedPreferences prefs = getSharedPreferences("qa",MODE_PRIVATE);
        prefs.edit().putString("history",prefs.getString("history","")+"\n"+getIntent().getDataString()).commit();
        TextView text = new TextView(this);
        text.setTextSize(22);
        text.setText("QA ONLY · 收到飞书跳转\n" + getIntent().getDataString());
        setContentView(text);
    }
}
