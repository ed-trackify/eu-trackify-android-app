package common;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.print.sdk.Barcode;
import com.android.print.sdk.PrinterConstants;
import com.android.print.sdk.PrinterInstance;
import eu.trackify.net.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.aflak.bluetooth.Bluetooth;
import me.aflak.bluetooth.interfaces.BluetoothCallback;

public class PrinterManager {

    public static PrinterManager Instance;
    
    // Supported printer names
    private static final String[] SUPPORTED_PRINTERS = {
        "PT-210",      // Goojprt PT-210
        "PT210",       // Alternative naming
        "GOOJPRT",     // Generic Goojprt
        "MTP-II",      // Legacy support
        "PTP-II"       // Alternative model
    };
    
    private static final String PREF_KEY_PRINTER_ADDRESS = "printer_bt_address";
    private static final String PREF_KEY_PRINTER_NAME = "printer_bt_name";

    public enum ConnectivityStatus {Connecting, Connected, Disconnected}

    public interface IPrinterStatus {
        void statusChanged(ConnectivityStatus status);
    }

    Context context;
    IPrinterStatus callback;
    Bluetooth bluetooth;
    PrinterInstance printerInstance;
    BluetoothAdapter bluetoothAdapter;
    
    // Stored printer info
    String savedPrinterAddress;
    String savedPrinterName;
    
    // Discovery
    AlertDialog discoveryDialog;
    List<BluetoothDevice> discoveredDevices;
    ArrayAdapter<String> deviceListAdapter;
    boolean isDiscovering = false;

    public void initialize(Context _context, IPrinterStatus _callback) {
        Instance = this;
        this.context = _context;
        callback = _callback;
        
        // Load saved printer
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        savedPrinterAddress = prefs.getString(PREF_KEY_PRINTER_ADDRESS, null);
        savedPrinterName = prefs.getString(PREF_KEY_PRINTER_NAME, null);

        // Check if Bluetooth/Printer is enabled in config
        if (!SettingsCtrl.isPrinterEnabled(context)) {
            if (callback != null) {
                callback.statusChanged(ConnectivityStatus.Disconnected);
            }
            return;
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            MessageCtrl.Toast("Bluetooth not supported on this device");
            return;
        }

        bluetooth = new Bluetooth(context);
        bluetooth.setBluetoothCallback(new BluetoothCallback() {
            @Override
            public void onBluetoothTurningOn() {}

            @Override
            public void onBluetoothOn() {
                // Try to connect to saved printer
                if (savedPrinterAddress != null) {
                    connectToDevice(savedPrinterAddress);
                }
            }

            @Override
            public void onBluetoothTurningOff() {}

            @Override
            public void onBluetoothOff() {
                if (callback != null) {
                    callback.statusChanged(ConnectivityStatus.Disconnected);
                }
            }

            @Override
            public void onUserDeniedActivation() {
                MessageCtrl.Toast("Bluetooth is required for printing");
            }
        });
    }

