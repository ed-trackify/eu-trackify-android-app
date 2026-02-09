package common;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.FragmentActivity;

import eu.trackify.net.R;
import com.google.zxing.client.android.Intents;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import common.Communicator.IServerResponse;
import common.UserRef.UserType;

public class App extends FragmentActivity {

    public static final int ZBAR_SCANNER_REQUEST = 0;

    AppModel Model;
    public static App Object;
    public static UserRef CurrentUser = null;

    // Controls
    public View splashCtrl;
    public LoginCtrl loginCtrl;
    public UserDistributorTabCtrl userDistributorTabCtrl;
    public Draggable_UserDistributorShipmentsFragment userDistributorMyShipmentsFragment;
    public UserDistributorShipmentsFragment userDistributorReconcileShipmentsFragment;
    public UserDistributorShipmentsFragment userDistributorReturnShipmentsFragment;
    public UserWarehouseManagerCtrl userWarehouseManagerCtrl;
    public UserWarehouseAdminCtrl userWarehouseAdminCtrl;
    public UserPackerCtrl userPackerCtrl;
    public SendProblemCommentsCtrl sendProblemCommentsCtrl;
    public SendFileCommentsCtrl sendFileCommentsCtrl;
    public ImageUploadCtrl imageUploadCtrl;
    public SettingsCtrl settingsCtrl;
    public ChangePasswordDialog changePasswordDialog;
    public UserDistributorShipmentDetailTabCtrl userDistributorShipmentDetailTabCtrl;
    public UserDistributorShipmentDetail userDistributorShipmentDetail;
    public UserDistributorNotesFragment userDistributorNotesFragment;
    public UserDistributorShipmentPicturesFragment userDistributorPicturesFragment;
    public UserDistributorSMSHistoryFragment userDistributorSMSHistoryFragment;
    public UserDistributorExpenses userDistributorExpenses;
    public UserDistributorDriverCtrl userDistributorDriverCtrl;
    public ModalCtrl modalCtrl;
    public SignatureCtrl signatureCtrl;
    public RoutingCtrl routingCtrl;
    public ViewReturnShipmentDetail viewReturnShipmentDetail;
    public StatusCheckCtrl statusCheckCtrl;
    public ReturnReceivedCtrl returnReceivedCtrl;

    View offlineIndicator;
    View ll_Topbar;
    ImageView iv_Settings, iv_Refresh, iv_Route, iv_ConnectPrinter;

    public ActivityResultLauncher<Intent> cameraLauncher;

    public ActivityResultLauncher<Intent> filePickerLauncher;
    
