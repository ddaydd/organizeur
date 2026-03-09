package com.sec.android.WSM;

import android.util.Log;

public class AppCipher {
    private int Ret;
    private AppCipherNative an;
    private long appID;

    public AppCipher(Client client, long j2, long[] jArr) {
        if (client == null) {
            throw new WSMException("[WSMException] AppCipher(Client c, long nProviderID, long pConsumerID[]) error Client c is null");
        }
        if (j2 == 0) {
            throw new WSMException("[WSMException] AppCipher(Client c, long nProviderID, long pConsumerID[]) error nProviderID is 0");
        }
        if (jArr == null) {
            throw new WSMException("[WSMException] AppCipher(Client c, long nProviderID, long pConsumerID[]) error pConsumerID[] is null");
        }
        int length = jArr.length;
        int[] iArr = new int[length];
        for (int i2 = 0; i2 < length; i2++) {
            long j3 = jArr[i2];
            if (j3 > 2147483647L || j3 < -2147483648L) {
                throw new WSMException("[WSMException] in AppCipher(Client...) variable pConsumerID[" + i2 + "] = " + jArr[i2] + ". It is more then 2^32. Can't cast long (64 bit) into int (32 bit).");
            }
            iArr[i2] = (int) j3;
        }
        AppCipherNative appCipherNative = new AppCipherNative();
        this.an = appCipherNative;
        long createAppKey = appCipherNative.createAppKey(client.getID(), (int) j2, iArr, length);
        this.appID = createAppKey;
        if (createAppKey > 0) {
            return;
        }
        this.Ret = (int) createAppKey;
        throw new WSMException("[WSMException] AppCipher(Client c, long nProviderID, long pConsumerID[]) error code : " + this.appID);
    }

    public int decrypt(byte[] bArr, int i2, int i3) {
        if (bArr == null) {
            throw new WSMException("[WSMException] decrypt error pCipher is null");
        }
        int decrypt = (int) this.an.decrypt(this.appID, bArr, i2, i3);
        if (decrypt > 0) {
            return decrypt;
        }
        this.Ret = decrypt;
        throw new WSMException("[WSMException] decrypt code : " + decrypt);
    }

    public int decryptAndCheckNonce(byte[] bArr, long j2, byte[] bArr2, long j3) {
        if (bArr2 == null) {
            throw new WSMException("[WSMException] decryptAndCheckNonce error plain is null");
        }
        if (bArr == null) {
            throw new WSMException("[WSMException] decryptAndCheckNonce error cipher is null");
        }
        int decryptAndCheckNonce = this.an.decryptAndCheckNonce(this.appID, bArr, j2, bArr2, j3);
        this.Ret = decryptAndCheckNonce;
        if (decryptAndCheckNonce > 0) {
            return decryptAndCheckNonce;
        }
        throw new WSMException("[WSMException] decryptAndCheckNonce error code : " + decryptAndCheckNonce);
    }

    public int decryptAndWrap(byte[] bArr, long j2, byte[] bArr2, int i2, byte[] bArr3, long j3) {
        if (bArr == null) {
            throw new WSMException("[WSMException] delegatedWrap error pCipher is null");
        }
        if (bArr2 == null) {
            throw new WSMException("[WSMException] delegatedWrap error taName is null");
        }
        if (bArr3 == null) {
            throw new WSMException("[WSMException] delegatedWrap error pWrappedPlain is null");
        }
        int decryptAndWrap = this.an.decryptAndWrap(this.appID, bArr, j2, bArr2, i2, bArr3, j3);
        this.Ret = decryptAndWrap;
        if (decryptAndWrap <= 0) {
            throw new WSMException("[WSMException] decryptAndWrap code : " + decryptAndWrap);
        }
        Log.d("WSM_SR_tests_enc", "decryptAndWrap return: " + decryptAndWrap);
        return decryptAndWrap;
    }

