package sk.umb.bachelor.degree.posture_corrector;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.UUID;


public class PostureCorrectorDevice {
    public State getState() {
        return state;
    }

    public abstract static class PostureCorrectorDeviceCallback
    {
        public void onPostureChange(Posture posture) {}
        public void onConnect() {}
        public void onDisconnect() {}
    }
    public enum Posture {
        POSTURE_STRETCHED,
        POSTURE_HUNCHED
    }

    public enum State {
        STATE_DISCONNECTED,
        STATE_CONNECTED,
        STATE_CONNECTING
    }

    private State state = State.STATE_DISCONNECTED;
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    private static final UUID DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private Handler handler;

    private PostureCorrectorDeviceCallback callback;

    public PostureCorrectorDevice(Context context) {
        this.context = context;
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
        handler = new Handler();
    }

    @SuppressLint("MissingPermission")
    public boolean connectToDevice(String deviceAddress, PostureCorrectorDeviceCallback callback) {
        this.callback = callback;
        this.state = State.STATE_CONNECTING;
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
        return bluetoothGatt != null;
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        state = State.STATE_DISCONNECTED;
        callback.onPostureChange(null); // Trigger onPostureChange to save data
        callback.onDisconnect();
        this.callback = null;
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    private BluetoothGattCharacteristic dataCharacteristic;
    @SuppressLint("MissingPermission")
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                // Device connected, discover services
                Log.i("BLEManager", "Connected! Finding services");
                state = State.STATE_CONNECTED;
                callback.onConnect();
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i("BLEManager", "Disconnected :-(");
                bluetoothGatt.close();
                disconnect();
                // Device disconnected
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLEManager", "Got services");
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    Log.i("BLEManager", "Service found");
                    dataCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (dataCharacteristic != null) {
                        Log.i("BLEManager", "Characteristic found");
                        boolean success = gatt.setCharacteristicNotification(dataCharacteristic, true);
                        if (success) {
                            Log.i("BLEManager", "Set notification");
                            BluetoothGattDescriptor descriptor = dataCharacteristic.getDescriptor(DESCRIPTOR_UUID);
                            if (descriptor != null) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                                Log.i("BLEManager", "Enable notification");
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.getUuid().equals(DESCRIPTOR_UUID)) {
                boolean success = gatt.readCharacteristic(dataCharacteristic);
                Log.i("BLEManager", "readCharacteristic success: " + success);
            }
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            super.onCharacteristicRead(gatt, characteristic, value, status);
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getUuid().equals(CHARACTERISTIC_UUID)) {
                processDataCharacteristicChange(value);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            processDataCharacteristicChange(data);
        }

        private void processDataCharacteristicChange(byte[] data) {
            String stringData = (new String(data, StandardCharsets.US_ASCII));
            Log.i("BLEManager", "Descriptor changed: " + stringData);
            if (stringData.equals("ok")) {
                callback.onPostureChange(Posture.POSTURE_STRETCHED);
            } else if (stringData.equals("bad")) {
                callback.onPostureChange(Posture.POSTURE_HUNCHED);
            }
        }
    };
}