    public ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(updateBaseContextLocale(base));
    }

    private Context updateBaseContextLocale(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        String languageCode = prefs.getString("app_language", "en");

        java.util.Locale locale = new java.util.Locale(languageCode);
        java.util.Locale.setDefault(locale);

        android.content.res.Resources resources = context.getResources();
        android.content.res.Configuration config = new android.content.res.Configuration(resources.getConfiguration());

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            resources.updateConfiguration(config, resources.getDisplayMetrics());
            return context;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Object = this;
        Model = new AppModel(getApplicationContext());

        // Apply saved language preference
        SettingsCtrl.applyLanguage(this);

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
                String source = paramThread != null ? paramThread.getName() : "";
                String stack = AppModel.getStackTrace(paramThrowable);
                AppModel.WriteLog(AppModel.LOG_FILE_NAME, source, stack);
            }
        });

        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Method m = StrictMode.class.getMethod("disableDeathOnFileUriExposure");
                m.invoke(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        setContentView(R.layout._main);

        splashCtrl = findViewById(R.id.splashCtrl);
        modalCtrl = (ModalCtrl) findViewById(R.id.modalCtrl);
        loginCtrl = (LoginCtrl) findViewById(R.id.loginCtrl);
        userDistributorTabCtrl = (UserDistributorTabCtrl) findViewById(R.id.userDistributorTabCtrl);
        userWarehouseManagerCtrl = (UserWarehouseManagerCtrl) findViewById(R.id.userWarehouseManagerCtrl);
        userWarehouseAdminCtrl = (UserWarehouseAdminCtrl) findViewById(R.id.userWarehouseAdminCtrl);
        userPackerCtrl = (UserPackerCtrl) findViewById(R.id.userPackerCtrl);
        userDistributorDriverCtrl = (UserDistributorDriverCtrl) findViewById(R.id.userDistributorDriverCtrl);
        sendProblemCommentsCtrl = (SendProblemCommentsCtrl) findViewById(R.id.sendCommentsCtrl);
        sendFileCommentsCtrl = (SendFileCommentsCtrl) findViewById(R.id.sendFileCommentsCtrl);
        imageUploadCtrl = (ImageUploadCtrl) findViewById(R.id.imageUploadCtrl);
        userDistributorShipmentDetailTabCtrl = (UserDistributorShipmentDetailTabCtrl) findViewById(R.id.userDistributorShipmentDetailTabCtrl);
        signatureCtrl = (SignatureCtrl) findViewById(R.id.signatureCtrl);
        routingCtrl = (RoutingCtrl) findViewById(R.id.routingCtrl);
        viewReturnShipmentDetail = (ViewReturnShipmentDetail) findViewById(R.id.viewReturnShipmentDetail);
        statusCheckCtrl = (StatusCheckCtrl) findViewById(R.id.statusCheckCtrl);
        returnReceivedCtrl = (ReturnReceivedCtrl) findViewById(R.id.returnReceivedCtrl);
        settingsCtrl = (SettingsCtrl) findViewById(R.id.settingsCtrl);
        changePasswordDialog = (ChangePasswordDialog) findViewById(R.id.changePasswordDialog);
        offlineIndicator = findViewById(R.id.offline_indicator);
        ll_Topbar = findViewById(R.id.ll_Topbar);

        Model.db.ProcessCreateDatabase();
        DatabaseManager.IsDatabaseReady = true;

        iv_Settings = (ImageView) findViewById(R.id.iv_Settings);
        iv_Settings.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                settingsCtrl.SetVisibility(true);
            }
        });

        iv_Refresh = (ImageView) findViewById(R.id.iv_Refresh);
        iv_Refresh.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                // Start rotation animation
                Animation rotation = AnimationUtils.loadAnimation(App.Object, R.anim.rotate_refresh);
                iv_Refresh.startAnimation(rotation);

                // Refresh data - with null checks to prevent crashes during phone calls
                try {
                    if (App.Object.userDistributorMyShipmentsFragment != null) {
                        App.Object.userDistributorMyShipmentsFragment.Load();
                    }
                    if (App.Object.userDistributorReconcileShipmentsFragment != null) {
                        App.Object.userDistributorReconcileShipmentsFragment.Load();
                    }
                    if (App.Object.userDistributorReturnShipmentsFragment != null) {
                        App.Object.userDistributorReturnShipmentsFragment.Load();
                    }
                } catch (Exception e) {
                    AppModel.ApplicationError(e, "App::RefreshButton - Fragment access error");
                }

                // Stop animation after 2 seconds
                iv_Refresh.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (iv_Refresh != null) {
                            iv_Refresh.clearAnimation();
                        }
                    }
                }, 2000);
            }
        });

        iv_Route = (ImageView) findViewById(R.id.iv_Route);
        iv_Route.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (routingCtrl.getVisibility() == View.VISIBLE) {
                    routingCtrl.setVisibility(View.GONE);
                    iv_Route.setImageResource(R.drawable.location);
                } else {
                    routingCtrl.setVisibility(View.VISIBLE);
                    iv_Route.setImageResource(R.drawable.listing);
                }
            }
        });

        iv_ConnectPrinter = (ImageView) findViewById(R.id.iv_ConnectPrinter);
        
        // Initialize printer - check settings dynamically
        updatePrinterVisibility();
        
        iv_ConnectPrinter.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (!SettingsCtrl.isPrinterEnabled(App.Object)) {
                    MessageCtrl.Toast(getString(R.string.settings_printer_enable_first));
                    settingsCtrl.SetVisibility(true);
                } else if (PrinterManager.Instance != null) {
                    // Show printer discovery dialog
                    PrinterManager.Instance.showDiscoveryDialog();
                } else if (Printer.Instance != null) {
                    // Fallback to old implementation
                    Printer.Instance.connect();
                }
            }
        });
        
        // TEMPORARILY DISABLED: Printer initialization disabled for debugging
        // Initialize printer manager
        /*
        (new PrinterManager()).initialize(this, new PrinterManager.IPrinterStatus() {
            @Override
            public void statusChanged(PrinterManager.ConnectivityStatus status) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updatePrinterIcon(status);
                    }
                });
            }
        });

        // Fallback to old Printer class if needed
        if (PrinterManager.Instance == null) {
            (new Printer()).initialize(this, new Printer.IPrinterStatus() {
                @Override
                public void statusChanged(Printer.ConnectivityStatus status) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (status == Printer.ConnectivityStatus.Connected)
                                iv_ConnectPrinter.setImageResource(R.drawable.print_connected);
                            else if (status == Printer.ConnectivityStatus.Connecting)
                                iv_ConnectPrinter.setImageResource(R.drawable.print_connecting);
                            else
                                iv_ConnectPrinter.setImageResource(R.drawable.print_not_connect);
                        }
                    });
                }
            });
        }
        */

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                // To show splash screen..
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Build permission list based on Android version
                java.util.ArrayList<String> permissionList = new java.util.ArrayList<>();

                // Storage permissions (different for Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionList.add(Manifest.permission.READ_MEDIA_IMAGES);
                } else {
                    permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                    permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }

                // Phone & SMS permissions
                permissionList.add(Manifest.permission.CALL_PHONE);
                permissionList.add(Manifest.permission.SEND_SMS);
                permissionList.add(Manifest.permission.READ_SMS);
                permissionList.add(Manifest.permission.RECEIVE_SMS);
                permissionList.add(Manifest.permission.READ_PHONE_STATE);

                // Location permissions
                permissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
                permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);

                // Camera & Audio
                permissionList.add(Manifest.permission.CAMERA);
                permissionList.add(Manifest.permission.RECORD_AUDIO);

                // Bluetooth permissions (different for Android 12+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionList.add(Manifest.permission.BLUETOOTH_CONNECT);
                    permissionList.add(Manifest.permission.BLUETOOTH_SCAN);
                } else {
                    permissionList.add(Manifest.permission.BLUETOOTH);
                    permissionList.add(Manifest.permission.BLUETOOTH_ADMIN);
                }

                checkPermission(App.Object, new Runnable() {
                            @Override
                            public void run() {

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            ShowCtrl(UserType.None);

                                            try {
                                                if (loginCtrl.AutoLogin(new IServerResponse() {
                                                    @Override
                                                    public void onCompleted(boolean success, String messageToShow, Object... objs) {
                                                        splashCtrl.setVisibility(View.GONE);
                                                    }
                                                })) {
                                                } else {
                                                    splashCtrl.setVisibility(View.GONE);
                                                }
                                            } catch (Exception autoLoginEx) {
                                                AppModel.ApplicationError(autoLoginEx, "AutoLogin failed");
                                                splashCtrl.setVisibility(View.GONE);
                                                // Show login screen even if AutoLogin fails
                                            }

                                        } catch (Exception ex) {
                                            AppModel.ApplicationError(ex, "checkPermission");
                                            splashCtrl.setVisibility(View.GONE);
                                        }
                                    }
                                });
                            }
                        }, permissionList.toArray(new String[0]));
            }
        });
        t.start();

        Model.Start();

        // Register network connectivity monitoring for offline indicator
        registerNetworkCallback();

        // Try to increase SMS limit to 100 (requires root)
        try {
            SMSLimitModifier.setupSMSLimit(this);
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "SMS limit modifier failed");
        }

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        AttachmentManager.onImageCaptured();
                    } else {
                        MessageCtrl.Toast(getString(R.string.error_image_capture_canceled));
                    }
                }
        );

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        AttachmentManager.OnFileSelect(this, result.getData().getData(), () -> {

                        });
                    }
                }
        );
        
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        if (imageUploadCtrl != null && imageUploadCtrl.getVisibility() == View.VISIBLE) {
                            imageUploadCtrl.onGalleryImageSelected(result.getData().getData());
                        }
                    }
                }
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (SettingsCtrl.isPrinterEnabled(this)) {
            if (PrinterManager.Instance != null) {
                PrinterManager.Instance.onStart();
            } else if (Printer.Instance != null) {
                Printer.Instance.onStart();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Log when fragments might have been destroyed during phone call or app pause
        try {
            if (userDistributorTabCtrl != null && userDistributorTabCtrl.getVisibility() == View.VISIBLE) {
                // Check if fragments are null (destroyed by system)
                if (userDistributorMyShipmentsFragment == null ||
                    userDistributorReconcileShipmentsFragment == null ||
                    userDistributorReturnShipmentsFragment == null) {
                    AppModel.ApplicationError(null, "App::onResume - Warning: Fragments were destroyed by system (likely due to phone call or memory pressure)");
                    // Fragments will be recreated automatically when needed
                    // The null checks we added will prevent crashes in the meantime
                }
            }
        } catch (Exception e) {
            AppModel.ApplicationError(e, "App::onResume - Error checking fragment state");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (PrinterManager.Instance != null) {
            PrinterManager.Instance.onStop();
        } else if (Printer.Instance != null) {
            Printer.Instance.onStop();
        }
    }

    // Register the launcher and result handler
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if (result.getContents() == null) {

                } else {
                    try {
                        // Pass raw barcode content to fragments
                        // Each fragment/controller handles parsing appropriately:
                        // - Normal scans: BarcodeParser.extractTrackingNumber() for single best match
                        // - StatusCheck/ReturnReceived: BarcodeParser.extractCandidates() for multi-try
                        String rawCode = result.getContents();
                        AppModel.ApplicationError(null, "BARCODE: Raw=" + rawCode.length() + " chars");

                        if (userDistributorTabCtrl != null && userDistributorTabCtrl.getVisibility() == View.VISIBLE) {
                            if (userDistributorMyShipmentsFragment != null && userDistributorTabCtrl.isVisible(userDistributorMyShipmentsFragment))
                                userDistributorMyShipmentsFragment.SetScannedCode(rawCode);
                            else if (userDistributorReturnShipmentsFragment != null && userDistributorTabCtrl.isVisible(userDistributorReturnShipmentsFragment))
                                userDistributorReturnShipmentsFragment.SetScannedCode(rawCode);
                        } else if (userWarehouseManagerCtrl != null && userWarehouseManagerCtrl.getVisibility() == View.VISIBLE)
                            userWarehouseManagerCtrl.SetScannedCode(rawCode);
                        else if (userWarehouseAdminCtrl != null && userWarehouseAdminCtrl.getVisibility() == View.VISIBLE)
                            userWarehouseAdminCtrl.SetScannedCode(rawCode);
                        else if (userPackerCtrl != null && userPackerCtrl.getVisibility() == View.VISIBLE)
                            userPackerCtrl.SetScannedCode(rawCode);
                        else if (userDistributorDriverCtrl != null && userDistributorDriverCtrl.getVisibility() == View.VISIBLE)
                            userDistributorDriverCtrl.SetScannedCode(rawCode);
                    } catch (Exception e) {
                        AppModel.ApplicationError(e, "App::BarcodeScanner - Fragment access error");
                    }
                }
            });

    public void Logout() {
        CurrentUser = null;
        AppModel.Object.SaveVariable(AppModel.USER_CACHE_KEY, "");
        AppModel.Object.SaveVariable(AppModel.USER_KEY, "");
        AppModel.Object.SaveVariable(AppModel.PASS_KEY, "");
        ShowCtrl(UserType.None);
    }
    
    public void updatePrinterVisibility() {
        if (iv_ConnectPrinter != null) {
            // Printer feature disabled for now
            iv_ConnectPrinter.setVisibility(View.GONE);
            /*if (SettingsCtrl.isPrinterEnabled(this)) {
                iv_ConnectPrinter.setVisibility(View.VISIBLE);
            } else {
                iv_ConnectPrinter.setVisibility(View.GONE);
            }*/
        }
    }
    
    private void updatePrinterIcon(PrinterManager.ConnectivityStatus status) {
        if (iv_ConnectPrinter != null) {
            if (status == PrinterManager.ConnectivityStatus.Connected)
                iv_ConnectPrinter.setImageResource(R.drawable.print_connected);
            else if (status == PrinterManager.ConnectivityStatus.Connecting)
                iv_ConnectPrinter.setImageResource(R.drawable.print_connecting);
            else
                iv_ConnectPrinter.setImageResource(R.drawable.print_not_connect);
        }
    }

    private void registerNetworkCallback() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(new android.net.ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(android.net.Network network) {
                        runOnUiThread(() -> {
                            if (offlineIndicator != null) offlineIndicator.setVisibility(View.GONE);
                        });
                    }

                    @Override
                    public void onLost(android.net.Network network) {
                        runOnUiThread(() -> {
                            if (offlineIndicator != null) offlineIndicator.setVisibility(View.VISIBLE);
                        });
                    }
                });
            }
        } catch (Exception e) {
            AppModel.ApplicationError(e, "App::registerNetworkCallback");
        }
    }

    public void ApplySettings(AppSetting settings) {
        // Printer feature disabled for now
        iv_ConnectPrinter.setVisibility(View.GONE);
        //iv_ConnectPrinter.setVisibility(settings.showPrintLabel() ? View.VISIBLE : View.GONE);
        iv_Route.setVisibility(settings.showRouting() ? View.VISIBLE : View.GONE);
    }

    public static void checkPermission(final Activity activity, final Runnable onPermissionGranted, String... permissions) {
        Dexter.withContext(activity)
                .withPermissions(permissions
                ).withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        try {
                            if (report.areAllPermissionsGranted()) {
                                onPermissionGranted.run();
                            } else if (report.isAnyPermissionPermanentlyDenied()) {
                                // Some permissions are permanently denied - show dialog to open settings
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        showPermissionSettingsDialog(activity, onPermissionGranted);
                                    }
                                });
                            } else {
                                // Permissions denied but not permanently - proceed anyway with limited functionality
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            MessageCtrl.Toast(activity.getString(R.string.error_permissions_limited));
                                            // Continue with limited functionality instead of closing
                                            onPermissionGranted.run();
                                        } catch (Exception e) {
                                            AppModel.ApplicationError(e, "Failed to show permission warning");
                                            onPermissionGranted.run();
                                        }
                                    }
                                });
                            }
                        } catch (Exception ex) {
                            AppModel.ApplicationError(ex, "Permission check failed");
                            // Continue anyway
                            onPermissionGranted.run();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    /**
     * Show dialog prompting user to open app settings to enable permissions
     */
    private static void showPermissionSettingsDialog(final Activity activity, final Runnable onContinue) {
        new android.app.AlertDialog.Builder(activity)
            .setTitle(R.string.permission_settings_title)
            .setMessage(R.string.permission_settings_message)
            .setPositiveButton(R.string.permission_settings_open, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    openAppSettings(activity);
                }
            })
            .setNegativeButton(R.string.permission_settings_continue, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    // Continue with limited functionality
                    MessageCtrl.Toast(activity.getString(R.string.error_permissions_limited));
                    onContinue.run();
                }
            })
            .setCancelable(false)
            .show();
    }

    /**
     * Open the app's settings page where user can manually enable permissions
     */
    public static void openAppSettings(Activity activity) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        android.net.Uri uri = android.net.Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }

    // EU Trackify Brand-Aligned Loading Messages
    public static void SetProcessing(boolean show) {
        App.Object.modalCtrl.SetText("Updating your deliveries...");
        App.Object.modalCtrl.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public static void SetUploading(boolean show) {
        App.Object.modalCtrl.SetText("Syncing shipment data...");
        App.Object.modalCtrl.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public static void SetDownloading(boolean show) {
        App.Object.modalCtrl.SetText("Loading latest updates...");
        App.Object.modalCtrl.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public static void SetDownloadProgress(int progress) {
        App.Object.modalCtrl.SetText("Downloading update... " + progress + "%");
        App.Object.modalCtrl.setVisibility(View.VISIBLE);
    }

    public static void SetLoading(boolean show) {
        App.Object.modalCtrl.SetText("Preparing your dashboard...");
        App.Object.modalCtrl.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    View Current;
    Stack<View> stack = new Stack<View>();

    public void ShowCtrl(UserType ctrlType) {
        if (ctrlType == UserType.None) {
            loginCtrl.Initialize();
            userDistributorTabCtrl.setVisibility(View.GONE);
            userDistributorShipmentDetailTabCtrl.setVisibility(View.GONE);
            loginCtrl.setVisibility(View.VISIBLE);
            iv_Settings.setVisibility(View.GONE);
            iv_Refresh.setVisibility(View.GONE);
            if (ll_Topbar != null) ll_Topbar.setVisibility(View.GONE);
            if (offlineIndicator != null) offlineIndicator.setVisibility(View.GONE);
        } else {
            // Add null checks to prevent crashes during phone call interruptions
            if (userDistributorMyShipmentsFragment != null) {
                userDistributorMyShipmentsFragment.SetScannedCode(null);
            }
            if (userWarehouseManagerCtrl != null) {
                userWarehouseManagerCtrl.SetScannedCode(null);
            }
            if (userWarehouseAdminCtrl != null) {
                userWarehouseAdminCtrl.SetScannedCode(null);
            }
            if (userPackerCtrl != null) {
                userPackerCtrl.SetScannedCode(null);
            }
            loginCtrl.setVisibility(View.GONE);
            if (ll_Topbar != null) ll_Topbar.setVisibility(View.VISIBLE);
            iv_Settings.setVisibility(View.VISIBLE);
            iv_Refresh.setVisibility(View.VISIBLE);
            //iv_Route.setVisibility(View.VISIBLE);

            // SMS reply monitoring removed - can be added later if needed

            if (ctrlType == UserType.Distributor)
                userDistributorTabCtrl.setVisibility(View.VISIBLE);
            else if (ctrlType == UserType.Driver) {
                userDistributorDriverCtrl.setVisibility(View.VISIBLE);
                iv_Refresh.setVisibility(View.GONE);
            }
            // else if (ctrlType == UserType.WarehouseManager)
            // userWarehouseManagerCtrl.setVisibility(View.VISIBLE);
            // else if (ctrlType == UserType.WarehouseAdmin)
            // userWarehouseAdminCtrl.setVisibility(View.VISIBLE);
            // else if (ctrlType == UserType.Packer)
            // userPackerCtrl.setVisibility(View.VISIBLE);
            else {
                loginCtrl.setVisibility(View.VISIBLE);
                iv_Settings.setVisibility(View.GONE);
                iv_Refresh.setVisibility(View.GONE);
                iv_Route.setVisibility(View.GONE);
            }
        }
    }

    public static void HideKeyboard() {
        View view = App.Object.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) App.Object.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onBackPressed() {
        if (signatureCtrl.getVisibility() == View.VISIBLE)
            signatureCtrl.Hide();
        else if (routingCtrl.getVisibility() == View.VISIBLE)
            iv_Route.performClick();
        else if (viewReturnShipmentDetail.getVisibility() == View.VISIBLE)
            viewReturnShipmentDetail.setVisibility(View.GONE);
        else if (statusCheckCtrl != null && statusCheckCtrl.getVisibility() == View.VISIBLE)
            statusCheckCtrl.hide();
        else if (returnReceivedCtrl != null && returnReceivedCtrl.getVisibility() == View.VISIBLE)
            returnReceivedCtrl.hide();
        else if (changePasswordDialog != null && changePasswordDialog.getVisibility() == View.VISIBLE)
            changePasswordDialog.SetVisibility(false);
        else if (modalCtrl.getVisibility() != View.VISIBLE && sendProblemCommentsCtrl.getVisibility() != View.VISIBLE && sendFileCommentsCtrl.getVisibility() != View.VISIBLE && userDistributorShipmentDetailTabCtrl.getVisibility() != View.VISIBLE && settingsCtrl.getVisibility() != View.VISIBLE)
            super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Forward to settings control for profile picture handling
        if (settingsCtrl != null &&
            (requestCode == SettingsCtrl.REQUEST_CAMERA || requestCode == SettingsCtrl.REQUEST_GALLERY)) {
            settingsCtrl.handleActivityResult(requestCode, resultCode, data);
        }
    }

    public boolean isCameraAvailable() {
        PackageManager pm = App.Object.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    public void ShowScanner() {
        try {
            if (isCameraAvailable()) {
                ScanOptions options = new ScanOptions();
//                options.setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES);
//                options.setPrompt("Scan a barcode");
//                options.setCameraId(0);  // Use a specific camera of the device
//                options.setBeepEnabled(false);
//                options.setBarcodeImageEnabled(true);
                // options.setCaptureActivity(CustomCaptureActivity.class);
                options.addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN);
                options.setOrientationLocked(false);
                barcodeLauncher.launch(options);
            } else {
                MessageCtrl.Toast(getString(R.string.error_camera_not_available));
            }
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "App::ShowScanner");
        }
    }
}
