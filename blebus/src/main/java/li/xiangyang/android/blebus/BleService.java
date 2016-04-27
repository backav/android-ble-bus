package li.xiangyang.android.blebus;

import java.util.Arrays;
import java.util.UUID;

/**
 * 当前类抽象了一个蓝牙设备的服务(指定服务,属性以及属性的操作)
 *
 * @author bac
 */
public class BleService {

    private String deviceAddress;
    private UUID serviceUUID;
    private UUID characteristicUUID;
    private boolean characteristicOperating = false;
    private int tag;

    private OperateType operateType = OperateType.Notify;

    private byte[] writeData;

    public BleService(String address, UUID serviceUuid, UUID characteristicUuid, OperateType opType) {
        this.serviceUUID = serviceUuid;
        characteristicUUID = characteristicUuid;
        deviceAddress = address;
        operateType = opType;
    }

    public BleService(String address, UUID serviceUuid, UUID characteristicUuid, byte[] data, boolean withResponse) {

        this.serviceUUID = serviceUuid;
        characteristicUUID = characteristicUuid;
        deviceAddress = address;
        operateType = withResponse ? OperateType.Write : OperateType.WriteWithoutResponse;
        writeData = data;
    }

    public UUID getServiceUUID() {
        return serviceUUID;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }


    public UUID getCharacteristicUUID() {
        return characteristicUUID;
    }

    public OperateType getOperateType() {
        return operateType;
    }

    public byte[] getWritingData() {
        return writeData;
    }

    /**
     * 判断是否处于操作中状态,比如监听中
     *
     * @return
     */
    public boolean isOperating() {
        return characteristicOperating;
    }

    public void setOperating(boolean operating) {
        characteristicOperating = operating;
    }
    public void resetOperating() {
        characteristicOperating = false;
    }

    public int getTag() {
        return tag;
    }

    public void setTag(int tag) {
        this.tag = tag;
    }

    @Override
    public int hashCode() {
        int hashcode = this.characteristicUUID.hashCode();
        return hashcode;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof BleService) {
            BleService s = (BleService) o;
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
        if (operateType == OperateType.Write || operateType == OperateType.WriteWithoutResponse) {
            sb.append(" " + Utils.toHexString(writeData)).append(" to");
        }
        sb.append(" ").append(characteristicUUID);
//        sb.append(" " + serviceType + " 设备(" + deviceAddress + ")的" + serviceType.toString() + "数据");
//        if (serviceType.equals(BLEServiceType.Custom)) {
//            sb.append(",service:").append(serviceUUID);
//        }
        return sb.toString();
    }

    public enum OperateType {
        Read("Read"),
        Write("Write"),
        WriteWithoutResponse("WriteWithoutResponse"),
        Notify("Notify"),
        Indicate("Indicate");

        private String desc;

        OperateType(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            return desc;
        }
    }
}