    public int delegatedWrap(long j2, byte[] bArr, int i2, byte[] bArr2, int i3, byte[] bArr3, int i4) {
        if (bArr == null) {
            throw new WSMException("[WSMException] delegatedWrap error pPlain is null");
        }
        if (bArr2 == null) {
            throw new WSMException("[WSMException] delegatedWrap error taName is null");
        }
        if (bArr3 == null) {
            throw new WSMException("[WSMException] delegatedWrap error pWrapped is null");
        }
        int delegatedWrap = this.an.delegatedWrap(j2, bArr, i2, bArr2, i3, bArr3, i4);
        this.Ret = delegatedWrap;
        if (-26 == delegatedWrap) {
            throw new WSMException("[WSMException] Delegated wrap not supported in WSM NWD version");
        }
        if (delegatedWrap > 0) {
            return delegatedWrap;
        }
        throw new WSMException("[WSMException] delegatedWrap code : " + delegatedWrap);
    }

    public void destroy() {
        int destroyAppKey = this.an.destroyAppKey(this.appID);
        this.Ret = destroyAppKey;
        if (destroyAppKey > 0) {
            return;
        }
        throw new WSMException("[WSMException] app destroy error code : " + destroyAppKey);
    }

    public int encrypt(byte[] bArr, int i2, int i3) {
        if (bArr == null) {
            throw new WSMException("[WSMException] encrypt error pPlain is null");
        }
        int encrypt = (int) this.an.encrypt(this.appID, bArr, i2, i3);
        if (encrypt > 0) {
            return encrypt;
        }
        this.Ret = encrypt;
        throw new WSMException("[WSMException] encrypt error code : " + encrypt);
    }

    public int encryptWithNonce(byte[] bArr, long j2, byte[] bArr2, int i2, byte[] bArr3, long j3) {
        if (bArr == null) {
            throw new WSMException("[WSMException] encryptWithNonce error plain is null");
        }
        if (bArr2 == null) {
            throw new WSMException("[WSMException] encryptWithNonce error nonce is null");
        }
        if (bArr3 == null) {
            throw new WSMException("[WSMException] encryptWithNonce error cipher is null");
        }
        int encryptWithNonce = this.an.encryptWithNonce(this.appID, bArr, j2, bArr2, i2, bArr3, j3);
        this.Ret = encryptWithNonce;
        if (encryptWithNonce > 0) {
            return encryptWithNonce;
        }
        throw new WSMException("[WSMException] encryptWithNonce error code : " + encryptWithNonce);
    }

    public int generateNonce(byte[] bArr, int i2) {
        if (bArr == null) {
            throw new WSMException("[WSMException] encryptWithNonce error nonce is null");
        }
        int generateNonce = this.an.generateNonce(this.appID, bArr, i2);
        this.Ret = generateNonce;
        if (generateNonce > 0) {
            return generateNonce;
        }
        throw new WSMException("[WSMException] generateNonce err code : " + generateNonce);
    }

    public long getAppID() {
        return this.appID;
    }

    public int getRet() {
        return this.Ret;
    }

    public void setAppID(long j2) {
        this.appID = j2;
    }

    public AppCipher(Server server, long j2, long[] jArr) {
        if (server == null) {
            throw new WSMException("[WSMException] AppCipher(Server s, long nProviderID, long pConsumerID[]) error Sever s is null");
        }
        if (j2 == 0) {
            throw new WSMException("[WSMException] AppCipher(Server s, long nProviderID, long pConsumerID[]) error nProviderID is 0");
        }
        if (jArr == null) {
            throw new WSMException("[WSMException] AppCipher(Server s, long nProviderID, long pConsumerID[]) error pConsumerID[] is null");
        }
        int length = jArr.length;
        int[] iArr = new int[length];
        for (int i2 = 0; i2 < length; i2++) {
            long j3 = jArr[i2];
            if (j3 > 2147483647L || j3 < -2147483648L) {
                Log.d("WSM AppCipher", "pConsumerID[i] = " + Long.toHexString(jArr[i2]));
                throw new WSMException("[WSMException] in AppCipher(Server...) variable pConsumerID[" + i2 + "] = " + jArr[i2] + ". It is more then 2^32. Can't cast long (64 bit) into int (32 bit).");
            }
            iArr[i2] = (int) j3;
        }
        AppCipherNative appCipherNative = new AppCipherNative();
        this.an = appCipherNative;
        long createAppKey = appCipherNative.createAppKey(server.getID(), (int) j2, iArr, length);
        this.appID = createAppKey;
        if (createAppKey > 0) {
            return;
        }
        this.Ret = (int) createAppKey;
        throw new WSMException("[WSMException] AppCipher(Server s, long nProviderID, long pConsumerID[]) error code : " + this.appID);
    }
}
