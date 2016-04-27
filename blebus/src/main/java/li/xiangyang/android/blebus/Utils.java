package li.xiangyang.android.blebus;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Utils {

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static final int DATA_TYPE_MANUFACTURER_SPECIFIC_DATA = 0xFF;

    public static String toHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] getManufacturerDataFromScanRecord(byte[] scanRecord) {

        int currentPos = 0;
        while (currentPos < scanRecord.length) {
            int length = scanRecord[currentPos++] & 0xFF;
            if (length == 0) {
                break;
            }
            int dataLength = length - 1;
            if (dataLength > 0) {
                int fieldType = scanRecord[currentPos++] & 0xFF;
                switch (fieldType) {
                    case DATA_TYPE_MANUFACTURER_SPECIFIC_DATA:
//                        int manufacturerId = ((scanRecord[currentPos + 1] & 0xFF) << 8) + (scanRecord[currentPos] & 0xFF);
                        byte[] manufacturerDataBytes = extractBytes(scanRecord, currentPos + 2,
                                dataLength - 2);
                        return manufacturerDataBytes;
                    default:
                        break;
                }
            }
            currentPos += dataLength;
        }
        return null;
    }

    private static byte[] extractBytes(byte[] scanRecord, int start, int length) {
        byte[] bytes = new byte[length];
        System.arraycopy(scanRecord, start, bytes, 0, length);
        return bytes;
    }

    public static long unsignedIntToLong(byte[] data, int index) {
        ByteBuffer bb = ByteBuffer.wrap(data, index, 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return bb.getInt() & 0xFFFFFFFF;
    }

    public static int unsignedShortToInt(byte[] data, int index) {
        ByteBuffer bb = ByteBuffer.wrap(data, index, 2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return bb.getShort() & 0xFFFF;
    }
}
