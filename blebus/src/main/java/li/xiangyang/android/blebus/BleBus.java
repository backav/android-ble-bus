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

@SuppressLint("NewApi")
public class BleBus {

    private final int SCAN_PERIOD = 5;// 搜索限制时间/重新连接时间 单位:秒

    private Context mContext;
    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean needCloseBleWhenStop = false;
    private boolean connecting = false;

    //保存需要处理的BleService
    private List<BleService> mServices = new ArrayList<BleService>();
    // 保存正在尝试连接的gatt
    private Map<String, BluetoothGatt> mConnectingGatts = new HashMap<>();// <Address,Gatt>
    // 保存连接成功的gatt
    private Map<String, BluetoothGatt> mConnectedGatts = new HashMap<String, BluetoothGatt>();// <Address,Gatt>

    private IBusListener mCustomListener;

    private ILogger log;

    /**
     * 初始化bus
     *
     * @param cxt
     * @param listener
     */
    public BleBus(Context cxt, IBusListener listener) {
        this(cxt, listener, new ILogger() {
            @Override
            public void error(Object msg) {
            }

            @Override
            public void debug(Object msg) {
            }

            @Override
            public void info(Object msg) {
            }

            @Override
            public void warn(Object msg) {
            }
        });
    }

    public BleBus(Context cxt, IBusListener listener, ILogger logger) {
        mContext = cxt;
        mCustomListener = listener;
        mHandler = new Handler();
        this.log = logger;
    }

    /**
     * 读取设备的rssi
     * 只有设备连接成功后,才会有Rssi结果
     *
     * @param device
     * @return
     */
    public boolean readRssi(String device) {
        BluetoothGatt gatt = mConnectedGatts.get(device);
        if (gatt != null) {
            return gatt.readRemoteRssi();
        }
        return false;
    }

    /**
     * 监听服务
     *
     * @param service
     */
    public boolean listen(BleService service) {

        if (mServices.contains(service)) {
            log.error("重复的操作 " + service);
            return false;
        }
        return processService(service);
    }

    public void unlisten(final BleService serviceMaybeNew) {

        final BleService service = getExistsService(serviceMaybeNew);
        if (service != null) {
            if (service.getOperateType() == BleService.OperateType.Notify || service.getOperateType() == BleService.OperateType.Indicate) {
                boolean operateSuccess = true;
                if (service.isOperating()) {
                    final BluetoothGatt gatt = mConnectedGatts.get(service.getDeviceAddress());
                    if (gatt != null) {
                        BluetoothGattService s = gatt.getService(service.getServiceUUID());
                        if (s != null) {
                            final BluetoothGattCharacteristic c = s.getCharacteristic(service.getCharacteristicUUID());
                            if (c != null) {
                                log.debug("停止 " + service);
                                operateSuccess = disableNotifyCharacteristic(service, gatt, c);
                            }
                        }
                    }
                }
                if (operateSuccess) {
                    mServices.remove(service);
                }

            } else {
                log.error(service + " 不是 notify 或 indicate 类型的");
            }
        }
    }

    /**
     * 读取设备的数据
     *
     * @return
     */
    public boolean read(final BleService service) {
        return processService(service);
    }

    /**
     * 写数据到设备
     *
     * @param service
     */
    public boolean write(final BleService service) {
        return processService(service);
    }

