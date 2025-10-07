package common;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.android.print.sdk.Barcode;
import com.android.print.sdk.PrinterConstants;
import com.android.print.sdk.PrinterInstance;
import eu.trackify.net.R;

import me.aflak.bluetooth.Bluetooth;
import me.aflak.bluetooth.interfaces.BluetoothCallback;

public class Printer {

    public static Printer Instance;

    // Support multiple printer names including Goojprt PT-210
    public final String[] SUPPORTED_PRINTERS = {
        "PT-210",      // Goojprt PT-210
        "PT210",       // Alternative naming
        "GOOJPRT",     // Generic Goojprt
        "MTP-II"       // Legacy support
    };

    public enum ConnectivityStatus {Connecting, Connected, Disconnected}

    public interface IPrinterStatus {
        void statusChanged(ConnectivityStatus status);
    }

    Context context;
    IPrinterStatus callback;
    Bluetooth bluetooth;
    PrinterInstance printerInstance;

    public void initialize(Context _context, IPrinterStatus _callback) {
        Instance = this;

        this.context = _context;
        callback = _callback;

        // Check if Bluetooth/Printer is enabled in config
        if (!AppConfig.isBluetoothEnabled() || !AppConfig.isPrinterEnabled()) {
            // Feature disabled - immediately return disconnected status
            if (callback != null) {
                callback.statusChanged(ConnectivityStatus.Disconnected);
            }
            return;
        }

        bluetooth = new Bluetooth(context);
        bluetooth.setBluetoothCallback(new BluetoothCallback() {
            @Override
            public void onBluetoothTurningOn() {

            }

            @Override
            public void onBluetoothOn() {
                connect();
            }

            @Override
            public void onBluetoothTurningOff() {

            }

            @Override
            public void onBluetoothOff() {

            }

            @Override
            public void onUserDeniedActivation() {

            }
        });
    }

