package li.xiangyang.android.blebus;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@SuppressLint("NewApi")
public class BleBus {

    private static final int SCAN_PERIOD = 5;// 搜索限制时间/重新连接时间 单位:秒

    private Context mContext;
    private Handler mHandler;
    private IListener mListener;
    private ILogger log;

    private BluetoothAdapter mBluetoothAdapter;
    private boolean connecting = false;

    //保存需要处理的BleOperation
    private final List<BleOperation> mServices = new ArrayList<BleOperation>();
    // 保存正在尝试连接的gatt
    private final Map<String, BluetoothGatt> mConnectingGatts = new HashMap<>();// <Address,Gatt>
    // 保存连接成功的gatt
    private final Map<String, BluetoothGatt> mConnectedGatts = new HashMap<String, BluetoothGatt>();// <Address,Gatt>

    private Lock writeLock = new ReentrantLock();
    private Condition writeCondition = writeLock.newCondition();

    public BleBus(Context cxt, IListener listener) {
        this(cxt, listener, new LogcatLogger());
    }

    public BleBus(Context cxt, IListener listener, ILogger logger) {
        mContext = cxt;
        mHandler = new Handler();
        mListener = new HandledBusListenner(mHandler, listener);
        this.log = logger;
    }

    public boolean isDeviceConnected(String device) {
        return mConnectingGatts.containsKey(device);
    }

    public boolean readRssi(String device) {
        BluetoothGatt gatt = mConnectedGatts.get(device);
        if (gatt != null) {
            return gatt.readRemoteRssi();
        }
        return false;
    }

    public boolean enableIndicate(String mac, UUID service, UUID characteristic) {
        return this.scheduleOperation(new BleOperation(mac, service, characteristic, BleOperation.Type.Indicate));
    }

    public boolean enableNotify(String mac, UUID service, UUID characteristic) {
        return this.scheduleOperation(new BleOperation(mac, service, characteristic, BleOperation.Type.Notify));
    }

    public void disableIndicate(String mac, UUID service, UUID characteristic) {
        this.scheduleOperation(new BleOperation(mac, service, characteristic, BleOperation.Type.DisableIndicate));
    }

    public void disableNotify(String mac, UUID service, UUID characteristic) {
        this.scheduleOperation(new BleOperation(mac, service, characteristic, BleOperation.Type.DisableNotify));
    }

    public void read(String mac, UUID service, UUID characteristic) {
        this.scheduleOperation(new BleOperation(mac, service, characteristic, BleOperation.Type.Read));
    }

    public boolean write(String mac, UUID service, UUID characteristic, byte[] data) {
        return this.scheduleOperation(new BleOperation(mac, service, characteristic, data, true));
    }

    public boolean writeWithoutResponse(String mac, UUID service, UUID characteristic, byte[] data) {
        return this.scheduleOperation(new BleOperation(mac, service, characteristic, data, false));
    }

    public void close(String... devices) {

        Set<String> sets;
        if (devices == null || devices.length == 0) {
            //关闭所有设备的连接
            sets = new HashSet<>(mConnectedGatts.size() + mConnectingGatts.size());
            sets.addAll(mConnectingGatts.keySet());
            sets.addAll(mConnectedGatts.keySet());
        } else {
            //关闭指定设备的连接
            sets = new HashSet<>(devices.length);
            sets.addAll(Arrays.asList(devices));
        }

        for (String address : sets) {

            synchronized (mServices) {
                for (Iterator<BleOperation> iterator = mServices.iterator(); iterator.hasNext(); ) {
                    BleOperation bleService = (BleOperation) iterator.next();
                    if (bleService.getDeviceAddress().equals(address)) {
                        iterator.remove();
                    }
                }
            }

            BluetoothGatt gatt;
            synchronized (mConnectingGatts) {
                gatt = mConnectingGatts.get(address);
                if (gatt != null) {
                    gatt.disconnect();
                    gatt.close();
                    mConnectingGatts.remove(address);
                }
            }

            synchronized (mConnectedGatts) {
                gatt = mConnectedGatts.get(address);
                if (gatt != null) {
                    String deviceInfo = gatt.getDevice().getName() + ":" + gatt.getDevice().getAddress();
                    log.warn("强制断开连接,设备:" + deviceInfo);
                    gatt.disconnect();
                    gatt.close();
                    mConnectedGatts.remove(address);
                }
            }

        }
        if (mServices.size() == 0) {

            try {
                mContext.unregisterReceiver(mBluetoothStateReceiver);
            } catch (Exception ignored) {
            }

            log.warn("所有服务都被关闭了");
        }
    }


