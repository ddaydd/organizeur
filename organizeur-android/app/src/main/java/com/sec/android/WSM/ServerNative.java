package com.sec.android.WSM;

class ServerNative {
    static {
        System.loadLibrary("wsm2_jni");
    }

    public native int checkAndGenerateClientResponse(long j2, byte[] bArr, byte[] bArr2);

    public native int destroy(long j2);

    public native int generateClientChallenge(long j2, byte[] bArr);

    public native int generateConfirmMessage(long j2, StringBuilder sb);

    public native int getESAPKey(long j2, byte[] bArr);

    public native int getWSMConfirmKey(long j2, byte[] bArr);

    public native int getWSMEncKey(long j2, byte[] bArr);

    public native long init(String str, String str2);

    public native long init(String str, String str2, int i2);

    public native long init(String str, String str2, byte[] bArr);

    public native long init(String str, String str2, byte[] bArr, int i2);

    public native int recheckAndGenerateClientResponse(long j2, byte[] bArr, byte[] bArr2);

    public native int regenerateClientChallenge(long j2, byte[] bArr, boolean z2);
}
