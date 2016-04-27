package li.xiangyang.android.blebus;


/**
 * Created by bac on 16/4/27.
 */
public interface ILogger {

    void error(Object msg);

    void debug(Object msg);

    void info(Object msg);
    void warn(Object msg);
}
