package common;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import eu.trackify.net.R;

import common.Communicator.IServerResponse;

/**
 * Return Received Controller - Displays shipment details after scanning courier tracking
 * Allows marking a shipment as "Return Received" (status 10)
 * Scans courier_tracking code instead of internal tracking ID
 */
public class ReturnReceivedCtrl extends LinearLayout {

    // Status ID for Return Received
    private static final int STATUS_RETURN_RECEIVED = 10;

    // UI Elements
    private ImageView btnBack;
    private TextView tvScannedCode;
    private TextView tvTrackingId;
    private TextView tvCurrentStatus;
    private TextView tvSenderName;
    private TextView tvSenderPhone;
    private TextView tvReceiverName;
    private TextView tvReceiverPhone;
    private TextView tvReceiverAddress;
    private TextView tvErrorMessage;

    private View cardShipmentInfo;
    private View cardSenderInfo;
    private View cardReceiverInfo;
    private View llLoading;
    private View llError;
    private View llActionButtons;

    private Button btnConfirmReturn;
    private Button btnCancel;
    private Button btnRescan;

    // Data
    private String scannedCode;
    private ShipmentWithDetail currentShipment;

    public ReturnReceivedCtrl(Context context) {
        super(context);
        init(context);
    }

    public ReturnReceivedCtrl(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ReturnReceivedCtrl(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.ctrl_return_received, this, true);

        // Initialize UI elements
        btnBack = findViewById(R.id.btnBack);
        tvScannedCode = findViewById(R.id.tvScannedCode);
        tvTrackingId = findViewById(R.id.tvTrackingId);
        tvCurrentStatus = findViewById(R.id.tvCurrentStatus);
        tvSenderName = findViewById(R.id.tvSenderName);
        tvSenderPhone = findViewById(R.id.tvSenderPhone);
        tvReceiverName = findViewById(R.id.tvReceiverName);
        tvReceiverPhone = findViewById(R.id.tvReceiverPhone);
        tvReceiverAddress = findViewById(R.id.tvReceiverAddress);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        cardShipmentInfo = findViewById(R.id.cardShipmentInfo);
        cardSenderInfo = findViewById(R.id.cardSenderInfo);
        cardReceiverInfo = findViewById(R.id.cardReceiverInfo);
        llLoading = findViewById(R.id.llLoading);
        llError = findViewById(R.id.llError);
        llActionButtons = findViewById(R.id.llActionButtons);

        btnConfirmReturn = findViewById(R.id.btnConfirmReturn);
        btnCancel = findViewById(R.id.btnCancel);
        btnRescan = findViewById(R.id.btnRescan);

        setupClickListeners();
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });

        btnCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });

        btnRescan.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
                // Trigger a new scan for return received
                if (App.Object.userDistributorMyShipmentsFragment != null) {
                    App.Object.userDistributorMyShipmentsFragment.triggerReturnReceivedScan();
                }
            }
        });

        btnConfirmReturn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmReturnReceived();
            }
        });

        // Make receiver phone clickable for calling
        tvReceiverPhone.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String phone = tvReceiverPhone.getText().toString();
                if (!AppModel.IsNullOrEmpty(phone) && !phone.equals("-")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone));
                        getContext().startActivity(intent);
                    } catch (Exception ex) {
                        MessageCtrl.Toast(getContext().getString(R.string.error_invalid_phone));
                        AppModel.ApplicationError(ex, "ReturnReceivedCtrl::tvReceiverPhone");
                    }
                }
            }
        });

        // Make sender phone clickable for calling
        tvSenderPhone.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String phone = tvSenderPhone.getText().toString();
                if (!AppModel.IsNullOrEmpty(phone) && !phone.equals("-")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone));
                        getContext().startActivity(intent);
                    } catch (Exception ex) {
                        MessageCtrl.Toast(getContext().getString(R.string.error_invalid_phone));
                        AppModel.ApplicationError(ex, "ReturnReceivedCtrl::tvSenderPhone");
                    }
                }
            }
        });
    }

    /**
     * Show the return received screen and look up the scanned code
     */
    public void show(String courierTracking) {
        this.scannedCode = courierTracking;
        this.currentShipment = null;

        // Reset UI state
        tvScannedCode.setText(courierTracking);
        showLoading();

        setVisibility(View.VISIBLE);

        // Look up the shipment by courier tracking
        lookupShipment(courierTracking);
    }

    /**
     * Hide the return received screen
     */
    public void hide() {
        setVisibility(View.GONE);
        this.scannedCode = null;
        this.currentShipment = null;
    }

    private void showLoading() {
        llLoading.setVisibility(View.VISIBLE);
        llError.setVisibility(View.GONE);
        cardShipmentInfo.setVisibility(View.GONE);
        cardSenderInfo.setVisibility(View.GONE);
        cardReceiverInfo.setVisibility(View.GONE);
        llActionButtons.setVisibility(View.GONE);
    }

    private void showError(String message) {
        llLoading.setVisibility(View.GONE);
        llError.setVisibility(View.VISIBLE);
        tvErrorMessage.setText(message);
        cardShipmentInfo.setVisibility(View.GONE);
        cardSenderInfo.setVisibility(View.GONE);
        cardReceiverInfo.setVisibility(View.GONE);
        llActionButtons.setVisibility(View.GONE);
    }

    private void showShipmentDetails() {
        llLoading.setVisibility(View.GONE);
        llError.setVisibility(View.GONE);
        cardShipmentInfo.setVisibility(View.VISIBLE);
        cardSenderInfo.setVisibility(View.VISIBLE);
        cardReceiverInfo.setVisibility(View.VISIBLE);
        llActionButtons.setVisibility(View.VISIBLE);
    }

    private void lookupShipment(String courierTracking) {
        Communicator.LookupShipmentByCourierTracking(courierTracking, new IServerResponse() {
            @Override
            public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                App.Object.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (success && objs != null && objs.length > 0) {
                            currentShipment = (ShipmentWithDetail) objs[0];
                            populateShipmentDetails();
                            showShipmentDetails();
                        } else {
                            String errorMsg = messageToShow != null ? messageToShow :
                                getContext().getString(R.string.return_received_not_found);
                            showError(errorMsg);
                        }
                    }
                });
            }
        });
    }

    private void populateShipmentDetails() {
        if (currentShipment == null) return;

        // Tracking ID
        tvTrackingId.setText(AppModel.IsNullOrEmpty(currentShipment.tracking_id) ? "-" : currentShipment.tracking_id);

        // Status with color
        tvCurrentStatus.setText(AppModel.IsNullOrEmpty(currentShipment.status_name) ? "-" : currentShipment.status_name);
        try {
            tvCurrentStatus.setBackgroundColor(Color.parseColor(currentShipment.getBgColor()));
            tvCurrentStatus.setTextColor(Color.parseColor(currentShipment.getTxtColor()));
        } catch (Exception e) {
            // Use default colors
        }

        // Sender info
        tvSenderName.setText(AppModel.IsNullOrEmpty(currentShipment.sender_name) ? "-" : currentShipment.sender_name);
        tvSenderPhone.setText(AppModel.IsNullOrEmpty(currentShipment.sender_phone) ? "-" : currentShipment.sender_phone);

        // Receiver info
        tvReceiverName.setText(AppModel.IsNullOrEmpty(currentShipment.receiver_name) ? "-" : currentShipment.receiver_name);
        tvReceiverPhone.setText(AppModel.IsNullOrEmpty(currentShipment.receiver_phone) ? "-" : currentShipment.receiver_phone);

        // Full address with city
        String fullAddress = currentShipment.receiver_address;
        if (!AppModel.IsNullOrEmpty(currentShipment.receiver_city)) {
            if (!AppModel.IsNullOrEmpty(fullAddress)) {
                fullAddress = fullAddress + ", " + currentShipment.receiver_city;
            } else {
                fullAddress = currentShipment.receiver_city;
            }
        }
        tvReceiverAddress.setText(AppModel.IsNullOrEmpty(fullAddress) ? "-" : fullAddress);
    }

    private void confirmReturnReceived() {
        if (currentShipment == null) {
            MessageCtrl.Toast(getContext().getString(R.string.return_received_error));
            return;
        }

        App.SetProcessing(true);

        // Use the new endpoint for return received
        Communicator.MarkReturnReceived(currentShipment.tracking_id, scannedCode, new IServerResponse() {
            @Override
            public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                App.Object.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        App.SetProcessing(false);

                        if (success) {
                            if (objs != null && objs.length > 0) {
                                KeyRef u = (KeyRef) objs[0];
                                if (!AppModel.IsNullOrEmpty(u.response_txt)) {
                                    MessageCtrl.Toast(u.response_txt);
                                } else {
                                    MessageCtrl.Toast(getContext().getString(R.string.return_received_success));
                                }
                            } else {
                                MessageCtrl.Toast(getContext().getString(R.string.return_received_success));
                            }

                            // Refresh all shipment lists
                            if (App.Object.userDistributorMyShipmentsFragment != null) {
                                App.Object.userDistributorMyShipmentsFragment.Load();
                            }
                            if (App.Object.userDistributorReconcileShipmentsFragment != null) {
                                App.Object.userDistributorReconcileShipmentsFragment.Load();
                            }
                            if (App.Object.userDistributorReturnShipmentsFragment != null) {
                                App.Object.userDistributorReturnShipmentsFragment.Load();
                            }

                            // Close the return received screen
                            hide();

                        } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                            MessageCtrl.Toast(messageToShow);
                        } else {
                            MessageCtrl.Toast(getContext().getString(R.string.return_received_error));
                        }
                    }
                });
            }
        });
    }

    /**
     * Check if the return received screen is currently visible
     */
    public boolean isVisible() {
        return getVisibility() == View.VISIBLE;
    }
}
