package com.sec.android.WSM;

class AppCipherNative {
    static {
        System.loadLibrary("wsm2_jni");
    }

    public native long createAppKey(long j2, int i2, int[] iArr, int i3);

    public native long decrypt(long j2, byte[] bArr, long j3, long j4);

    public native int decryptAndCheckNonce(long j2, byte[] bArr, long j3, byte[] bArr2, long j4);

    public native int decryptAndWrap(long j2, byte[] bArr, long j3, byte[] bArr2, int i2, byte[] bArr3, long j4);

    public native int delegatedWrap(long j2, byte[] bArr, int i2, byte[] bArr2, int i3, byte[] bArr3, int i4);

    public native int destroyAppKey(long j2);

    public native long encrypt(long j2, byte[] bArr, long j3, long j4);

    public native int encryptWithNonce(long j2, byte[] bArr, long j3, byte[] bArr2, int i2, byte[] bArr3, long j4);

    public native int generateNonce(long j2, byte[] bArr, int i2);
}
