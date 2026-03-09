package com.sec.android.WSM;

public class AuthPacket {
    private byte[] content;
    private int length;
    private byte type;
    private byte version;

    public AuthPacket(byte[] bArr) {
        setPayload(bArr);
    }

    public int getLength() {
        return this.length;
    }

    public byte[] getPayload() {
        int i2 = this.length;
        byte[] bArr = new byte[i2];
        bArr[0] = this.version;
        bArr[1] = this.type;
        bArr[2] = (byte) i2;
        System.arraycopy(this.content, 0, bArr, 3, i2 - 3);
        return bArr;
    }

    public byte getType() {
        return this.type;
    }

    public byte getVersion() {
        return this.version;
    }

    public void setByteContent(byte b2, int i2) {
        byte[] bArr = this.content;
        if (i2 >= bArr.length || i2 < 0) {
            throw new WSMException("[WSMException] position > content.length .");
        }
        bArr[i2] = b2;
    }

    public void setLength(int i2) {
        this.length = i2;
    }

    public void setPayload(byte[] bArr) {
        if ((bArr.length & 255) <= 3) {
            throw new WSMException("[WSMException] setPayload (byte packet[]) error!!: the length of packet should be greater than 3, since the packet contains header information of 3 bytes at the first 3 bytes of the packet.");
        }
        this.version = bArr[0];
        this.type = bArr[1];
        int i2 = bArr[2] & 255;
        this.length = i2;
        if (i2 <= 3 || i2 != bArr.length) {
            throw new WSMException("[WSMException] setPayload (byte packet[]) error!!: the third element, packet[2], represents the length of the packet." + this.length);
        }
        byte[] bArr2 = new byte[i2 - 3];
        this.content = bArr2;
        System.arraycopy(bArr, 3, bArr2, 0, i2 - 3);
    }

    public void setType(byte b2) {
        this.type = b2;
    }

    public void setVersion(byte b2) {
        this.version = b2;
    }

    public AuthPacket(byte[] bArr, int i2, int i3) {
        setPayload(bArr, i2, i3);
    }

    private void setPayload(byte[] bArr, int i2, int i3) {
        if ((bArr.length & 255) - i2 <= 3) {
            throw new WSMException("[WSMException] setPayload (byte packet[]) error!!: the length of packet should be greater than 3, since the packet contains header information of 3 bytes at the first 3 bytes of the packet.");
        }
        this.version = bArr[i2];
        this.type = bArr[i2 + 1];
        int i4 = bArr[i2 + 2] & 255;
        this.length = i4;
        if (i4 <= 3 || i4 != (bArr.length & 255) - i2) {
            throw new WSMException("[WSMException] setPayload (byte packet[]) error!!: the third element, packet[2], represents the length of the packet.");
        }
        byte[] bArr2 = new byte[i4 - 3];
        this.content = bArr2;
        System.arraycopy(bArr, i2 + 3, bArr2, 0, i4 - 3);
    }
}
