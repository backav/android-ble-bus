package li.xiangyang.android.blebus;

import android.os.Handler;

import java.util.UUID;

public class HandledBusListenner implements IListener {

    private final Handler mHandler;
    private final IListener mCustomListener;

    public HandledBusListenner(Handler handler, IListener listener) {
        this.mHandler = handler;
        this.mCustomListener = listener;
    }

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
    public void deviceRssiRead(final String address, final boolean success, final int rssi) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mCustomListener.deviceRssiRead(address, success, rssi);
            }
        });
    }

    @Override
    public void dataReceived(String address, UUID service, UUID characteristic, byte[] data) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mCustomListener.dataReceived(address, service, characteristic, data);
            }
        });
    }

    @Override
    public void bluetoothClosed() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mCustomListener.bluetoothClosed();
            }
        });
    }

    @Override
    public void operationReadResult(String address, UUID service, UUID characteristic, boolean success) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mCustomListener.operationReadResult(address, service, characteristic, success);
            }
        });
    }

    @Override
    public void operationWriteResult(String address, UUID service, UUID characteristic, boolean success) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mCustomListener.operationWriteResult(address, service, characteristic, success);
            }
        });
    }

    @Override
    public void operationEnableResult(String address, UUID service, UUID characteristic, boolean success) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mCustomListener.operationEnableResult(address, service, characteristic, success);
            }
        });
    }
}
