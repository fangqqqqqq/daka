package cn.fangq.daka;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
final class Ui {
    static final int INK=Color.rgb(22,49,52),GREEN=Color.rgb(18,119,98),MUTED=Color.rgb(108,124,124),BG=Color.rgb(244,247,246),SOFT=Color.rgb(230,241,237),RED=Color.rgb(179,70,65);
    static GradientDrawable round(int color,int radius) { GradientDrawable bg=new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(radius); return bg; }
    static int dp(Context a,int n) { return (int)(a.getResources().getDisplayMetrics().density*n+0.5f); }
    static LinearLayout column(Activity a) { LinearLayout l=new LinearLayout(a); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Activity a,LinearLayout parent) { LinearLayout l=new LinearLayout(a); l.setGravity(Gravity.CENTER_VERTICAL); parent.addView(l,new LinearLayout.LayoutParams(-1,-2)); return l; }
    static LinearLayout page(Activity a) {
        ScrollView scroll=new ScrollView(a); scroll.setBackgroundColor(BG); scroll.setFillViewport(true);
        LinearLayout page=column(a); page.setPadding(dp(a,22),dp(a,16),dp(a,22),dp(a,28));
        scroll.setOnApplyWindowInsetsListener((v,i)-> { page.setPadding(dp(a,22),dp(a,16)+i.getSystemWindowInsetTop(),dp(a,22),dp(a,28)+i.getSystemWindowInsetBottom()); return i; });
        scroll.addView(page); a.setContentView(scroll); styleWindow(a); return page;
    }
    static void styleWindow(Activity a) { a.getWindow().setStatusBarColor(BG); a.getWindow().setNavigationBarColor(BG); a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR); }
    static TextView text(Activity a,LinearLayout parent,String value,int size,int color) {
        TextView t=new TextView(a); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(0,dp(a,4),0,dp(a,5)); t.setLineSpacing(dp(a,3),1); parent.addView(t); return t;
    }
    static TextView heading(Activity a,LinearLayout p,String value,int size) { TextView t=text(a,p,value,size,INK); t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); return t; }
    static LinearLayout card(Activity a,LinearLayout parent,String title) {
        LinearLayout card=column(a); card.setPadding(dp(a,18),dp(a,15),dp(a,18),dp(a,16)); card.setBackground(round(Color.WHITE,dp(a,22)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(a,16); parent.addView(card,lp);
        if(!title.isEmpty()) heading(a,card,title,18); return card;
    }
    static Button button(Activity a,LinearLayout p,String label,Runnable action) { return button(a,p,label,action,false); }
    static Button button(Activity a,LinearLayout p,String label,Runnable action,boolean primary) {
        Button b=new Button(a); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); b.setTextColor(primary ? Color.WHITE : GREEN);
        b.setBackground(round(primary ? GREEN : SOFT,dp(a,13))); b.setElevation(0); b.setMinHeight(0); b.setMinimumHeight(0); b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,48)); lp.topMargin=dp(a,10); p.addView(b,lp); return b;
    }
    static void weighted(View v,int weight,Activity a) { LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(a,46),weight); lp.setMargins(dp(a,3),dp(a,6),dp(a,3),dp(a,2)); v.setLayoutParams(lp); }
    static EditText field(Activity a,LinearLayout p,String title,String value,String description) {
        text(a,p,title,13,MUTED); EditText e=new EditText(a); e.setTextSize(15); e.setSingleLine(true); e.setText(value); e.setContentDescription(description); e.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8)); e.setBackground(round(BG,dp(a,10)));
        p.addView(e,new LinearLayout.LayoutParams(-1,dp(a,48))); return e;
    }
    static Switch toggle(Activity a,LinearLayout p,String title,boolean checked,java.util.function.Consumer<Boolean> change) {
        Switch s=new Switch(a); s.setText(title); s.setTextColor(INK); s.setTextSize(15); s.setPadding(0,dp(a,10),0,dp(a,10)); s.setChecked(checked); p.addView(s,new LinearLayout.LayoutParams(-1,-2)); s.setOnCheckedChangeListener((v,c)->change.accept(c)); return s;
    }
    static Spinner select(Activity a,LinearLayout p,String title,String[] options,int selected,java.util.function.IntConsumer change) {
        text(a,p,title,13,MUTED); Spinner spinner=new Spinner(a);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,options); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter); spinner.setSelection(selected);
        spinner.setContentDescription(title); p.addView(spinner,new LinearLayout.LayoutParams(-1,dp(a,48)));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,View view,int pos,long id) { change.accept(pos); }
            public void onNothingSelected(AdapterView<?> parent) {}
        }); return spinner;
    }
}


