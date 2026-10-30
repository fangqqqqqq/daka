package cn.fangq.daka;

/** Guard rules are deliberately conservative when the Feishu interface is unfamiliar. */
public final class AutomationPolicy {
    private AutomationPolicy() {}
    public static boolean mayStart(boolean autoEnabled, boolean test, boolean locked, long now,
                                   long due, String state) {
        return autoEnabled && !test && !locked && now >= due && now - due <= 15 * 60_000L
                && !terminal(state) && !"clicked".equals(state) && !"uncertain".equals(state);
    }
    public static boolean terminal(String state) {
        return "confirmed".equals(state) || "user_confirmed".equals(state) || "skipped".equals(state);
    }
    public static boolean mayClick(boolean feishu, boolean unlocked, boolean attendancePage,
                                   boolean today, boolean blocked, int matches, boolean alreadyClicked,
                                   boolean stable) {
        return feishu && unlocked && attendancePage && today && !blocked && matches == 1
                && !alreadyClicked && stable;
    }
    public static boolean newSuccess(String before, String after) {
        return !after.isEmpty() && !after.equals(before);
    }
}
