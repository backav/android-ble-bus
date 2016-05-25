package li.xiangyang.android.blebus_samples;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;

import li.xiangyang.android.blebus.BleBus;
import li.xiangyang.android.blebus.BleService;
import li.xiangyang.android.blebus.IBusListener;

public class HeartRateActivity extends Activity {
    private final static UUID UUID_SERVICE_HEARTRATE = UUID
            .fromString("0000180D-0000-1000-8000-00805f9b34fb");// 心率服务
    private final static UUID UUID_C_HEARTRATE = UUID
            .fromString("00002A37-0000-1000-8000-00805F9B34FB");// 心率特征

    private TextView txtHr;

    private String mDeviceAddress;
    private BleBus mBus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate);

        txtHr = (TextView) findViewById(R.id.txtHeartrate);

        Intent in = getIntent();
        mDeviceAddress = in.getStringExtra("address");

        mBus = new BleBus(this, new IBusListener() {
            @Override
            public void deviceConnected(String address) {
                toast("设备连接成功");
            }

            @Override
            public void deviceDisconnected(String address) {
                toast("设备断开连接");
            }

            @Override
            public void openBluetoothFailed() {
                toast("请打开蓝牙");
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            }

            @Override
            public void deviceRssiRead(String address, boolean success, int rssi) {

            }

            @Override
            public void listenOperateResult(BleService service, UUID characteristic, boolean success) {

            }

            @Override
            public void writeOperateResult(BleService service, UUID characteristic, boolean success) {

            }

            @Override
            public void readOperateResult(BleService service, UUID characteristic, boolean success) {

            }

            @Override
            public void dataReceived(BleService service, UUID characteristic, final byte[] data) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txtHr.setText("" + data[1]);
                    }
                });
            }
        });

        mBus.listen(new BleService(mDeviceAddress, UUID_SERVICE_HEARTRATE, UUID_C_HEARTRATE, BleService.OperateType.Notify));
    }


    @Override
    protected void onDestroy() {
        mBus.stop();
        super.onDestroy();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
