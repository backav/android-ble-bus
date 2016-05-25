package li.xiangyang.android.blebus_samples;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import li.xiangyang.android.midialog.OptionsDialog;

public class MainActivity extends Activity implements CommonListAdapter.ViewFormater<MainActivity.BleDevice> {

    private ListView mListView;

    private List<BleDevice> mDevices = new ArrayList<>();
    private CommonListAdapter<BleDevice> mAdapter;
    private Map<String, Integer> mDeviceRssi = new HashMap<>();

    private BluetoothAdapter mBluetoothAdapter;

    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        mListView = (ListView) findViewById(R.id.listView);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                new OptionsDialog(MainActivity.this, new OptionsDialog.IListener() {
                    @Override
                    public void onCancel() {
                    }

                    @Override
                    public void onSelected(int index, String option) {
                        if (index == 0) {
                            Intent in = new Intent(MainActivity.this, HeartRateActivity.class);
                            in.putExtra("address", mDevices.get(i).address);
                            startActivity(in);
                        }
                    }
                }, "设备类型", "心率").show();
            }
        });

        findViewById(R.id.btnScan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startScan();
            }
        });

        mAdapter = new CommonListAdapter<BleDevice>(this, mDevices, null, R.layout.item_device, 0, this);
        mListView.setAdapter(mAdapter);
    }

    @Override
    protected void onDestroy() {

        mBluetoothAdapter.stopLeScan(callback);

        super.onDestroy();
    }

    private void startScan() {
        mDeviceRssi.clear();
        mDevices.clear();
        mAdapter.notifyDataSetChanged();

        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.stopLeScan(callback);
            mBluetoothAdapter.startLeScan(callback);
        } else {
            Toast.makeText(this, "请打开蓝牙", Toast.LENGTH_SHORT).show();
        }
    }

    BluetoothAdapter.LeScanCallback callback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, final int i, byte[] bytes) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!mDeviceRssi.containsKey(bluetoothDevice.getAddress())) {
                        mDevices.add(new BleDevice(bluetoothDevice.getName(), bluetoothDevice.getAddress()));
                    }
                    mDeviceRssi.put(bluetoothDevice.getAddress(), i);
                    mAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    @Override
    public void formatItemView(BleDevice item, View view, int index, boolean group) {
        ViewHolder vh = (ViewHolder) view.getTag();
        if (vh == null) {
            vh = new ViewHolder((TextView) view.findViewById(R.id.txtName), (TextView) view.findViewById(R.id.txtRssi));
            view.setTag(vh);
        }

        vh.txtName.setText(item.name + "(" + item.address + ")");
        vh.txtRssi.setText(mDeviceRssi.get(item.address) + "");
    }

    class BleDevice {
        String name;
        String address;

        public BleDevice(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }

    class ViewHolder {
        TextView txtName;
        TextView txtRssi;

        public ViewHolder(TextView txtName, TextView txtRssi) {
            this.txtName = txtName;
            this.txtRssi = txtRssi;
        }
    }
}