    private boolean scheduleOperation(BleOperation service) {

        boolean operateSuccess = true;
        final BluetoothGatt gatt = mConnectedGatts.get(service.getDeviceAddress());
        if (gatt != null) {
            BluetoothGattService s = gatt.getService(service.getServiceUUID());
            if (s != null) {
                final BluetoothGattCharacteristic c = s.getCharacteristic(service.getCharacteristicUUID());
                if (c != null) {
                    operateSuccess = processOperation(service, gatt, c);
                } else {
                    log.error("设备[" + service.getDeviceAddress() + "]的服务[" + service.getServiceUUID() + "]没有这个特性[" + service.getCharacteristicUUID() + "]");
                    operateSuccess = false;
                }
            } else {
                final String deviceInfo = gatt.getDevice().getName() + ":" + gatt.getDevice().getAddress();
                log.info("准备查找设备[" + deviceInfo + "]上的服务");
                if (!(operateSuccess = gatt.discoverServices())) {
                    log.error("在设备[" + deviceInfo + "]上查找服务失败了");
                }
            }
        } else {
            mServices.add(service);
            startConnect();
        }

        return operateSuccess;
    }

    private boolean startBluetooth() {
        if (mBluetoothAdapter == null) {
            final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }
        if (mBluetoothAdapter != null) {
            if (mBluetoothAdapter.isEnabled()) {
                return true;
            }

            //监听蓝牙开关状态,等待蓝牙开启
            waitBluetoothOpen();

            mListener.bluetoothClosed();

            return false;
        } else {
            log.info("不支持蓝牙");
            return false;
        }
    }