    public void showDiscoveryDialog() {
        if (!SettingsCtrl.isPrinterEnabled(context)) {
            MessageCtrl.Toast("Please enable printer in settings first");
            return;
        }
        
        if (!bluetoothAdapter.isEnabled()) {
            bluetooth.enable();
            return;
        }
        
        // Create discovery dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_printer_discovery, null);
        
        TextView tvInfo = dialogView.findViewById(R.id.tvInfo);
        LinearLayout llScanning = dialogView.findViewById(R.id.llScanning);
        LinearLayout llNoDevices = dialogView.findViewById(R.id.llNoDevices);
        ListView lvDevices = dialogView.findViewById(R.id.lvDevices);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnRescan = dialogView.findViewById(R.id.btnRescan);
        
        discoveredDevices = new ArrayList<>();
        deviceListAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = (TextView) view.findViewById(android.R.id.text1);
                text.setTextColor(context.getResources().getColor(R.color.text_primary));
                text.setPadding(16, 16, 16, 16);
                return view;
            }
        };
        lvDevices.setAdapter(deviceListAdapter);
        
        builder.setView(dialogView);
        discoveryDialog = builder.create();
        
        // Device selection
        lvDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < discoveredDevices.size()) {
                    BluetoothDevice device = discoveredDevices.get(position);
                    connectToDevice(device.getAddress());
                    savePreferredPrinter(device.getAddress(), device.getName());
                    discoveryDialog.dismiss();
                }
            }
        });
        
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopDiscovery();
                discoveryDialog.dismiss();
            }
        });
        
        btnRescan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDiscovery();
            }
        });
        
        discoveryDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                stopDiscovery();
            }
        });
        
        discoveryDialog.show();
        
        // Start discovery
        startDiscovery();
    }
    
    private void startDiscovery() {
        if (isDiscovering) return;
        
        discoveredDevices.clear();
        deviceListAdapter.clear();
        
        // First add paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (isSupportedPrinter(device.getName())) {
                discoveredDevices.add(device);
                String displayName = device.getName() + " (Paired)";
                if (device.getAddress().equals(savedPrinterAddress)) {
                    displayName += " ✓";
                }
                deviceListAdapter.add(displayName);
            }
        }
        
        if (discoveredDevices.size() > 0) {
            View dialogView = discoveryDialog.findViewById(android.R.id.content);
            dialogView.findViewById(R.id.llScanning).setVisibility(View.GONE);
            dialogView.findViewById(R.id.lvDevices).setVisibility(View.VISIBLE);
        }
        
        // Register discovery receiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(discoveryReceiver, filter);
        
        // Start discovery
        bluetoothAdapter.startDiscovery();
        isDiscovering = true;
    }
    
    private void stopDiscovery() {
        if (!isDiscovering) return;
        
        try {
            bluetoothAdapter.cancelDiscovery();
            context.unregisterReceiver(discoveryReceiver);
        } catch (Exception e) {
            // Receiver not registered
        }
        isDiscovering = false;
    }
    
    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && device.getName() != null && isSupportedPrinter(device.getName())) {
                    // Check if not already in list
                    boolean exists = false;
                    for (BluetoothDevice d : discoveredDevices) {
                        if (d.getAddress().equals(device.getAddress())) {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (!exists) {
                        discoveredDevices.add(device);
                        String displayName = device.getName();
                        if (device.getAddress().equals(savedPrinterAddress)) {
                            displayName += " ✓";
                        }
                        deviceListAdapter.add(displayName);
                        
                        // Update UI
                        if (discoveryDialog != null) {
                            View dialogView = discoveryDialog.findViewById(android.R.id.content);
                            dialogView.findViewById(R.id.llScanning).setVisibility(View.GONE);
                            dialogView.findViewById(R.id.lvDevices).setVisibility(View.VISIBLE);
                        }
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                isDiscovering = false;
                
                // Update UI
                if (discoveryDialog != null && discoveredDevices.isEmpty()) {
                    View dialogView = discoveryDialog.findViewById(android.R.id.content);
                    dialogView.findViewById(R.id.llScanning).setVisibility(View.GONE);
                    dialogView.findViewById(R.id.llNoDevices).setVisibility(View.VISIBLE);
                }
            }
        }
    };
    
    private boolean isSupportedPrinter(String deviceName) {
        if (deviceName == null) return false;
        
        String upperName = deviceName.toUpperCase();
        for (String printer : SUPPORTED_PRINTERS) {
            if (upperName.contains(printer)) {
                return true;
            }
        }
        return false;
    }
    
    private void savePreferredPrinter(String address, String name) {
        savedPrinterAddress = address;
        savedPrinterName = name;
        
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        prefs.edit()
            .putString(PREF_KEY_PRINTER_ADDRESS, address)
            .putString(PREF_KEY_PRINTER_NAME, name)
            .apply();
    }

    public void connect() {
        if (!SettingsCtrl.isPrinterEnabled(context)) {
            if (callback != null) {
                callback.statusChanged(ConnectivityStatus.Disconnected);
            }
            return;
        }
        
        if (isPrinterConnected(false)) {
            callback.statusChanged(ConnectivityStatus.Connected);
        } else if (savedPrinterAddress != null) {
            connectToDevice(savedPrinterAddress);
        } else {
            showDiscoveryDialog();
        }
    }
    
    private void connectToDevice(String address) {
        if (callback != null) {
            callback.statusChanged(ConnectivityStatus.Connecting);
        }
        
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            MessageCtrl.Toast("Printer not found");
            if (callback != null) {
                callback.statusChanged(ConnectivityStatus.Disconnected);
            }
            return;
        }
        
        printerInstance = new PrinterInstance(context, device, new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case PrinterConstants.Connect.SUCCESS: {
                        MessageCtrl.Toast("Printer connected: " + (savedPrinterName != null ? savedPrinterName : "PT-210"));
                        if (callback != null) {
                            callback.statusChanged(ConnectivityStatus.Connected);
                        }
                        break;
                    }
                    case PrinterConstants.Connect.CLOSED:
                    case PrinterConstants.Connect.FAILED:
                    case PrinterConstants.Connect.NODEVICE: {
                        if (callback != null) {
                            callback.statusChanged(ConnectivityStatus.Disconnected);
                        }
                        break;
                    }
                }
            }
        });
        printerInstance.openConnection();
    }

    public boolean isPrinterConnected(boolean showInfoIfNotConnected) {
        if (!SettingsCtrl.isPrinterEnabled(context)) {
            if (showInfoIfNotConnected) {
                MessageCtrl.Toast("Printer feature is disabled");
            }
            return false;
        }
        
        boolean connected = bluetooth != null && bluetooth.isEnabled() && 
                           printerInstance != null && printerInstance.isConnected();
        if (!connected && showInfoIfNotConnected) {
            MessageCtrl.Toast("Printer not connected");
        }
        return connected;
    }

    public boolean printShipment(ShipmentWithDetail detail) {
        try {
            if (isPrinterConnected(true)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        printerInstance.init();
                        printerInstance.setFont(0, 0, 0, 0);
                        printerInstance.setPrinter(PrinterConstants.Command.ALIGN, PrinterConstants.Command.ALIGN_CENTER);
                        
                        printText(context.getString(R.string.app_name), true, true, 2);
                        printBarcode(detail.tracking_id);
                        
                        printerInstance.setPrinter(PrinterConstants.Command.ALIGN, PrinterConstants.Command.ALIGN_LEFT);
                        printText("--------------------------------", false, false, 1);
                        printText("Tracking: " + detail.tracking_id, false, false, 1);
                        printText("Sender: " + detail.sender_name, false, false, 1);
                        printText("Receiver: " + detail.receiver_name, false, false, 1);
                        printText("Phone: " + detail.receiver_phone, false, false, 1);
                        printText("Address: " + detail.receiver_address, false, false, 1);
                        printText("City: " + detail.receiver_city, false, false, 1);
                        
                        if (!AppModel.IsNullOrEmpty(detail.receiver_cod) && !detail.receiver_cod.equals("0")) {
                            printText("--------------------------------", false, false, 1);
                            printText("COD: $" + detail.receiver_cod, true, false, 2);
                        }
                        
                        printText("--------------------------------", false, false, 1);
                        printText("Signature:", false, false, 1);
                        printText("\n\n_______________________\n", false, false, 1);
                        printerInstance.printText("\n\n\n\n");
                    }
                }).start();
                return true;
            }
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "printShipment");
            MessageCtrl.Toast(ex.getMessage());
        }
        return false;
    }

    private void printText(String text, boolean bold, boolean underline, int size) {
        int fontType = 0;
        if (bold) fontType |= 0x08;
        if (underline) fontType |= 0x04;
        
        printerInstance.setFont(0, size - 1, size - 1, fontType);
        printerInstance.printText(text + "\n");
    }

    private void printBarcode(String code) {
        Barcode barcode = new Barcode(PrinterConstants.BarcodeType.CODE128, 
                                      2, 100, 2, code);
        printerInstance.printBarCode(barcode);
    }

    public void onStart() {
        if (bluetooth != null && !bluetooth.isEnabled()) {
            bluetooth.enable();
        } else if (savedPrinterAddress != null) {
            connect();
        }
    }

    public boolean printReturnShipment(ReturnShipment detail) {
        try {
            if (isPrinterConnected(true)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        printerInstance.init();
                        printerInstance.setFont(0, 0, 0, 0);
                        printerInstance.setPrinter(PrinterConstants.Command.ALIGN, PrinterConstants.Command.ALIGN_CENTER);
                        
                        printText(context.getString(R.string.app_name), true, true, 2);
                        printText("RETURN SHIPMENT", true, false, 2);
                        printBarcode(detail.tracking_id);
                        
                        printerInstance.setPrinter(PrinterConstants.Command.ALIGN, PrinterConstants.Command.ALIGN_LEFT);
                        printText("--------------------------------", false, false, 1);
                        printText("Tracking: " + detail.tracking_id, false, false, 1);
                        printText("Sender: " + detail.sender_name, false, false, 1);
                        printText("Receiver: " + detail.receiver_name, false, false, 1);
                        printText("Phone: " + detail.receiver_phone, false, false, 1);
                        printText("Address: " + detail.receiver_address, false, false, 1);
                        printText("City: " + detail.receiver_city, false, false, 1);
                        
                        if (!AppModel.IsNullOrEmpty(detail.cod) && !detail.cod.equals("0")) {
                            printText("--------------------------------", false, false, 1);
                            printText("COD: $" + detail.cod, true, false, 2);
                        }
                        
                        if (!AppModel.IsNullOrEmpty(detail.special_instructions)) {
                            printText("--------------------------------", false, false, 1);
                            printText("Special Instructions:", false, false, 1);
                            printText(detail.special_instructions, false, false, 1);
                        }
                        
                        printText("--------------------------------", false, false, 1);
                        printText("Signature:", false, false, 1);
                        printText("\n\n_______________________\n", false, false, 1);
                        printerInstance.printText("\n\n\n\n");
                    }
                }).start();
                return true;
            }
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "printReturnShipment");
            MessageCtrl.Toast(ex.getMessage());
        }
        return false;
    }

    public void onStop() {
        if (bluetooth != null) {
            bluetooth.disable();
        }
        stopDiscovery();
    }
}