    private boolean processService(BleService service) {

        synchronized (mServices) {
            mServices.add(service);

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
                startConnect();
            }

            if (!operateSuccess) {
                mServices.remove(service);
            }
            return operateSuccess;
        }
    }

    /**
     * 关闭连接
     *
     * @param devices 如果devices为空,表示关闭所有连接
     */
    public void stop(String... devices) {


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
                for (Iterator<BleService> iterator = mServices.iterator(); iterator.hasNext(); ) {
                    BleService bleService = (BleService) iterator.next();
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
            } catch (Exception ex) {
            }

            log.warn("所有服务都被关闭了");
        }
        // 如果没有要监听的服务了,则停止搜索
        if (mServices.size() == 0 && needCloseBleWhenStop) {
            log.debug("关闭蓝牙");
            mBluetoothAdapter.disable();
        }
    }

    /**
     * 开始启动蓝牙
     */
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

            // 通知自动打开蓝牙失败了
            mListener.openBluetoothFailed();

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
            for (BleService bleService : mServices) {
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

    /**
     * 用于搜索到设备
     *
     * @param device
     */
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

    /**
     * 用于直接通过mac地址连接
     *
     * @param deviceAddress
     */
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
                for (BleService s : mServices) {
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

                synchronized (mServices) {
                    for (Iterator<BleService> iterator = mServices.iterator(); iterator.hasNext(); ) {
                        BleService service = iterator.next();
                        if (service.getDeviceAddress().equals(address)) {
                            service.resetOperating();
                        }
                    }
                }

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

            List<BleService> matches = new ArrayList<>(mServices.size());
            synchronized (mServices) {
                for (BleService service : mServices) {
                    if ((service.getOperateType() == BleService.OperateType.Notify || service.getOperateType() == BleService.OperateType.Indicate)
                            && matchService(service, gatt, characteristic)) {
                        matches.add(service);
                    }
                }
            }
            for (BleService service : matches) {
                mListener.dataReceived(service, characteristic.getUuid(), characteristic.getValue());
            }
            if (matches.size() == 0) {
                log.warn("接收到 notify(indicate)数据(" + characteristic.getUuid() + "),但是没有找到对应的 BleService");
            }
        }

        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {

            List<BleService> matches = new ArrayList<>(mServices.size());
            synchronized (mServices) {
                for (Iterator<BleService> iterator = mServices.iterator(); iterator.hasNext(); ) {
                    BleService service = iterator.next();
                    if (service.getOperateType() == BleService.OperateType.Read
                            && matchService(service, gatt, characteristic)) {
                        matches.add(service);
                        iterator.remove();// 接受到数据以后,就删除
                    }
                }
            }
            for (BleService service : matches) {
                mListener.dataReceived(service, characteristic.getUuid(), characteristic.getValue());
            }
            if (matches.size() == 0) {
                log.warn("接收到 read数据(" + characteristic.getUuid() + "),但是没有找到对应的 BleService");
            }
        }

        ;

        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          final BluetoothGattCharacteristic characteristic,
                                          final int status) {

            List<BleService> matches = new ArrayList<>(mServices.size());

            synchronized (mServices) {
                for (Iterator<BleService> iterator = mServices.iterator(); iterator.hasNext(); ) {
                    final BleService service = iterator.next();
                    if ((service.getOperateType() == BleService.OperateType.Write || service.getOperateType() == BleService.OperateType.WriteWithoutResponse)
                            && matchService(service, gatt, characteristic)) {
                        matches.add(service);
                        iterator.remove();
                    }
                }
            }
            for (BleService service : matches) {
                mListener.writeOperateResult(service,
                        characteristic.getUuid(),
                        status == BluetoothGatt.GATT_SUCCESS);
            }

            if (matches.size() == 0) {
                log.warn("接收到 write response (" + characteristic.getUuid() + "),但是没有找到对应的 BleService");
            }
        }

        public void onDescriptorWrite(BluetoothGatt gatt,
                                      final BluetoothGattDescriptor descriptor, final int status) {

            final boolean success = status == BluetoothGatt.GATT_SUCCESS;

            if (descriptor.getUuid().equals(Constants.UUID_CONFIG)) {

                List<BleService> matches = new ArrayList<>(mServices.size());
                synchronized (mServices) {
                    for (Iterator<BleService> iterator = mServices.iterator(); iterator.hasNext(); ) {
                        final BleService bleService = iterator.next();
                        if ((bleService.getOperateType() == BleService.OperateType.Notify || bleService.getOperateType() == BleService.OperateType.Indicate)
                                && matchService(bleService, gatt, descriptor.getCharacteristic())) {
                            matches.add(bleService);
                            if (!success) {
                                iterator.remove();
                            }
                        }
                    }
                }

                for (BleService service : matches) {
                    service.setOperating(success);
                    mListener.listenOperateResult(service, descriptor.getCharacteristic().getUuid(), success);
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
        log.debug("在设备[" + deviceInfo + "]上发现 " + ss.size() + " 个服务");

        if (ss == null || ss.size() <= 0) {
            return;
        }

        synchronized (mServices) {
            /**
             * 遍历所有mListenedServices,如果当前发现的服务中有需要的,则进一步处理
             */
            for (final Iterator<BleService> iterator = mServices.iterator(); iterator.hasNext(); ) {
                final BleService myService = iterator.next();

                if (myService.isOperating()) {
                    log.debug("Service[" + myService + "]上的所有操作都在进行中,不在重新执行");
                    continue;
                }

                boolean operateSuccess = false;
                BluetoothGattService deviceService = gatt.getService(myService.getServiceUUID());
                if (deviceService != null) {
                    List<BluetoothGattCharacteristic> cs = deviceService.getCharacteristics();
                    if (cs != null && cs.size() > 0) {
                        final BluetoothGattCharacteristic characteristic = deviceService.getCharacteristic(myService.getCharacteristicUUID());
                        if (characteristic != null) {
                            operateSuccess = processOperation(myService, gatt, characteristic);
                        } else {
                            log.error("在设备[" + deviceInfo + "]上找不到此属性:" + myService.getCharacteristicUUID());
                        }
                    } else {
                        log.error("在设备[" + deviceInfo + "]上找不到此属性:" + myService.getCharacteristicUUID());
                    }
                } else {
                    log.error("在设备[" + deviceInfo + "]上找不到此服务:" + myService.getServiceUUID());
                }

                if (!operateSuccess) {
                    iterator.remove();
                }
            }
        }
    }


    private boolean processOperation(BleService myService, BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        log.debug("开始:" + myService);
        boolean operateSuccess = false;
        BleService.OperateType operateType = myService.getOperateType();
        // Nofity或Indicate
        if (operateType == BleService.OperateType.Notify || operateType == BleService.OperateType.Indicate) {
            operateSuccess = notifyCharacteristic(
                    myService,
                    gatt,
                    characteristic,
                    operateType != BleService.OperateType.Notify);
        }
        // Read
        else if (operateType == BleService.OperateType.Read) {
            operateSuccess = readCharacteristic(myService, gatt,
                    characteristic);
        }
        // write
        else if (operateType == BleService.OperateType.Write
                || operateType == BleService.OperateType.WriteWithoutResponse) {

            // 如果要写入的数据超过20字节,则分多次写入
            byte[] dataToWrite = myService.getWritingData();
            for (int packetStart = 0; packetStart < dataToWrite.length; packetStart += 20) {

                int packetEnd = (packetStart + 20) < dataToWrite.length ? dataToWrite.length : packetStart + 20;

                byte[] packet = Arrays.copyOfRange(dataToWrite, packetStart, packetEnd);
                operateSuccess = writeCharacteristic(
                        myService,
                        gatt,
                        characteristic,
                        packet,
                        operateType == BleService.OperateType.Write);
                // 如果其中一个包发送失败,则不再发送余下数据
                if (!operateSuccess) {
                    break;
                }
            }
        } else {
            log.error("无效的 operateType" + operateType + " 来自" + myService);
        }
        return operateSuccess;
    }


    /**
     * 开启Notify/Indicate
     *
     * @param myService
     * @param gatt
     * @param characteristic
     * @param isIndicate
     * @return
     */
    private boolean notifyCharacteristic(final BleService myService,
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
            mListener.listenOperateResult(myService, characteristic.getUuid(), false);
        }
        myService.setOperating(result);
        return result;
    }


    /**
     * 关闭Notify/Indicate
     *
     * @param myService
     * @param gatt
     * @param characteristic
     * @return
     */
    private boolean disableNotifyCharacteristic(final BleService myService,
                                                BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {

        boolean result = gatt.setCharacteristicNotification(characteristic, false);
        if (result) {
            myService.setOperating(false);
        } else {
            log.error(myService.toString() + " 中的" + characteristic.getUuid().toString() + " 停止notify失败了");
        }
        return result;
    }

    /**
     * 读取
     *
     * @param service
     * @param gatt
     * @param characteristic
     * @return
     */
    private boolean readCharacteristic(BleService service, BluetoothGatt gatt,
                                       BluetoothGattCharacteristic characteristic) {
        boolean success = gatt.readCharacteristic(characteristic);
        if (!success) {
            mListener.readOperateResult(service, characteristic.getUuid(), false);
        }
        service.setOperating(success);
        return success;
    }

    /**
     * 写入
     *
     * @param service
     * @param gatt
     * @param characteristic
     * @param data
     * @param withResponse
     * @return
     */
    private boolean writeCharacteristic(BleService service, BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic, byte[] data,
                                        boolean withResponse) {
        characteristic.setWriteType(withResponse ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        characteristic.setValue(data);
        boolean success = gatt.writeCharacteristic(characteristic);
        if (!success) {
            mListener.writeOperateResult(service, characteristic.getUuid(), false);
        }

        service.setOperating(success);
        return success;
    }


    private BleService getExistsService(BleService service) {
        int index = mServices.indexOf(service);
        if (index >= 0) {
            return mServices.get(index);
        }
        return null;
    }


    private boolean matchService(BleService service, BluetoothGatt gatt,
                                 BluetoothGattCharacteristic characteristic) {
        return service.getCharacteristicUUID().equals(characteristic.getUuid())
                && service.getDeviceAddress().equals(gatt.getDevice().getAddress())
                && service.getServiceUUID().equals(characteristic.getService().getUuid());
    }


    /**
     * 默认的监听器,用于将所有的回调从主线程发送给用户指定的监听器
     */
    private IBusListener mListener = new IBusListener() {
        @Override
        public void deviceConnecting(final String address) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCustomListener.deviceConnecting(address);
                }
            });
        }

        @Override
        public void deviceConnectFail(final String address) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCustomListener.deviceConnectFail(address);
                }
            });
        }

        @Override
        public void deviceConnected(final String address) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCustomListener.deviceConnected(address);
                }
            });
        }

        @Override
        public void deviceDisconnected(final String address) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCustomListener.deviceDisconnected(address);
                }
            });
        }

        @Override
        public void openBluetoothFailed() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCustomListener.openBluetoothFailed();
                }
            });
        }

        @Override
        public void deviceRssiRead(final String address, final boolean success, final int rssi) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCustomListener.deviceRssiRead(address, success, rssi);
                }
            });
        }

        @Override
        public void listenOperateResult(final BleService service, final UUID characteristic, final boolean success) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCustomListener.listenOperateResult(service, characteristic, success);
                }
            });
        }

        @Override
        public void writeOperateResult(final BleService service, final UUID characteristic, final boolean success) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCustomListener.writeOperateResult(service, characteristic, success);
                }
            });
        }

        @Override
        public void readOperateResult(final BleService service, final UUID characteristic, final boolean success) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCustomListener.readOperateResult(service, characteristic, success);
                }
            });
        }

        @Override
        public void dataReceived(final BleService service, final UUID characteristic, final byte[] data) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
//                    log.info("收到数据:"+Utils.toHexString(data)+",来自:"+service);
                    mCustomListener.dataReceived(service, characteristic, data);
                }
            });
        }
    };

    private final BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
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
