package com.sec.android.WSM;

public class ESAPKey {
    private byte[] key;

    public ESAPKey(byte[] bArr) {
        if (bArr != null) {
            byte[] bArr2 = new byte[bArr.length];
            this.key = bArr2;
            System.arraycopy(bArr, 0, bArr2, 0, bArr2.length);
        }
    }

    public byte[] getKey() {
        return this.key;
    }

    public byte[] toByteArray() {
        return this.key;
    }
}
