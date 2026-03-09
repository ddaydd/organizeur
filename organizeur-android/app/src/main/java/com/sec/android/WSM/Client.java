package com.sec.android.WSM;

import java.util.Arrays;

public class Client {
    private static final int REAUTH_SERVER_CHALLENGE_KEY_UPDATE_LEN = 100;
    private static final int REAUTH_SERVER_CHALLENGE_KEY_UPDATE_LEN_EX = 200;
    private static final int REAUTH_SERVER_CHALLENGE_LEN = 51;
    private static final int REAUTH_SERVER_CHALLENGE_LEN_EX = 99;
    private static final int SERVER_CHALLENGE_LEN = 102;
    private static final int SERVER_CHALLENGE_LEN_EX = 200;
    private static final int WSM_CONFIRM_KEY_SIZE = 32;
    private static final int WSM_ESAP_KEY_SIZE_EX = 64;
    private static final int WSM_IV_SIZE = 16;
    private static final int WSM_SAP_KEY_SIZE_EX = 48;
    private static final int WSM_SO_ESAP_KEY_SIZE_EX = 96;
    private ClientNative cn;
    private long id;
    boolean isKeyUpdate;
    private int ret;

    public Client(String str, String str2) {
        ClientNative clientNative = new ClientNative();
        this.cn = clientNative;
        if (str == null) {
            throw new WSMException("[WSMException] Client (String serverID, String clientID) error serverID is null");
        }
        if (str2 == null) {
            throw new WSMException("[WSMException] Client (String serverID, String clientID) error clientID is null");
        }
        long init = clientNative.init(str, str2);
        this.id = init;
        if (init > 0) {
            this.isKeyUpdate = false;
            this.ret = 1;
        } else {
            throw new WSMException("[WSMException] Client (String serverID, String clientID) error code : " + this.id);
        }
    }

    private int getCurrentProtocolVersion() {
        return Common.get_current_protocol_version();
    }

    private int setCurrentProtocolVersion(int i2) {
        return Common.set_protocol_version(i2);
    }

    public AuthPacket checkAndGenerateServerChallenge(AuthPacket authPacket) {
        if (authPacket == null) {
            throw new WSMException("[WSMException] checkAndGenerateServerChallenge (AuthPacket clientChallenge) error!!: clientChallenge is null");
        }
        byte[] payload = authPacket.getPayload();
        if (payload == null) {
            throw new WSMException("[WSMException] pClientChallenge is null!");
        }
        int i2 = 1 == getCurrentProtocolVersion() ? 102 : 200;
        byte[] bArr = new byte[i2];
        int checkAndGenerateServerChallenge = this.cn.checkAndGenerateServerChallenge(this.id, payload, bArr);
        this.ret = checkAndGenerateServerChallenge;
        if (checkAndGenerateServerChallenge <= 0) {
            throw new WSMException("[WSMException] checkAndGenerateServerChallenge (AuthPacket clientChallenge) error code : " + this.ret);
        }
        if ((bArr[2] & 255) == i2) {
            return new AuthPacket(bArr);
        }
        throw new WSMException("[WSMException] checkAndGenerateServerChallenge (AuthPacket clientChallenge) error!!: the length of pServerChallenge is not " + i2 + ".");
    }

    public void checkClientResponse(AuthPacket authPacket) {
        if (authPacket == null) {
            throw new WSMException("[WSMException] checkClientResponse (AuthPacket clientResponse) error clientResponce is null");
        }
        int checkClientResponse = this.cn.checkClientResponse(this.id, authPacket.getPayload());
        this.ret = checkClientResponse;
        if (checkClientResponse > 0) {
            if (checkClientResponse == -1) {
                throw new WSMException("[WSMException] checkClientResponse (AuthPacket clientResponse) error!!: returned value is -1. (NOT identical HMAC.  authentication error!!)");
            }
        } else {
            throw new WSMException("[WSMException] checkClientResponse (AuthPacket clientResponse) error code : " + this.ret);
        }
    }

    public void destroy() {
        int destroy = this.cn.destroy(this.id);
        this.ret = destroy;
        if (destroy > 0) {
            return;
        }
        throw new WSMException("[WSMException] destroy () error code : " + this.ret);
    }

    public AuthPacket generateCommitment() {
        if (1 == getCurrentProtocolVersion()) {
            throw new WSMException("[WSMException] generateCommitment () : version 1 is not supported");
        }
        byte[] bArr = new byte[67];
        Arrays.fill(bArr, (byte) 0);
        int generateCommitment = this.cn.generateCommitment(this.id, bArr);
        this.ret = generateCommitment;
        if (generateCommitment > 0) {
            return new AuthPacket(bArr);
        }
        throw new WSMException("[WSMException] generateCommitment () error code : " + this.ret);
    }

    public String generateConfirmMessage() {
        StringBuilder sb = new StringBuilder();
        int generateConfirmMessage = this.cn.generateConfirmMessage(this.id, sb);
        this.ret = generateConfirmMessage;
        if (generateConfirmMessage > 0) {
            return sb.toString();
        }
        throw new WSMException("[WSMException] generateConfirmMessage () error code : " + this.ret);
    }

    public ESAPKey getESAPKey() {
        byte[] bArr = new byte[WSM_SO_ESAP_KEY_SIZE_EX];
        int eSAPKey = this.cn.getESAPKey(this.id, bArr);
        this.ret = eSAPKey;
        if (eSAPKey > 0) {
            return new ESAPKey(bArr);
        }
        throw new WSMException("[WSMException] getESAPKey () error code : " + this.ret);
    }

    public long getID() {
        return this.id;
    }

    public boolean getIsKeyUpdate() {
        return this.isKeyUpdate;
    }

    public int getRet() {
        return this.ret;
    }