    public void connect() {
        // Check if Bluetooth/Printer is enabled in config
        if (!AppConfig.isBluetoothEnabled() || !AppConfig.isPrinterEnabled()) {
            if (callback != null) {
                callback.statusChanged(ConnectivityStatus.Disconnected);
            }
            return;
        }
        
        if (isPrinterConnected(false))
            callback.statusChanged(ConnectivityStatus.Connected);
        else {
            if (bluetooth != null && bluetooth.isEnabled()) {
                callback.statusChanged(ConnectivityStatus.Connecting);

                // Look for any supported printer
                BluetoothDevice device = null;
                for (BluetoothDevice pairedDevice : bluetooth.getPairedDevices()) {
                    String deviceName = pairedDevice.getName();
                    if (deviceName != null) {
                        for (String supportedPrinter : SUPPORTED_PRINTERS) {
                            if (deviceName.toUpperCase().contains(supportedPrinter.toUpperCase())) {
                                device = pairedDevice;
                                MessageCtrl.Toast("Found printer: " + deviceName);
                                break;
                            }
                        }
                        if (device != null) break;
                    }
                }
                
                if (device == null) {
                    callback.statusChanged(ConnectivityStatus.Disconnected);
                    MessageCtrl.Toast("No supported printer found. Looking for: PT-210, GOOJPRT, or MTP-II");
                    return;
                }

                //MessageCtrl.Toast("Paired bluetooth device found with name: " + BLUETOOTH_DEVICE_NAME);
                printerInstance = new PrinterInstance(context, device, new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        switch (msg.what) {
                            case PrinterConstants.Connect.SUCCESS: {
                                //MessageCtrl.Toast("Printer connected");
                                callback.statusChanged(ConnectivityStatus.Connected);
                                break;
                            }
                            case PrinterConstants.Connect.CLOSED:
                            case PrinterConstants.Connect.FAILED:
                            case PrinterConstants.Connect.NODEVICE: {
                                //MessageCtrl.Toast("Printer not found");
                                callback.statusChanged(ConnectivityStatus.Disconnected);
                                break;
                            }
                        }
                    }
                });
                printerInstance.openConnection();
            } else
                callback.statusChanged(ConnectivityStatus.Disconnected);
        }
    }

    public boolean isPrinterConnected(boolean showInfoIfNotConnected) {
        // Check if feature is disabled
        if (!AppConfig.isBluetoothEnabled() || !AppConfig.isPrinterEnabled()) {
            if (showInfoIfNotConnected) {
                MessageCtrl.Toast("Printer feature is disabled");
            }
            return false;
        }
        
        boolean connected = bluetooth != null && bluetooth.isEnabled() && printerInstance != null && printerInstance.isConnected();
        if (!connected && showInfoIfNotConnected)
            MessageCtrl.Toast("Printer not connected");
        return connected;
    }

    public boolean printShipment(ShipmentWithDetail detail) {
        try {
            if (isPrinterConnected(true)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        printerInstance.init();
                        printText(context.getString(R.string.app_name), true, true, 2);
                        printBarcode(detail.tracking_id);
                        printText("Sender: " + detail.sender_name, false, false, 1);
                        printText("Receiver: " + detail.receiver_name, false, false, 1);
                        printText("Phone: " + detail.receiver_phone, false, false, 1);
                        printText("Address: " + detail.receiver_address, false, false, 1);
                        printText("City: " + detail.receiver_city, false, false, 1);
                        printText("COD: " + detail.receiver_cod, false, false, 1);
                        printerInstance.printText("\n\n\n\n");
                    }
                }).start();

                return true;
            }
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "sendImageToPrint");
            MessageCtrl.Toast(ex.getMessage());
        }
        return false;
    }

    public boolean printReturnShipment(ReturnShipment detail) {
        try {
            if (isPrinterConnected(true)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        printerInstance.init();
                        printText(context.getString(R.string.app_name), true, true, 1);
                        printText("Return Shipment", true, true, 2);
                        printBarcode(detail.tracking_id);
                        printText("Sender: " + detail.sender_name, false, false, 1);
                        printText("Receiver: " + detail.receiver_name, false, false, 1);
                        printText("Phone: " + detail.receiver_phone, false, false, 1);
                        printText("Address: " + detail.receiver_address, false, false, 1);
                        printText("City: " + detail.receiver_city, false, false, 1);
                        printText("COD: " + detail.cod, false, false, 1);
                        printText("Special Instructions: " + detail.special_instructions, false, false, 1);
                        printerInstance.printText("\n\n\n\n");
                    }
                }).start();

                return true;
            }
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "sendImageToPrint");
            MessageCtrl.Toast(ex.getMessage());
        }
        return false;
    }

    private void printBarcode(String content) {
        // ---------- Barcode: Code128
        // param1：bar code width, 2<=n<=6，default is 2.
        // param2：bar code height, 1<=n<=255，default is 162.
        // param3：bar code note position, 0-don’t print, 1-above,2-below,3-both.

        // ---------- QR Code
        // param1：Graphical version，1<=n<=30(0:auto select)。
        // param2：Error correction level，n = 76,77,81,72(L:7%,M:15%,Q:25%,H:30%)。
        // param3：Longitudinal magnification。

        //Barcode barcode4 = new Barcode(PrinterConstants.BarcodeType.QRCODE, 0, 3, 6, content);
        Barcode barcode4 = new Barcode(PrinterConstants.BarcodeType.CODE128, 3, 162, 2, content);
        printerInstance.printBarCode(barcode4);
        printerInstance.setPrinter(PrinterConstants.Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
    }

    private void printText(String text, boolean center, boolean bold, int newLinesAfterText) {
        printerInstance.setFont(0, 0, bold ? 1 : 0, 0);
        printerInstance.setPrinter(PrinterConstants.Command.ALIGN, center ? PrinterConstants.Command.ALIGN_CENTER : PrinterConstants.Command.ALIGN_LEFT);
        printerInstance.printText(text == null ? "" : text);
        printerInstance.setPrinter(PrinterConstants.Command.PRINT_AND_WAKE_PAPER_BY_LINE, newLinesAfterText);
    }

    public void onStart() {
        // Check if feature is disabled
        if (!AppConfig.isBluetoothEnabled() || !AppConfig.isPrinterEnabled()) {
            return;
        }
        
        if (bluetooth != null) {
            bluetooth.onStart();
        }
        if (bluetooth != null && bluetooth.isEnabled()) {
            connect();
        } else {
            bluetooth.showEnableDialog(App.Object);
        }
    }

    public void onStop() {
        // Check if feature is disabled
        if (!AppConfig.isBluetoothEnabled() || !AppConfig.isPrinterEnabled()) {
            return;
        }
        
        if (bluetooth != null) {
            bluetooth.onStop();
        }
    }
}
