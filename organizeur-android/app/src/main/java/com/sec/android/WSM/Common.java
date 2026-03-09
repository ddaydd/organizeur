package com.sec.android.WSM;

public class Common {
    private static CommonNative instance = new CommonNative();

    private Common() {
    }

    public static int get_current_protocol_version() {
        return instance.getCurrentProtocolVersion();
    }

    public static boolean is_daemon_reachable() {
        return instance.isDaemonReachable();
    }

    public static int set_protocol_version(int i2) {
        return instance.setProtocolVersion(i2);
    }
}
