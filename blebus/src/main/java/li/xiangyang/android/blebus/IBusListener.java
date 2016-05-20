package li.xiangyang.android.blebus;

import java.util.UUID;


public interface IBusListener {

    /**
     * 设备连接成功
     * @param address
     */
    void deviceConnected(String address);

    /**
     * 设备断开连接
     * @param address
     */
    void deviceDisconnected(String address);

    void openBluetoothFailed();

    /**
     * 读取设备RSSI的结果
     * @param address
     * @param success
     * @param rssi
     */
    void deviceRssiRead(String address, boolean success, int rssi);

    /**
     * 监听操作的结果
     * @param service
     * @param characteristic
     * @param success
     */
    void listenOperateResult(BleService service, UUID characteristic, boolean success);

    /**
     * 写入操作结果
     */
    void writeOperateResult(BleService service, UUID characteristic, boolean success);

    /**
     * 读取操作的结果
     * @param service
     * @param characteristic
     * @param success
     */
    void readOperateResult(BleService service, UUID characteristic, boolean success);

    /**
     * 收到数据
     * 来自于read或者listen
     * @param service
     * @param characteristic
     */
    void dataReceived(final BleService service,
                      final UUID characteristic, byte[] data);

}
