package li.xiangyang.android.blebus;

import java.util.Arrays;
import java.util.UUID;

public class BleOperation {

    protected String deviceAddress;
    protected UUID serviceUUID;
    protected UUID characteristicUUID;

    private Type operateType = Type.Notify;

    private byte[] writeData;

    BleOperation(String address, UUID serviceUuid, UUID characteristicUuid, Type opType) {
        this.serviceUUID = serviceUuid;
        characteristicUUID = characteristicUuid;
        deviceAddress = address;
        operateType = opType;
    }

    BleOperation(String address, UUID serviceUuid, UUID characteristicUuid, byte[] data, boolean withResponse) {

        this.serviceUUID = serviceUuid;
        characteristicUUID = characteristicUuid;
        deviceAddress = address;
        operateType = withResponse ? Type.Write : Type.WriteWithoutResponse;
        writeData = data;
    }

    UUID getServiceUUID() {
        return serviceUUID;
    }

    String getDeviceAddress() {
        return deviceAddress;
    }


    UUID getCharacteristicUUID() {
        return characteristicUUID;
    }

    Type getOperateType() {
        return operateType;
    }

    byte[] getWritingData() {
        return writeData;
    }

    @Override
    public int hashCode() {
        int hashcode = this.characteristicUUID.hashCode();
        return hashcode;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof BleOperation) {
            BleOperation s = (BleOperation) o;
            if (s.deviceAddress.equals(this.deviceAddress)
                    && s.serviceUUID.equals(this.serviceUUID)
                    && s.operateType.equals(this.operateType)
                    && s.characteristicUUID.equals(this.characteristicUUID)
                    ) {

                if ((s.writeData == null && this.writeData == null) || (Arrays.equals(s.writeData, this.writeData))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(operateType.toString());
        if (operateType == Type.Write || operateType == Type.WriteWithoutResponse) {
            sb.append(" " + Utils.toHexString(writeData)).append(" to");
        }
        sb.append(" ").append(characteristicUUID);
        return sb.toString();
    }

    public enum Type {
        Read("Read"),
        Write("Write"),
        WriteWithoutResponse("WriteWithoutResponse"),
        Notify("Notify"),
        Indicate("Indicate"),
        DisableNotify("DisableNotify"),
        DisableIndicate("DisableIndicate");;

        private String desc;

        Type(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            return desc;
        }
    }
}
