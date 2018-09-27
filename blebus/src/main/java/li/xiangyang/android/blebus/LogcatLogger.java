package li.xiangyang.android.blebus;

import android.util.Log;

public class LogcatLogger implements ILogger {

    private final String TAG = "blebus";

    @Override
    public void error(Object msg) {
        Log.e(TAG, msg.toString());
    }

    @Override
    public void debug(Object msg) {
        Log.d(TAG, msg.toString());
    }

    @Override
    public void info(Object msg) {
        Log.i(TAG, msg.toString());
    }

    @Override
    public void warn(Object msg) {
        Log.w(TAG, msg.toString());
    }
}
