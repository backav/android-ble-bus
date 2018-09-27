package li.xiangyang.android.blebus;

import java.util.UUID;

public interface IListener {
    void bluetoothClosed();

    void deviceConnecting(String address);

    void deviceConnectFail(String address);

    void deviceConnected(String address);

    void deviceDisconnected(String address);

    void deviceRssiRead(String address, boolean success, int rssi);

    void dataReceived(String address, UUID service, UUID characteristic, byte[] data);

    void operationReadResult(String address, UUID service, UUID characteristic, boolean success);

    void operationWriteResult(String address, UUID service, UUID characteristic, boolean success);

    void operationEnableResult(String address, UUID service, UUID characteristic, boolean success);
}