    public WSMKey getWSMConfirmKey() {
        byte[] bArr = new byte[32];
        int wSMConfirmKey = this.cn.getWSMConfirmKey(this.id, bArr);
        this.ret = wSMConfirmKey;
        if (wSMConfirmKey > 0) {
            return new WSMKey(bArr);
        }
        throw new WSMException("[WSMException] getWSMConfirmKey () error code : " + this.ret);
    }

    public WSMKey getWSMEncKey() {
        byte[] bArr = new byte[64];
        int wSMEncKey = this.cn.getWSMEncKey(this.id, bArr);
        this.ret = wSMEncKey;
        if (wSMEncKey > 0) {
            return new WSMKey(bArr);
        }
        throw new WSMException("[WSMException] getWSMEncKey () error code : " + this.ret);
    }

    public AuthPacket recheckAndGenerateServerChallenge(AuthPacket authPacket) {
        if (authPacket == null) {
            throw new WSMException("[WSMException] recheckAndGenerateServerChallenge (AuthPacket clientChallenge) error clientChallenge is null");
        }
        byte[] payload = authPacket.getPayload();
        int i2 = 1 == getCurrentProtocolVersion() ? 100 : 200;
        byte[] bArr = new byte[i2];
        int recheckAndGenerateServerChallenge = this.cn.recheckAndGenerateServerChallenge(this.id, payload, bArr);
        this.ret = recheckAndGenerateServerChallenge;
        if (recheckAndGenerateServerChallenge <= 0) {
            throw new WSMException("[WSMException] recheckAndGenerateServerChallenge (AuthPacket clientChallenge) error code : " + this.ret);
        }
        if (recheckAndGenerateServerChallenge == 2) {
            if ((bArr[2] & 255) == i2) {
                this.isKeyUpdate = true;
                return new AuthPacket(bArr);
            }
            throw new WSMException("[WSMException] recheckAndGenerateServerChallenge (AuthPacket clientChallenge) error!!: the length of pServerChallenge is not " + i2 + ".");
        }
        int i3 = 1 == getCurrentProtocolVersion() ? REAUTH_SERVER_CHALLENGE_LEN : REAUTH_SERVER_CHALLENGE_LEN_EX;
        if ((bArr[2] & 255) == i3) {
            this.isKeyUpdate = false;
            byte[] bArr2 = new byte[i3];
            System.arraycopy(bArr, 0, bArr2, 0, i3);
            return new AuthPacket(bArr2);
        }
        throw new WSMException("[WSMException] recheckAndGenerateServerChallenge (AuthPacket clientChallenge) error!!: the length of pServerChallenge is not " + i3 + ".");
    }

    public void recheckClientResponse(AuthPacket authPacket) {
        if (authPacket == null) {
            throw new WSMException("[WSMException] recheckClientResponse (AuthPacket clientResponse) error clientResponse is null");
        }
        int recheckClientResponse = this.cn.recheckClientResponse(this.id, authPacket.getPayload());
        this.ret = recheckClientResponse;
        if (recheckClientResponse > 0) {
            return;
        }
        throw new WSMException("[WSMException] recheckClientResponse (AuthPacket clientResponse) error code : " + this.ret);
    }

    public void setID(long j2) {
        this.id = j2;
    }

    public Client(String str, String str2, int i2) {
        setCurrentProtocolVersion(i2);
        if (str == null) {
            throw new WSMException("[WSMException] Client (String serverID, String clientID, int version) error serverID is null");
        }
        if (str2 == null) {
            throw new WSMException("[WSMException] Client (String serverID, String clientID, int version) error clientID is null");
        }
        ClientNative clientNative = new ClientNative();
        this.cn = clientNative;
        long init = clientNative.init(str, str2);
        this.id = init;
        if (init > 0) {
            this.isKeyUpdate = false;
        } else {
            throw new WSMException("[WSMException] Client (String serverID, String clientID) error code : " + this.id);
        }
    }

    public Client(String str, String str2, ESAPKey eSAPKey) {
        ClientNative clientNative = new ClientNative();
        this.cn = clientNative;
        if (str == null) {
            throw new WSMException("[WSMException] Client (String serverID, String clientID, ESAPKey ek) error serverID is null");
        }
        if (str2 == null) {
            throw new WSMException("[WSMException] Client (String serverID, String clientID, ESAPKey ek) error clientID is null");
        }
        if (eSAPKey == null) {
            throw new WSMException("[WSMException] Client (String serverID, String clientID, ESAPKey ek) error ek is null");
        }
        long init = clientNative.init(str, str2, eSAPKey.getKey());
        this.id = init;
        if (init > 0) {
            this.isKeyUpdate = false;
        } else {
            throw new WSMException("[WSMException] Client (String serverID, String clientID, ESAPKey ek) error code : " + this.id);
        }
    }

    public Client(String str, String str2, ESAPKey eSAPKey, int i2) {
        setCurrentProtocolVersion(i2);
        if (str == null) {
            throw new WSMException("[WSMException] Client (String serverID, String clientID, ESAPKey ek, int version) error serverID is null");
        }
        if (str2 == null) {
            throw new WSMException("[WSMException] Client (String serverID, String clientID, ESAPKey ek, int version) error clientID is null");
        }
        if (eSAPKey == null) {
            throw new WSMException("[WSMException] Client (String serverID, String clientID, ESAPKey ek, int version) error ek is null");
        }
        ClientNative clientNative = new ClientNative();
        this.cn = clientNative;
        long init = clientNative.init(str, str2, eSAPKey.getKey());
        this.id = init;
        if (init > 0) {
            this.isKeyUpdate = false;
        } else {
            throw new WSMException("[WSMException] Client (String serverID, String clientID, ESAPKey ek) error code : " + this.id);
        }
    }
}
