package cn.fangq.daka;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

class PageEvidence {
    final String text;
    PageEvidence(String text) { this.text = text; }
    String success() {
        for (String line : text.split("\n")) if (line.equals("打卡成功") || line.equals("上班打卡成功") || line.equals("下班打卡成功")) return line;
        return "";
    }
    boolean attendance() { return text.contains("考勤规则") || (text.contains("考勤") && text.contains("打卡记录")); }
    boolean today(LocalDate day) {
        // Match standalone date labels, not a substring of a historical year or a calendar grid.
        for (String line : text.split("\n")) {
            String value = line.trim();
            if (value.equals("今天") || value.equals("今日") || value.equals(day.toString())
                    || value.equals(day.format(DateTimeFormatter.ofPattern("yyyy年M月d日",Locale.ROOT)))
                    || value.equals(day.format(DateTimeFormatter.ofPattern("M月d日",Locale.ROOT)))
                    || value.equals(day.format(DateTimeFormatter.ofPattern("MM月dd日",Locale.ROOT)))) return true;
        }
        return false;
    }
    boolean blocked() {
        for (String blocker : new String[]{"请拍照","拍照打卡","拍照验证","拍摄照片","重拍","使用照片","人脸识别","人脸验证","身份验证","不在打卡范围","定位失败","请登录","验证码"}) if (text.contains(blocker)) return true;
        return false;
    }
}
