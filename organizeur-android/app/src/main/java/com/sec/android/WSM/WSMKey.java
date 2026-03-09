package com.sec.android.WSM;

public class WSMKey {
    private byte[] key;

    public WSMKey(byte[] bArr) {
        byte[] bArr2 = new byte[bArr.length];
        this.key = bArr2;
        System.arraycopy(bArr, 0, bArr2, 0, bArr2.length);
    }

    public byte[] getKey() {
        return this.key;
    }
}
