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

import java.util.List;

import common.Communicator.IServerResponse;
import common.UserDistributorShipmentsFragment.ShipmentsType;

/**
 * Status Check Controller - Looks up shipment by courier tracking and opens full detail view
 * with Packed (14) and Packing (12) action buttons.
 */
public class StatusCheckCtrl extends LinearLayout {

    // Static selected shipment for use by detail tabs
    public static ShipmentWithDetail SELECTED;

    // UI Elements
    private ImageView btnBack;
    private TextView tvScannedCode;
    private TextView tvTrackingId;
    private TextView tvCurrentStatus;
    private TextView tvSenderName;
    private TextView tvSenderPhone;
    private TextView tvSenderAddress;
    private TextView tvReceiverName;
    private TextView tvReceiverPhone;
    private TextView tvReceiverAddress;
    private TextView tvCodAmount;
    private TextView tvInstructions;
    private TextView tvErrorMessage;

    private View cardShipmentInfo;
    private View cardSenderInfo;
    private View cardReceiverInfo;
    private View llCodAmount;
    private View llInstructions;
    private View llLoading;
    private View llError;
    private View llActionButtons;

    private Button btnPickedUp;
    private Button btnReturnedToSender;
    private Button btnCancel;
    private Button btnRescan;

    // Data
    private String scannedCode;
    private List<String> candidateCodes;
    private int currentCandidateIndex;
    private ShipmentWithDetail currentShipment;

    public StatusCheckCtrl(Context context) {
        super(context);
        init(context);
    }

