package common;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import eu.trackify.net.BuildConfig;
import eu.trackify.net.R;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SettingsCtrl extends LinearLayout {

    public static final int REQUEST_CAMERA = 1001;
    public static final int REQUEST_GALLERY = 1002;

    EditText et_Username;
    Button btnClose, btnUpdate, btnChangePassword, btnChangePhoto;
    ImageView iv_Logout, iv_ProfilePicture, iv_CameraIcon;
    Switch switchPrinter;
    SharedPreferences prefs;
    RelativeLayout profilePictureContainer;

    public SettingsCtrl(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.popup_settings, this);

        if (!this.isInEditMode()) {
            prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            et_Username = (EditText) v.findViewById(R.id.et_Username);
            switchPrinter = (Switch) v.findViewById(R.id.switchPrinter);

            // Initialize profile picture views
            iv_ProfilePicture = (ImageView) v.findViewById(R.id.iv_ProfilePicture);
            iv_CameraIcon = (ImageView) v.findViewById(R.id.iv_CameraIcon);
            btnChangePhoto = (Button) v.findViewById(R.id.btnChangePhoto);

            // Load profile picture if exists
            loadProfilePicture();

            // Set app version dynamically
            TextView tvAppVersion = (TextView) v.findViewById(R.id.tv_AppVersion);
            if (tvAppVersion != null) {
                tvAppVersion.setText("Version " + BuildConfig.VERSION_NAME);
            }

            // Profile picture click handlers
            View.OnClickListener profilePictureClickListener = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showImagePickerDialog();
                }
            };

            if (iv_ProfilePicture != null) {
                iv_ProfilePicture.setOnClickListener(profilePictureClickListener);
            }

            if (iv_CameraIcon != null) {
                iv_CameraIcon.setOnClickListener(profilePictureClickListener);
            }

            if (btnChangePhoto != null) {
                btnChangePhoto.setOnClickListener(profilePictureClickListener);
            }

            // Setup SMS Reply Monitoring
            setupSMSReplyMonitoring();

            // Setup Language Selector
            setupLanguageSelector();

            // Load saved printer preference
            boolean printerEnabled = prefs.getBoolean("printer_enabled", false);
            switchPrinter.setChecked(printerEnabled);
            
            // Handle printer switch changes
            switchPrinter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    // Save preference
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("printer_enabled", isChecked);
                    editor.apply();
                    
                    // Enable/disable Bluetooth when printer is enabled/disabled
                    if (isChecked) {
                        enableBluetooth();
                        MessageCtrl.Toast(getContext().getString(R.string.settings_printer_enabled));
                    } else {
                        MessageCtrl.Toast(getContext().getString(R.string.settings_printer_disabled));
                    }
                    
                    // Update printer icon visibility in main UI
                    if (App.Object != null) {
                        App.Object.updatePrinterVisibility();
                    }
                    
                    // Update print button visibility in shipment detail if it's open
                    if (App.Object.userDistributorShipmentDetail != null && App.Object.userDistributorShipmentDetail.btnPrint != null) {
                        App.Object.userDistributorShipmentDetail.btnPrint.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    }
                }
            });

            btnClose = (Button) v.findViewById(R.id.btnClose);
            btnClose.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        SetVisibility(false);
                    } catch (Exception ex) {
                        AppModel.ApplicationError(ex, "SendCommentsCtrl::btnCancel");
                    }
                }
            });

            iv_Logout = v.findViewById(R.id.iv_Logout);
            iv_Logout.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    SetVisibility(false);
                    App.Object.Logout();
                }
            });

            // Change password button (hidden button for compatibility)
            btnChangePassword = (Button) v.findViewById(R.id.btnChangePassword);
            if (btnChangePassword != null) {
                btnChangePassword.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Hide settings and show change password dialog
                        SetVisibility(false);
                        if (App.Object.changePasswordDialog != null) {
                            App.Object.changePasswordDialog.show();
                        }
                    }
                });
            }

            // Also handle click on the container LinearLayout for the new design
            LinearLayout btnChangePasswordContainer = (LinearLayout) v.findViewById(R.id.btnChangePasswordContainer);
            if (btnChangePasswordContainer != null) {
                btnChangePasswordContainer.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Hide settings and show change password dialog
                        SetVisibility(false);
                        if (App.Object.changePasswordDialog != null) {
                            App.Object.changePasswordDialog.show();
                        }
                    }
                });
            }

            // Update button (hidden button for compatibility)
            btnUpdate = (Button) v.findViewById(R.id.btnUpdate);
            if (btnUpdate != null) {
                btnUpdate.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    App.SetProcessing(true);
                    Communicator.CheckAppUpdate(new Communicator.IServerResponse() {
                        @Override
                        public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                            App.Object.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (success) {
                                            AppUpdate obj = (AppUpdate) objs[0];
                                            if (String.valueOf(BuildConfig.VERSION_CODE).equalsIgnoreCase(obj.latest_version)) {
                                                MessageCtrl.Toast(obj.same_version_msg);
                                                App.SetLoading(false);
                                            } else {
                                                App.SetDownloading(true);
                                                Communicator.DownloadFileUsingStream("App.apk", obj.download_url, new Communicator.IServerResponse() {
                                                    @Override
                                                    public void onCompleted(final boolean success, String messageToShow, final Object... objs) {
                                                        App.Object.runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                try {
                                                                    if (success) {
                                                                        File update = (File) objs[0];
                                                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                                                        intent.setDataAndType(Uri.fromFile(update), "application/vnd.android.package-archive");
                                                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                                        App.Object.startActivity(intent);
                                                                    } else {
                                                                        String errorMsg = messageToShow != null ? messageToShow : "Unable to download update";
                                                                        MessageCtrl.Toast(errorMsg);
                                                                    }
                                                                } catch (Exception ex) {
                                                                    AppModel.ApplicationError(ex, "SettingsCtrl:CheckAppUpdate");
                                                                } finally {
                                                                    App.SetDownloading(false);
                                                                }
                                                            }
                                                        });
                                                    }
                                                });
                                            }
                                        } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                                            MessageCtrl.Toast(messageToShow);
                                            App.SetLoading(false);
                                        }
                                    } catch (Exception ex) {
                                        AppModel.ApplicationError(ex, "SettingsCtrl:CheckAppUpdate");
                                        App.SetLoading(false);
                                    }
                                }
                            });
                        }
                    });
                }
            });
            }

            // Also handle click on the Update container LinearLayout for the new design
            LinearLayout btnUpdateContainer = (LinearLayout) v.findViewById(R.id.btnUpdateContainer);
            if (btnUpdateContainer != null) {
                btnUpdateContainer.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        App.SetProcessing(true);
                        Communicator.CheckAppUpdate(new Communicator.IServerResponse() {
                            @Override
                            public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                                App.Object.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            if (success) {
                                                AppUpdate obj = (AppUpdate) objs[0];
                                                if (String.valueOf(BuildConfig.VERSION_CODE).equalsIgnoreCase(obj.latest_version)) {
                                                    MessageCtrl.Toast(obj.same_version_msg);
                                                    App.SetLoading(false);
                                                } else {
                                                    App.SetDownloading(true);
                                                    Communicator.DownloadFileUsingStream("App.apk", obj.download_url, new Communicator.IServerResponse() {
                                                        @Override
                                                        public void onCompleted(final boolean success, String messageToShow, final Object... objs) {
                                                            App.Object.runOnUiThread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    try {
                                                                        if (success) {
                                                                            File update = (File) objs[0];
                                                                            Intent intent = new Intent(Intent.ACTION_VIEW);

                                                                            // Use FileProvider for Android 7+ compatibility
                                                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                                                                Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                                                                                    App.Object,
                                                                                    App.Object.getPackageName() + ".fileprovider",
                                                                                    update
                                                                                );
                                                                                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                                                                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                                                            } else {
                                                                                intent.setDataAndType(Uri.fromFile(update), "application/vnd.android.package-archive");
                                                                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                                            }

                                                                            App.Object.startActivity(intent);
                                                                        } else {
                                                                            String errorMsg = messageToShow != null ? messageToShow : "Unable to download update";
                                                                            MessageCtrl.Toast(errorMsg);
                                                                        }
                                                                    } catch (Exception ex) {
                                                                        AppModel.ApplicationError(ex, "SettingsCtrl:CheckAppUpdate");
                                                                    } finally {
                                                                        App.SetDownloading(false);
                                                                    }
                                                                }
                                                            });
                                                        }
                                                    });
                                                }
                                            } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                                                MessageCtrl.Toast(messageToShow);
                                                App.SetLoading(false);
                                            }
                                        } catch (Exception ex) {
                                            AppModel.ApplicationError(ex, "SettingsCtrl:CheckAppUpdate");
                                            App.SetLoading(false);
                                        }
                                    }
                                });
                            }
                        });
                    }
                });
            }

            RelativeLayout rl_popupRootView = (RelativeLayout) findViewById(R.id.rl_popupRootView);
            rl_popupRootView.setOnTouchListener(new OnTouchListener() {
                @SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

        }
    }

    public void SetVisibility(boolean makeVisible) {
        et_Username.setText(App.CurrentUser == null ? "" : App.CurrentUser.user);
        // Reload printer preference when showing settings
        if (makeVisible) {
            boolean printerEnabled = prefs.getBoolean("printer_enabled", false);
            switchPrinter.setChecked(printerEnabled);
        }
        this.setVisibility(makeVisible ? View.VISIBLE : View.GONE);
    }
    
    private void enableBluetooth() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
            }
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SettingsCtrl::enableBluetooth");
        }
    }
    
    public static boolean isPrinterEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        return prefs.getBoolean("printer_enabled", false);
    }

    // Profile Picture Methods

    private void showImagePickerDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(getContext().getString(R.string.settings_select_profile_picture));
        String[] options = {getContext().getString(R.string.pictures_take_photo), getContext().getString(R.string.pictures_from_gallery), getContext().getString(R.string.button_cancel)};
        builder.setItems(options, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        openCamera();
                        break;
                    case 1:
                        openGallery();
                        break;
                    case 2:
                        dialog.dismiss();
                        break;
                }
            }
        });
        builder.show();
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getContext().getPackageManager()) != null) {
            ((Activity) getContext()).startActivityForResult(cameraIntent, REQUEST_CAMERA);
        } else {
            MessageCtrl.Toast(getContext().getString(R.string.error_camera_not_available));
        }
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        ((Activity) getContext()).startActivityForResult(galleryIntent, REQUEST_GALLERY);
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            Bitmap bitmap = null;

            if (requestCode == REQUEST_CAMERA) {
                // Handle camera capture
                bitmap = (Bitmap) data.getExtras().get("data");
            } else if (requestCode == REQUEST_GALLERY) {
                // Handle gallery selection
                try {
                    Uri selectedImage = data.getData();
                    bitmap = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), selectedImage);
                } catch (IOException e) {
                    AppModel.ApplicationError(e, "SettingsCtrl::handleActivityResult");
                }
            }

            if (bitmap != null) {
                // Resize and compress image
                bitmap = resizeBitmap(bitmap, 400, 400);

                // Save locally
                saveProfilePictureLocally(bitmap);

                // Upload to server
                uploadProfilePicture(bitmap);

                // Update UI
                updateProfilePictureUI(bitmap);
            }
        }
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = maxWidth;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxHeight;
            width = (int) (height * bitmapRatio);
        }

        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    private void saveProfilePictureLocally(Bitmap bitmap) {
        try {
            // Save to internal storage
            File profilePicFile = new File(getContext().getFilesDir(), "profile_picture.jpg");
            FileOutputStream fos = new FileOutputStream(profilePicFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();

            // Save path in preferences
            prefs.edit().putString("profile_picture_path", profilePicFile.getAbsolutePath()).apply();
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SettingsCtrl::saveProfilePictureLocally");
        }
    }

    private void uploadProfilePicture(final Bitmap bitmap) {
        // Convert bitmap to base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] imageBytes = baos.toByteArray();
        final String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);

        // Show loading
        App.SetProcessing(true);

        // Upload to server
        Communicator.UploadProfilePicture(base64Image, new Communicator.IServerResponse() {
            @Override
            public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                App.Object.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        App.SetLoading(false);
                        if (success) {
                            MessageCtrl.Toast(getContext().getString(R.string.settings_change_photo));

                            // Save the server URL if returned
                            if (objs != null && objs.length > 0) {
                                String serverUrl = (String) objs[0];
                                prefs.edit().putString("profile_picture_url", serverUrl).apply();
                            }
                        } else {
                            MessageCtrl.Toast(messageToShow != null ? messageToShow : "Failed to upload profile picture");
                        }
                    }
                });
            }
        });
    }

    private void updateProfilePictureUI(Bitmap bitmap) {
        if (iv_ProfilePicture != null) {
            iv_ProfilePicture.setImageBitmap(bitmap);
            iv_ProfilePicture.setPadding(0, 0, 0, 0);
            iv_ProfilePicture.setColorFilter(null);
        }
    }

    private void loadProfilePicture() {
        // Try to load from local storage first
        String localPath = prefs.getString("profile_picture_path", null);
        if (localPath != null) {
            File profilePicFile = new File(localPath);
            if (profilePicFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(localPath);
                if (bitmap != null) {
                    updateProfilePictureUI(bitmap);
                    return;
                }
            }
        }

        // Try to load from server URL
        String serverUrl = prefs.getString("profile_picture_url", null);
        if (serverUrl != null && !serverUrl.isEmpty()) {
            try {
                // Create circular transformation
                Transformation circleTransform = new Transformation() {
                    @Override
                    public Bitmap transform(Bitmap source) {
                        int size = Math.min(source.getWidth(), source.getHeight());

                        int x = (source.getWidth() - size) / 2;
                        int y = (source.getHeight() - size) / 2;

                        Bitmap squaredBitmap = Bitmap.createBitmap(source, x, y, size, size);
                        if (squaredBitmap != source) {
                            source.recycle();
                        }

                        Bitmap bitmap = Bitmap.createBitmap(size, size, source.getConfig());

                        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                        android.graphics.Paint paint = new android.graphics.Paint();
                        android.graphics.BitmapShader shader = new android.graphics.BitmapShader(squaredBitmap,
                            android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
                        paint.setShader(shader);
                        paint.setAntiAlias(true);

                        float r = size / 2f;
                        canvas.drawCircle(r, r, r, paint);

                        squaredBitmap.recycle();
                        return bitmap;
                    }

                    @Override
                    public String key() {
                        return "circle";
                    }
                };

                Picasso.get()
                    .load(serverUrl)
                    .transform(circleTransform)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(iv_ProfilePicture);

                if (iv_ProfilePicture != null) {
                    iv_ProfilePicture.setPadding(0, 0, 0, 0);
                    iv_ProfilePicture.setColorFilter(null);
                }
            } catch (Exception e) {
                AppModel.ApplicationError(e, "SettingsCtrl::loadProfilePicture");
            }
        }
    }

    /**
     * Setup SMS Reply Monitoring controls
     */
    private void setupSMSReplyMonitoring() {
        try {
            // Find the UI elements
            Switch switchSmsReplyMonitoring = (Switch) findViewById(R.id.switch_sms_reply_monitoring);
            TextView tvSmsReplyStatus = (TextView) findViewById(R.id.tv_sms_reply_status);
            Button btnTestSmsReply = (Button) findViewById(R.id.btn_test_sms_reply);

            if (switchSmsReplyMonitoring == null || tvSmsReplyStatus == null || btnTestSmsReply == null) {
                return; // Elements not found in layout
            }

            // Get the monitor instance
            final SMSReplyMonitor monitor = SMSReplyMonitor.getInstance(getContext());

            // Set initial state
            boolean isEnabled = monitor.isEnabled();
            switchSmsReplyMonitoring.setChecked(isEnabled);
            updateSmsReplyStatus(tvSmsReplyStatus, isEnabled);

            // Handle switch changes
            switchSmsReplyMonitoring.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    monitor.setEnabled(isChecked);
                    updateSmsReplyStatus(tvSmsReplyStatus, isChecked);

                    if (isChecked) {
                        MessageCtrl.Toast(getContext().getString(R.string.settings_sms_status_enabled));
                        // Check for READ_SMS permission
                        if (!hasReadSmsPermission()) {
                            MessageCtrl.Toast(getContext().getString(R.string.error_sms_permission_required));
                        }
                    } else {
                        MessageCtrl.Toast(getContext().getString(R.string.settings_sms_status_disabled));
                    }
                }
            });

            // Handle test button click
            btnTestSmsReply.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (monitor.isEnabled()) {
                        App.SetLoading(true);
                        MessageCtrl.Toast(getContext().getString(R.string.loading_processing));

                        new Thread(() -> {
                            try {
                                monitor.checkForNewReplies();
                                App.Object.runOnUiThread(() -> {
                                    App.SetLoading(false);
                                    MessageCtrl.Toast(getContext().getString(R.string.loading_default));
                                    updateSmsReplyStatus(tvSmsReplyStatus, monitor.isEnabled());
                                });
                            } catch (Exception e) {
                                App.Object.runOnUiThread(() -> {
                                    App.SetLoading(false);
                                    MessageCtrl.Toast(getContext().getString(R.string.error_loading_failed) + ": " + e.getMessage());
                                });
                            }
                        }).start();
                    } else {
                        MessageCtrl.Toast(getContext().getString(R.string.settings_sms_status_disabled));
                    }
                }
            });

        } catch (Exception e) {
            AppModel.ApplicationError(e, "SettingsCtrl::setupSMSReplyMonitoring");
        }
    }

    /**
     * Update SMS reply status display
     */
    private void updateSmsReplyStatus(TextView statusView, boolean isEnabled) {
        if (statusView != null) {
            String status = SMSReplyMonitor.getInstance(getContext()).getStatus();
            statusView.setText(status);
        }
    }

    /**
     * Check if app has READ_SMS permission
     */
    private boolean hasReadSmsPermission() {
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
            getContext().checkSelfPermission(android.Manifest.permission.READ_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Setup language selector
     */
    private void setupLanguageSelector() {
        try {
            // Find language selection views
            LinearLayout langEnglish = findViewById(R.id.lang_english);
            LinearLayout langMacedonian = findViewById(R.id.lang_macedonian);
            LinearLayout langAlbanian = findViewById(R.id.lang_albanian);

            final ImageView checkEnglish = findViewById(R.id.check_english);
            final ImageView checkMacedonian = findViewById(R.id.check_macedonian);
            final ImageView checkAlbanian = findViewById(R.id.check_albanian);

            if (langEnglish == null || langMacedonian == null || langAlbanian == null) {
                return; // Elements not found
            }

            // Get current language
            final String currentLang = getSavedLanguage();

            // Update UI to show current selection
            updateLanguageCheckmarks(currentLang, checkEnglish, checkMacedonian, checkAlbanian);

            // English click listener
            langEnglish.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    changeLanguage("en", checkEnglish, checkMacedonian, checkAlbanian);
                }
            });

            // Macedonian click listener
            langMacedonian.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    changeLanguage("mk", checkEnglish, checkMacedonian, checkAlbanian);
                }
            });

            // Albanian click listener
            langAlbanian.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    changeLanguage("sq", checkEnglish, checkMacedonian, checkAlbanian);
                }
            });

        } catch (Exception e) {
            AppModel.ApplicationError(e, "SettingsCtrl::setupLanguageSelector");
        }
    }

    /**
     * Update checkmarks to show selected language
     */
    private void updateLanguageCheckmarks(String languageCode, ImageView checkEnglish, ImageView checkMacedonian, ImageView checkAlbanian) {
        if (checkEnglish == null || checkMacedonian == null || checkAlbanian == null) {
            return;
        }

        checkEnglish.setVisibility(languageCode.equals("en") ? View.VISIBLE : View.GONE);
        checkMacedonian.setVisibility(languageCode.equals("mk") ? View.VISIBLE : View.GONE);
        checkAlbanian.setVisibility(languageCode.equals("sq") ? View.VISIBLE : View.GONE);
    }

    /**
     * Change app language
     */
    private void changeLanguage(String languageCode, ImageView checkEnglish, ImageView checkMacedonian, ImageView checkAlbanian) {
        try {
            String currentLang = getSavedLanguage();

            // If same language, do nothing
            if (currentLang.equals(languageCode)) {
                return;
            }

            // Save language preference
            saveLanguage(languageCode);

            // Update checkmarks
            updateLanguageCheckmarks(languageCode, checkEnglish, checkMacedonian, checkAlbanian);

            // Restart app to apply new language
            restartApp();

        } catch (Exception e) {
            AppModel.ApplicationError(e, "SettingsCtrl::changeLanguage");
        }
    }

    /**
     * Restart the app to apply language changes
     */
    private void restartApp() {
        try {
            // Create restart intent
            Intent intent = App.Object.getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(App.Object.getBaseContext().getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }

            // Close current activity and restart
            App.Object.finish();
            App.Object.startActivity(intent);

            // Kill process to ensure clean restart
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);

        } catch (Exception e) {
            AppModel.ApplicationError(e, "SettingsCtrl::restartApp");
        }
    }

    /**
     * Get saved language from preferences
     */
    private String getSavedLanguage() {
        return prefs.getString("app_language", "en");
    }

    /**
     * Save language to preferences
     */
    private void saveLanguage(String languageCode) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("app_language", languageCode);
        editor.commit(); // Use commit() for immediate synchronous save before app restart
    }

    /**
     * Apply saved language to context - called from App.java onCreate
     */
    public static void applyLanguage(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            String languageCode = prefs.getString("app_language", "en");

            java.util.Locale locale = new java.util.Locale(languageCode);
            java.util.Locale.setDefault(locale);

            android.content.res.Resources resources = context.getResources();
            android.content.res.Configuration config = resources.getConfiguration();

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                config.setLocale(locale);
            } else {
                config.locale = locale;
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.createConfigurationContext(config);
            }

            resources.updateConfiguration(config, resources.getDisplayMetrics());
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SettingsCtrl::applyLanguage");
        }
    }
}
