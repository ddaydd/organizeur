package com.sec.android.WSM;

public class Server {
    private static final int CLIENT_CHALLENGE_LEN = 70;
    private static final int CLIENT_CHALLENGE_LEN_EX = 136;
    private static final int CLIENT_RESPONSE_LEN = 35;
    private static final int CLIENT_RESPONSE_LEN_EX = 67;
    private static final int REAUTH_CLIENT_CHALLENGE_KEY_UPDATE_LEN = 68;
    private static final int REAUTH_CLIENT_CHALLENGE_KEY_UPDATE_LEN_EX = 136;
    private static final int REAUTH_CLIENT_CHALLENGE_LEN = 19;
    private static final int REAUTH_CLIENT_CHALLENGE_LEN_EX = 35;
    private static final int REAUTH_CLIENT_RESPONSE_LEN = 35;
    private static final int REAUTH_CLIENT_RESPONSE_LEN_EX = 67;
    private static final int WSM_CONFIRM_KEY_SIZE = 32;
    private static final int WSM_ESAP_KEY_SIZE_EX = 64;
    private static final int WSM_IV_SIZE = 16;
    private static final int WSM_SAP_KEY_SIZE_EX = 48;
    private static final int WSM_SO_ESAP_KEY_SIZE_EX = 96;
    private long id;
    private int ret;
    private ServerNative sn;

    public Server(String str, String str2) {
        ServerNative serverNative = new ServerNative();
        this.sn = serverNative;
        if (str == null) {
            throw new WSMException("[WSMException] Server (String serverID, String clientID) error serverID is null");
        }
        if (str2 == null) {
            throw new WSMException("[WSMException] Server (String serverID, String clientID) error clientID is null");
        }
        long init = serverNative.init(str, str2);
        this.id = init;
        if (init > 0) {
            return;
        }
        throw new WSMException("[WSMException] Server (String serverID, String clientID) error code : " + this.id);
    }

    private int getCurrentProtocolVersion() {
        return Common.get_current_protocol_version();
    }

    private int setCurrentProtocolVersion(int i2) {
        return Common.set_protocol_version(i2);
    }

    public AuthPacket checkAndGenerateClientResponse(AuthPacket authPacket) {
        if (authPacket == null) {
            throw new WSMException("[WSMException] checkAndGenerateServerChallenge (AuthPacket clientChallenge) error!!: clientChallenge is null");
        }
        byte[] payload = authPacket.getPayload();
        int i2 = 1 == getCurrentProtocolVersion() ? 35 : 67;
        byte[] bArr = new byte[i2];
        int checkAndGenerateClientResponse = this.sn.checkAndGenerateClientResponse(this.id, payload, bArr);
        this.ret = checkAndGenerateClientResponse;
        if (checkAndGenerateClientResponse <= 0) {
            throw new WSMException("[WSMException] checkAndGenerateClientResponse (AuthPacket serverChallenge) error code : " + this.ret);
        }
        if (bArr[2] == i2) {
            return new AuthPacket(bArr);
        }
        throw new WSMException("[WSMException] checkAndGenerateClientResponse (AuthPacket serverChallenge) error!!: the length of pClientResponse is not " + i2 + ".");
    }

    public void destroy() {
        int destroy = this.sn.destroy(this.id);
        this.ret = destroy;
        if (destroy > 0) {
            return;
        }
        throw new WSMException("[WSMException] destroy () error code : " + this.ret);
    }

    public AuthPacket generateClientChallenge(byte[] bArr) {
        if (bArr == null) {
            throw new WSMException("[WSMException] generateClientChallenge () error pClientChallenge is null");
        }
        int generateClientChallenge = this.sn.generateClientChallenge(this.id, bArr);
        this.ret = generateClientChallenge;
        if (generateClientChallenge <= 0) {
            throw new WSMException("[WSMException] generateClientChallenge () error code : " + this.ret);
        }
        int i2 = 1 == getCurrentProtocolVersion() ? CLIENT_CHALLENGE_LEN : 136;
        if ((bArr[2] & 255) == i2) {
            return new AuthPacket(bArr);
        }
        throw new WSMException("[WSMException] generateClientChallenge () error!!: the length of pClientChallenge is not " + i2 + "<>" + (bArr[2] & 255) + ".");
    }

    public String generateConfirmMessage() {
        StringBuilder sb = new StringBuilder();
        int generateConfirmMessage = this.sn.generateConfirmMessage(this.id, sb);
        this.ret = generateConfirmMessage;
        if (generateConfirmMessage > 0) {
            return sb.toString();
        }
        throw new WSMException("[WSMException] generateConfirmMessage () error code : " + this.ret);
    }

    public ESAPKey getESAPKey() {
        byte[] bArr = new byte[WSM_SO_ESAP_KEY_SIZE_EX];
        int eSAPKey = this.sn.getESAPKey(this.id, bArr);
        this.ret = eSAPKey;
        if (eSAPKey > 0) {
            return new ESAPKey(bArr);
        }
        throw new WSMException("[WSMException] getESAPKey () error code : " + this.ret);
    }

    public long getID() {
        return this.id;
    }

    public int getRet() {
        return this.ret;
    }

    public WSMKey getWSMConfirmKey() {
        byte[] bArr = new byte[32];
        int wSMConfirmKey = this.sn.getWSMConfirmKey(this.id, bArr);
        this.ret = wSMConfirmKey;
        if (wSMConfirmKey > 0) {
            return new WSMKey(bArr);
        }
        throw new WSMException("[WSMException] getWSMConfirmKey () error code : " + this.ret);
    }

