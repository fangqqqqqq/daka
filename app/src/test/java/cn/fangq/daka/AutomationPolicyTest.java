package cn.fangq.daka;

import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class AutomationPolicyTest {
    @Test public void scheduledUnlockedTaskCanStart() { assertTrue(AutomationPolicy.mayStart(true,false,false,2000,1000,"waiting")); }
    @Test public void reminderTestCannotStartAutomation() { assertFalse(AutomationPolicy.mayStart(true,true,false,2000,1000,"new")); }
    @Test public void lockedPhoneCannotStartAutomation() { assertFalse(AutomationPolicy.mayStart(true,false,true,2000,1000,"new")); }
    @Test public void earlyAndExpiredTasksCannotStart() {
        assertFalse(AutomationPolicy.mayStart(true,false,false,999,1000,"new"));
        assertFalse(AutomationPolicy.mayStart(true,false,false,1_000_000,1000,"new"));
    }
    @Test public void uncertainClickCannotBeRetried() {
        for (String state : new String[]{"clicked","uncertain","confirmed","user_confirmed","skipped"})
            assertFalse(state,AutomationPolicy.mayStart(true,false,false,2000,1000,state));
    }
    @Test public void userMustEnableAutomation() { assertFalse(AutomationPolicy.mayStart(false,false,false,2000,1000,"new")); }
    @Test public void clickRequiresEveryGuard() {
        assertTrue(AutomationPolicy.mayClick(true,true,true,true,false,1,false,true));
        assertFalse(AutomationPolicy.mayClick(false,true,true,true,false,1,false,true));
        assertFalse(AutomationPolicy.mayClick(true,false,true,true,false,1,false,true));
        assertFalse(AutomationPolicy.mayClick(true,true,false,true,false,1,false,true));
        assertFalse(AutomationPolicy.mayClick(true,true,true,false,false,1,false,true));
        assertFalse(AutomationPolicy.mayClick(true,true,true,true,true,1,false,true));
        assertFalse(AutomationPolicy.mayClick(true,true,true,true,false,0,false,true));
        assertFalse(AutomationPolicy.mayClick(true,true,true,true,false,2,false,true));
        assertFalse(AutomationPolicy.mayClick(true,true,true,true,false,1,true,true));
        assertFalse(AutomationPolicy.mayClick(true,true,true,true,false,1,false,false));
    }
    @Test public void oldSuccessIsNotAConfirmation() {
        assertFalse(AutomationPolicy.newSuccess("打卡成功","打卡成功"));
        assertFalse(AutomationPolicy.newSuccess("", ""));
        assertTrue(AutomationPolicy.newSuccess("", "上班打卡成功"));
    }
    @Test public void photoAndIdentityPagesBlockAutomaticClicks() {
        for (String prompt : new String[]{"拍照打卡","请拍照","重拍","使用照片","人脸识别","验证码","不在打卡范围","定位失败"}) assertTrue(prompt,new PageEvidence(prompt).blocked());
    }
    @Test public void historicalDateDoesNotMatchCurrentDay() {
        LocalDate today = LocalDate.parse("2026-10-02");
        assertFalse(new PageEvidence("考勤规则\n2025-10-02\n上班打卡").today(today));
        assertFalse(new PageEvidence("考勤规则\n昨天\n今天还没有数据").today(today));
        assertTrue(new PageEvidence("考勤规则\n2026-10-02\n上班打卡").today(today));
        assertTrue(new PageEvidence("考勤规则\n今天\n上班打卡").today(today));
    }
    @Test public void arbitraryTextIsNotASuccessMessage() {
        assertEquals("",new PageEvidence("照片已提交").success());
        assertEquals("",new PageEvidence("昨天打卡成功").success());
        assertEquals("",new PageEvidence("打卡成功了吗？").success());
        assertEquals("打卡成功",new PageEvidence("今天\n打卡成功\n09:01").success());
    }
    @Test public void chatPageDoesNotCountAsAttendance() { assertFalse(new PageEvidence("聊天\n上班打卡\n今天").attendance()); }
}
