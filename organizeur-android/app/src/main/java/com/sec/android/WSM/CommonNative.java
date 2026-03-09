package com.sec.android.WSM;

class CommonNative {
    static {
        System.loadLibrary("wsm2_jni");
    }

    public native int getCurrentProtocolVersion();

    public native boolean isDaemonReachable();

    public native int setProtocolVersion(int i2);
}