    public WSMKey getWSMEncKey() {
        byte[] bArr = new byte[64];
        int wSMEncKey = this.sn.getWSMEncKey(this.id, bArr);
        this.ret = wSMEncKey;
        if (wSMEncKey > 0) {
            return new WSMKey(bArr);
        }
        throw new WSMException("[WSMException] getWSMEncKey () error code : " + this.ret);
    }

    public AuthPacket recheckAndGenerateClientResponse(AuthPacket authPacket) {
        if (authPacket == null) {
            throw new WSMException("[WSMException] recheckAndGenerateClientResponse (AuthPacket serverChallenge) error serverChallenge is null");
        }
        byte[] payload = authPacket.getPayload();
        int i2 = 1 == Common.get_current_protocol_version() ? 35 : 67;
        byte[] bArr = new byte[i2];
        int recheckAndGenerateClientResponse = this.sn.recheckAndGenerateClientResponse(this.id, payload, bArr);
        this.ret = recheckAndGenerateClientResponse;
        if (recheckAndGenerateClientResponse <= 0) {
            throw new WSMException("[WSMException] recheckAndGenerateClientResponse (AuthPacket serverChallenge) error code : " + this.ret);
        }
        if ((bArr[2] & 255) == i2) {
            return new AuthPacket(bArr);
        }
        throw new WSMException("[WSMException] recheckAndGenerateClientResponse (AuthPacket serverChallenge) error!!: the length of pClientResponse is not " + i2 + ".");
    }

    public AuthPacket regenerateClientChallenge(boolean z2) {
        int i2;
        byte[] bArr;
        int i3 = 0;
        int i4 = Common.get_current_protocol_version();
        if (z2) {
            int i5 = 1 == i4 ? REAUTH_CLIENT_CHALLENGE_KEY_UPDATE_LEN : 136;
            bArr = new byte[i5];
            i3 = i5;
            i2 = 0;
        } else {
            i2 = 1 == i4 ? 19 : 35;
            bArr = new byte[i2];
        }
        int regenerateClientChallenge = this.sn.regenerateClientChallenge(this.id, bArr, z2);
        this.ret = regenerateClientChallenge;
        if (regenerateClientChallenge <= 0) {
            throw new WSMException("[WSMException] regenerateClientChallenge (boolean isKeyUpdate) error code : " + this.ret);
        }
        if (z2 && (bArr[2] & 255) != i3) {
            throw new WSMException("[WSMException] regenerageClientChallenge (boolean isKeyUpdate) error!!: the length of pClientChallenge is not " + i3 + ".");
        }
        if (z2 || (bArr[2] & 255) == i2) {
            return new AuthPacket(bArr);
        }
        throw new WSMException("[WSMException] regenerageClientChallenge (boolean isKeyUpdate) error!!: the length of pClientChallenge is not " + i2 + ".");
    }

    public void setID(long j2) {
        this.id = j2;
    }

    public Server(String str, String str2, int i2) {
        setCurrentProtocolVersion(i2);
        if (str == null) {
            throw new WSMException("[WSMException] Server (String serverID, String clientID, int version) error serverID is null");
        }
        if (str2 == null) {
            throw new WSMException("[WSMException] Server (String serverID, String clientID, int version) error clientID is null");
        }
        ServerNative serverNative = new ServerNative();
        this.sn = serverNative;
        long init = serverNative.init(str, str2);
        this.id = init;
        if (init > 0) {
            return;
        }
        throw new WSMException("[WSMException] Server (String serverID, String clientID) error code : " + this.id);
    }

    public Server(String str, String str2, ESAPKey eSAPKey) {
        ServerNative serverNative = new ServerNative();
        this.sn = serverNative;
        if (str == null) {
            throw new WSMException("[WSMException] Server (String serverID, String clientID, ESAPKey ek) error serverID is null");
        }
        if (str2 == null) {
            throw new WSMException("[WSMException] Server (String serverID, String clientID, ESAPKey ek) error clientID is null");
        }
        if (eSAPKey == null) {
            throw new WSMException("[WSMException] Server (String serverID, String clientID, ESAPKey ek) error ek is null");
        }
        long init = serverNative.init(str, str2, eSAPKey.getKey());
        this.id = init;
        if (init > 0) {
            return;
        }
        throw new WSMException("[WSMException] Server (String serverID, String clientID, ESAPKey ek) error code : " + this.id);
    }

    public Server(String str, String str2, ESAPKey eSAPKey, int i2) {
        setCurrentProtocolVersion(i2);
        if (str == null) {
            throw new WSMException("[WSMException] Server (String serverID, String clientID, ESAPKey ek, int version) error serverID is null");
        }
        if (str2 == null) {
            throw new WSMException("[WSMException] Server (String serverID, String clientID, ESAPKey ek, int version) error clientID is null");
        }
        if (eSAPKey == null) {
            throw new WSMException("[WSMException] Server (String serverID, String clientID, ESAPKey ek, int version) error ek is null");
        }
        ServerNative serverNative = new ServerNative();
        this.sn = serverNative;
        long init = serverNative.init(str, str2, eSAPKey.getKey());
        this.id = init;
        if (init > 0) {
            return;
        }
        throw new WSMException("[WSMException] Server (String serverID, String clientID, ESAPKey ek) error code : " + this.id);
    }
}