    public StatusCheckCtrl(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public StatusCheckCtrl(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.ctrl_status_check, this, true);

        // Initialize UI elements
        btnBack = findViewById(R.id.btnBack);
        tvScannedCode = findViewById(R.id.tvScannedCode);
        tvTrackingId = findViewById(R.id.tvTrackingId);
        tvCurrentStatus = findViewById(R.id.tvCurrentStatus);
        tvSenderName = findViewById(R.id.tvSenderName);
        tvSenderPhone = findViewById(R.id.tvSenderPhone);
        tvSenderAddress = findViewById(R.id.tvSenderAddress);
        tvReceiverName = findViewById(R.id.tvReceiverName);
        tvReceiverPhone = findViewById(R.id.tvReceiverPhone);
        tvReceiverAddress = findViewById(R.id.tvReceiverAddress);
        tvCodAmount = findViewById(R.id.tvCodAmount);
        tvInstructions = findViewById(R.id.tvInstructions);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        cardShipmentInfo = findViewById(R.id.cardShipmentInfo);
        cardSenderInfo = findViewById(R.id.cardSenderInfo);
        cardReceiverInfo = findViewById(R.id.cardReceiverInfo);
        llCodAmount = findViewById(R.id.llCodAmount);
        llInstructions = findViewById(R.id.llInstructions);
        llLoading = findViewById(R.id.llLoading);
        llError = findViewById(R.id.llError);
        llActionButtons = findViewById(R.id.llActionButtons);

        btnPickedUp = findViewById(R.id.btnPickedUp);
        btnReturnedToSender = findViewById(R.id.btnReturnedToSender);
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
                // Trigger a new scan for status check
                if (App.Object.userDistributorMyShipmentsFragment != null) {
                    App.Object.userDistributorMyShipmentsFragment.triggerStatusCheckScan();
                }
            }
        });

        btnPickedUp.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                updateStatus(4, "prezemena"); // Picked Up
            }
        });

        btnReturnedToSender.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                updateStatus(7, "prezemena"); // Returned to Sender
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
                        AppModel.ApplicationError(ex, "StatusCheckCtrl::tvReceiverPhone");
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
                        AppModel.ApplicationError(ex, "StatusCheckCtrl::tvSenderPhone");
                    }
                }
            }
        });
    }

    /**
     * Show the status check screen and look up the scanned code.
     * Accepts raw barcode data - will parse GS1/ANSI barcodes to extract
     * tracking number candidates and try them sequentially.
     */
    public void show(String rawBarcode) {
        this.scannedCode = rawBarcode;
        this.currentShipment = null;

        // Extract candidates from barcode (handles GS1/ANSI, URLs, plain codes)
        this.candidateCodes = BarcodeParser.extractCandidates(rawBarcode);
        this.currentCandidateIndex = 0;

        // Show the best candidate in the UI
        String displayCode = candidateCodes.isEmpty() ? rawBarcode : candidateCodes.get(0);
        tvScannedCode.setText(displayCode);
        showLoading();

        setVisibility(View.VISIBLE);

        // Try the first candidate
        AppModel.ApplicationError(null, "STATUS_CHECK: " + candidateCodes.size() + " candidates from barcode");
        tryNextCandidate();
    }

    /**
     * Hide the status check screen
     */
    public void hide() {
        setVisibility(View.GONE);
        this.scannedCode = null;
        this.candidateCodes = null;
        this.currentShipment = null;
    }

    /**
     * Try the next candidate tracking number against the backend.
     * If it fails, automatically tries the next candidate.
     */
    private void tryNextCandidate() {
        if (candidateCodes == null || currentCandidateIndex >= candidateCodes.size()) {
            // All candidates exhausted
            showError(getContext().getString(R.string.status_check_not_found));
            return;
        }

        String candidate = candidateCodes.get(currentCandidateIndex);
        AppModel.ApplicationError(null, "STATUS_CHECK: Trying candidate " +
            (currentCandidateIndex + 1) + "/" + candidateCodes.size() + ": " + candidate);
        tvScannedCode.setText(candidate);

        lookupShipment(candidate);
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
                            currentShipment.GenerateNotes();
                            currentShipment.GeneratePictures();

                            // Set static SELECTED and open full detail view
                            SELECTED = currentShipment;
                            hide();
                            openFullDetailView();
                        } else {
                            // Try next candidate if available
                            currentCandidateIndex++;
                            if (candidateCodes != null && currentCandidateIndex < candidateCodes.size()) {
                                tryNextCandidate();
                            } else {
                                String errorMsg = messageToShow != null ? messageToShow :
                                    getContext().getString(R.string.status_check_not_found);
                                showError(errorMsg);
                            }
                        }
                    }
                });
            }
        });
    }

    /**
     * Opens the full tabbed detail view (Details, Notes, Photos, SMS) for the looked-up shipment.
     */
    private void openFullDetailView() {
        if (App.Object.userDistributorShipmentDetailTabCtrl.Show(ShipmentsType.StatusCheck)) {
            App.Object.userDistributorShipmentDetail.Initialize();
            App.Object.userDistributorNotesFragment.Initialize();
            App.Object.userDistributorPicturesFragment.Initialize();
            App.Object.userDistributorShipmentDetailTabCtrl.Initialize();
        }
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
        tvSenderAddress.setText(AppModel.IsNullOrEmpty(currentShipment.sender_address) ? "-" : currentShipment.sender_address);

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

        // COD amount
        if (!AppModel.IsNullOrEmpty(currentShipment.receiver_cod) && !currentShipment.receiver_cod.equals("0") && !currentShipment.receiver_cod.equals("0.00")) {
            llCodAmount.setVisibility(View.VISIBLE);
            tvCodAmount.setText(currentShipment.receiver_cod + " \u20AC");
        } else {
            llCodAmount.setVisibility(View.GONE);
        }

        // Instructions
        if (!AppModel.IsNullOrEmpty(currentShipment.instructions)) {
            llInstructions.setVisibility(View.VISIBLE);
            tvInstructions.setText(currentShipment.instructions);
        } else {
            llInstructions.setVisibility(View.GONE);
        }
    }

    private void updateStatus(final int statusId, String status) {
        if (currentShipment == null) {
            MessageCtrl.Toast(getContext().getString(R.string.status_check_error));
            return;
        }

        App.SetProcessing(true);

        Communicator.SendDistributorRequest(currentShipment.tracking_id, status, statusId, null, new IServerResponse() {
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
                                    // Show success message based on status
                                    if (statusId == 4) {
                                        MessageCtrl.Toast(getContext().getString(R.string.status_check_success_picked_up));
                                    } else if (statusId == 7) {
                                        MessageCtrl.Toast(getContext().getString(R.string.status_check_success_returned));
                                    }
                                }
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

                            // Close the status check screen
                            hide();

                        } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                            MessageCtrl.Toast(messageToShow);
                        } else {
                            MessageCtrl.Toast(getContext().getString(R.string.status_check_error));
                        }
                    }
                });
            }
        });
    }

    /**
     * Check if the status check screen is currently visible
     */
    public boolean isVisible() {
        return getVisibility() == View.VISIBLE;
    }
}