    private void waitBluetoothOpen() {
        // 防止重复监听
        try {
            mContext.unregisterReceiver(mBluetoothStateReceiver);
        } catch (IllegalArgumentException e) {
            log.debug("BluetoothStateReceiver还没有监听,忽略");
        }
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothStateReceiver, filter);
    }

    private void startConnect() {


        if (connecting || mServices.size() == 0) {
            return;
        }

        if (!startBluetooth()) {
            return;
        }

        Set<String> devices2connect = new HashSet<>(mServices.size());
        synchronized (mServices) {
            for (BleOperation bleService : mServices) {
                if (!mConnectedGatts.containsKey(bleService.getDeviceAddress())) {
                    devices2connect.add(bleService.getDeviceAddress());
                }
            }
        }

        if (devices2connect.size() > 0) {
            log.info(devices2connect.size() + "个设备尚未连接,准备连接:" + devices2connect.toString());
            connecting = true;


            for (String address : devices2connect) {
                mListener.deviceConnecting(address);
                connectGatt(address);
            }

            log.debug("启动搜索");
            mBluetoothAdapter.startLeScan(leScanCallback);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    connecting = false;
                    log.debug("搜索超时,停止搜索");
                    mBluetoothAdapter.stopLeScan(leScanCallback);
                    startConnect();
                }
            }, SCAN_PERIOD * 1000);

        } else {
            connecting = false;
            log.info("所需设备都已经连接");
        }
    }

    private void connectGatt(final BluetoothDevice device) {
        final String deviceInfo = device.getName() + ":" + device.getAddress();
        synchronized (mConnectingGatts) {
            //如果存在连接中的gatt,关闭之
            BluetoothGatt gatt = mConnectingGatts.get(device.getAddress());
            if (gatt != null) {
                log.debug("停止之前的连接尝试,开始连接设备:" + deviceInfo);
                gatt.close();
            } else {
                log.debug("连接设备:" + deviceInfo);
            }
            mConnectingGatts.put(device.getAddress(), device.connectGatt(mContext, false, mGattCallback));
        }
    }

    private void connectGatt(final String deviceAddress) {
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceAddress);
        final String deviceInfo = device.getName() + ":" + device.getAddress();
        log.debug("准备尝试直接连接设备:" + deviceInfo);

        connectGatt(device);
    }

    /**
     * 扫描监听
     */
    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi,
                             byte[] scanRecord) {
            final String deviceInfo = device.getName() + "["
                    + device.getAddress() + "]:" + rssi;

            log.debug("发现设备: " + deviceInfo);

            if (mConnectedGatts.containsKey(device.getAddress())) {
                log.debug("设备已经连接,跳过: " + deviceInfo);
                return;
            }

            /*
             * 如果当前发现的设备是我需要的设备且没有被连接,则连接它
             */
            boolean needIt = false;
            synchronized (mServices) {
                for (BleOperation s : mServices) {
                    if (device.getAddress().equals(s.getDeviceAddress())) {
                        needIt = true;
                        break;
                    }
                }
            }


            if (needIt) {
                connectGatt(device);
            }
        }
    };

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        public void onConnectionStateChange(final BluetoothGatt gatt,
                                            int status, int newState) {


            String address = gatt.getDevice().getAddress();

            final String deviceInfo = gatt.getDevice().getName() + ":" + gatt.getDevice().getAddress();
            log.warn("设备[" + deviceInfo + "]连接状态改变:" + status + " & " + newState);
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {

                synchronized (mConnectingGatts) {
                    BluetoothGatt connectingGatt = mConnectingGatts.get(address);
                    if (connectingGatt != null) {
                        if (connectingGatt != gatt) {
                            log.error("当前已连接的gatt与连接中的gatt不是同一个,将关闭连接中的gatt");
                            connectingGatt.close();
                        }
                        mConnectingGatts.remove(address);
                    }
                }

                synchronized (mConnectedGatts) {
                    if (mConnectedGatts.containsKey(address)) {
                        log.error("设备[" + deviceInfo + "]已经连接了");
                        if (gatt != mConnectedGatts.get(address)) {
                            gatt.close();
                        }
                        return;
                    }
                    mConnectedGatts.put(address, gatt);
                }


                log.debug("设备[" + deviceInfo + "]连接成功");


                mListener.deviceConnected(address);

                if (gatt.getServices().size() > 0) {
                    deviceServiceDiscovered(gatt);
                } else {
                    log.debug("查找设备[" + deviceInfo + "]支持的服务..");
                    if (!gatt.discoverServices()) {
                        log.error("在设备[" + deviceInfo + "]上查找服务失败了");
                    }
                }

            } else {

                // 防止多次通知
                if (mConnectedGatts.containsKey(address)) {
                    mConnectedGatts.remove(address);
                    mListener.deviceDisconnected(address);
                }

                gatt.close();
                log.info("设备[" + deviceInfo + "]断开连接");

                startConnect();
            }
        }

        ;

        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {

            final String deviceInfo = gatt.getDevice().getName() + ":" + gatt.getDevice().getAddress();
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log.error("在设备[" + deviceInfo + "]上扫描服务失败了:status = " + status);
                return;
            }
            deviceServiceDiscovered(gatt);
        }

        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

            mListener.dataReceived(gatt.getDevice().getAddress(), characteristic.getService().getUuid(), characteristic.getUuid(), characteristic.getValue());

        }

        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {

            mListener.dataReceived(gatt.getDevice().getAddress(), characteristic.getService().getUuid(), characteristic.getUuid(), characteristic.getValue());

        }

        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          final BluetoothGattCharacteristic characteristic,
                                          final int status) {

            writeLock.lock();
            try {
                writeCondition.signal();
            } finally {
                writeLock.unlock();
            }

            mListener.operationWriteResult(gatt.getDevice().getAddress(), characteristic.getService().getUuid(), characteristic.getUuid(), status == BluetoothGatt.GATT_SUCCESS);
        }

        public void onDescriptorWrite(BluetoothGatt gatt,
                                      final BluetoothGattDescriptor descriptor, final int status) {

            if (descriptor.getUuid().equals(Constants.UUID_CONFIG)) {

                final boolean success = status == BluetoothGatt.GATT_SUCCESS;

                if (Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        || Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {

                    mListener.operationEnableResult(gatt.getDevice().getAddress(), descriptor.getCharacteristic().getService().getUuid(), descriptor.getCharacteristic().getUuid(), success);
                }
            }
        }

        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            mListener.deviceRssiRead(gatt.getDevice().getAddress(), status == BluetoothGatt.GATT_SUCCESS, rssi);
        }

    };

    private void deviceServiceDiscovered(final BluetoothGatt gatt) {

        final String deviceInfo = gatt.getDevice().getName() + ":" + gatt.getDevice().getAddress();
        List<BluetoothGattService> ss = gatt.getServices();
        if (ss == null || ss.size() <= 0) {
            return;
        }
        log.debug("在设备[" + deviceInfo + "]上发现 " + ss.size() + " 个服务");

        synchronized (mServices) {
            for (final Iterator<BleOperation> iterator = mServices.iterator(); iterator.hasNext(); ) {
                final BleOperation myService = iterator.next();

                if (myService.getDeviceAddress().equals(gatt.getDevice().getAddress())) {

                    BluetoothGattService deviceService = gatt.getService(myService.getServiceUUID());
                    if (deviceService != null) {
                        final BluetoothGattCharacteristic characteristic = deviceService.getCharacteristic(myService.getCharacteristicUUID());
                        if (characteristic != null) {
                            processOperation(myService, gatt, characteristic);
                        } else {
                            log.error("在设备[" + deviceInfo + "]上找不到此属性:" + myService.getCharacteristicUUID());
                        }
                    } else {
                        log.error("在设备[" + deviceInfo + "]上找不到此服务:" + myService.getServiceUUID());
                    }

                    iterator.remove();
                }


            }
        }
    }

    private boolean processOperation(BleOperation myService, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        log.debug("开始:" + myService);

        boolean operateSuccess = false;
        BleOperation.Type operateType = myService.getOperateType();

        // Nofity或Indicate
        if (operateType == BleOperation.Type.Notify || operateType == BleOperation.Type.Indicate) {
            operateSuccess = notifyCharacteristic(
                    myService,
                    gatt,
                    characteristic,
                    operateType != BleOperation.Type.Notify);
        }
        // Read
        else if (operateType == BleOperation.Type.Read) {
            operateSuccess = readCharacteristic(myService, gatt,
                    characteristic);
        }
        // write
        else if (operateType == BleOperation.Type.Write
                || operateType == BleOperation.Type.WriteWithoutResponse) {


            writeLock.lock();
            try {
                // 如果要写入的数据超过20字节,则分多次写入
                byte[] dataToWrite = myService.getWritingData();
                for (int packetStart = 0; packetStart < dataToWrite.length; packetStart += 20) {

                    int packetEnd = (packetStart + 20) > dataToWrite.length ? dataToWrite.length : packetStart + 20;

                    byte[] packet = Arrays.copyOfRange(dataToWrite, packetStart, packetEnd);
                    operateSuccess = writeCharacteristic(
                            myService,
                            gatt,
                            characteristic,
                            packet,
                            operateType == BleOperation.Type.Write);

                    try {
                        writeCondition.await(200, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ignored) {
                    }

                    // 如果其中一个包发送失败,则不再发送余下数据
                    if (!operateSuccess) {
                        break;
                    }

                }
            } finally {
                writeLock.unlock();
            }
        }
        // DisableNotify or DisableIndicate
        else if (myService.getOperateType() == BleOperation.Type.DisableNotify || myService.getOperateType() == BleOperation.Type.DisableIndicate) {
            operateSuccess = true;

            BluetoothGattService s = gatt.getService(myService.getServiceUUID());
            if (s != null) {
                final BluetoothGattCharacteristic c = s.getCharacteristic(myService.getCharacteristicUUID());
                if (c != null) {
                    log.debug("停止 " + myService);
                    operateSuccess = disableNotifyCharacteristic(myService, gatt, c);
                }
            }

        } else {
            log.error("无效的 operateType" + operateType + " 来自" + myService);
        }
        return operateSuccess;
    }

    private boolean notifyCharacteristic(final BleOperation myService,
                                         BluetoothGatt gatt,
                                         final BluetoothGattCharacteristic characteristic, boolean isIndicate) {

        boolean result = false;
        if (gatt.setCharacteristicNotification(characteristic, true)) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(Constants.UUID_CONFIG);
            if (descriptor != null) {
                byte[] writingValue = !isIndicate ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                descriptor.setValue(writingValue);
                result = gatt.writeDescriptor(descriptor);
            } else {
                log.error("[" + myService + "]的characteristic[" + characteristic.getUuid() + "]上在找不到Config Descriptor");
            }
        }

        // 如果失败了
        if (!result) {
            mListener.operationEnableResult(myService.deviceAddress, myService.serviceUUID, characteristic.getUuid(), false);
        }
        return result;
    }


    private boolean disableNotifyCharacteristic(final BleOperation myService,
                                                BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {

        boolean result = gatt.setCharacteristicNotification(characteristic, false);
        if (!result) {
            log.error(myService.toString() + " 中的" + characteristic.getUuid().toString() + " 停止notify失败了");
        }
        return result;
    }

    private boolean readCharacteristic(BleOperation service, BluetoothGatt gatt,
                                       BluetoothGattCharacteristic characteristic) {
        boolean success = gatt.readCharacteristic(characteristic);
        if (!success) {
            mListener.operationEnableResult(service.deviceAddress, service.serviceUUID, characteristic.getUuid(), false);
        }
        return success;
    }

    private boolean writeCharacteristic(BleOperation service, BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic, byte[] data,
                                        boolean withResponse) {
        characteristic.setWriteType(withResponse ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        characteristic.setValue(data);
        boolean success = gatt.writeCharacteristic(characteristic);
        if (!success) {
            mListener.operationEnableResult(service.deviceAddress, service.serviceUUID, characteristic.getUuid(), false);
        }
        return success;
    }

    private final BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        waitBluetoothOpen();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        mContext.unregisterReceiver(this);
                        startConnect();
                        break;
                }
            }
        }
    };

}
