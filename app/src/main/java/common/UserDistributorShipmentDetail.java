package common;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import eu.trackify.net.R;

import java.io.File;

import common.Communicator.IServerResponse;
import common.CountingInputStreamEntity.IUploadListener;
import common.SignatureCtrl.ISignature;
import common.UserDistributorShipmentsFragment.ShipmentsType;

public class UserDistributorShipmentDetail extends Fragment {

    public View btnInDelivery, btnDelivered, btnRejected, btnPrint, btnCreateReturn;
    TextView sid, tid, status, sendName, sendPh, sendAddr, recName, recPh, recAddr, recCod, recInstructions, tvBtnCreateReturn;
    View ivCall, ivSms, ivCallSender, instructionsContainer;

    ShipmentWithDetail Current;

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.ctrl_distributor_shipmentdetail, null);

        sid = (TextView) v.findViewById(R.id.sid);
        tid = (TextView) v.findViewById(R.id.tid);
        status = (TextView) v.findViewById(R.id.status);
        sendName = (TextView) v.findViewById(R.id.sendName);
        sendPh = (TextView) v.findViewById(R.id.sendPh);
        sendAddr = (TextView) v.findViewById(R.id.sendAddr);
        recName = (TextView) v.findViewById(R.id.recName);
        recPh = (TextView) v.findViewById(R.id.recPh);
        recAddr = (TextView) v.findViewById(R.id.recAddr);
        recCod = (TextView) v.findViewById(R.id.recCod);
        recInstructions = (TextView) v.findViewById(R.id.recInstructions);
        instructionsContainer = v.findViewById(R.id.instructionsContainer);
        ivCallSender = v.findViewById(R.id.ivCallSender);

        // Sender phone call functionality
        ivCallSender.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String phone = sendPh.getText().toString();
                if (AppModel.IsNullOrEmpty(phone) || phone.equals("-")) {
                    MessageCtrl.Toast(getContext().getString(R.string.error_sender_phone_not_found));
                    return;
                }

                try {
                    Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone));
                    startActivity(intent);
                } catch (Exception ex) {
                    MessageCtrl.Toast(getContext().getString(R.string.error_invalid_phone));
                    AppModel.ApplicationError(ex, "UserDistributorShipmentDetail::ivCallSender");
                }
            }
        });

        recPh.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String phone = recPh.getText().toString();
                if (AppModel.IsNullOrEmpty(phone)) {
                    MessageCtrl.Toast(getContext().getString(R.string.error_phone_not_found));
                    return;
                }

                try {
                    Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone));
                    startActivity(intent);
                } catch (Exception ex) {
                    MessageCtrl.Toast(getContext().getString(R.string.error_invalid_phone));
                    AppModel.ApplicationError(ex, "ScanCtrl::recPh");
                }
            }
        });
        ivCall = v.findViewById(R.id.ivCall);
        ivCall.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                recPh.performClick();
            }
        });
        ivSms = v.findViewById(R.id.ivSms);
        ivSms.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
                    SmsManager smsManager = SmsManager.getDefault();
                    smsManager.sendTextMessage(Current.receiver_phone, null, Current.sms_text, null, null);
                    Communicator.SendSmsStatus(Current.shipment_id);
                    MessageCtrl.Toast(getContext().getString(R.string.sms_message_sent));
                } catch (Exception ex) {
                    MessageCtrl.Toast(ex.getMessage().toString());
                    AppModel.ApplicationError(ex, "ivSms::OnClickListener");
                }
            }
        });

        btnInDelivery = v.findViewById(R.id.btnInDelivery);
        btnInDelivery.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                App.SetProcessing(true);
                Communicator.SendDistributorRequest(Current.tracking_id, "odbiena", 20, null, new IServerResponse() {
                    @Override
                    public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                        App.Object.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (success) {
                                        if (objs != null) {
                                            KeyRef u = (KeyRef) objs[0];
                                            MessageCtrl.Toast(u.response_txt);
                                            App.Object.userDistributorReturnShipmentsFragment.Load();
                                        } else
                                            MessageCtrl.Toast(getContext().getString(R.string.error_scan_failed));
                                    } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                                        MessageCtrl.Toast(messageToShow);

                                        Current.status_id = 20; // For offline mode
                                        Current.status_name = "odbiena"; // For offline mode
                                        Current.hasPendingSync = true;
                                        App.Object.userDistributorReturnShipmentsFragment.ApplyFilter();
                                    }
                                } catch (Exception ex) {
                                    AppModel.ApplicationError(ex, "ScanCtrl::SubmitStatus");
                                } finally {
                                    App.SetLoading(false);
                                }
                            }
                        });
                    }
                });
            }
        });

        btnDelivered = v.findViewById(R.id.btnDelivered);
        btnDelivered.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check if shipment is already delivered (status_id 2)
                if (Current != null && Current.status_id == 2) {
                    MessageCtrl.Toast(getContext().getString(R.string.error_already_delivered));
                    return;
                }

                App.Object.signatureCtrl.Show(Current, new ISignature() {
                    @Override
                    public void onSignatureSubmit(Bitmap bitmap, String fullName, String nullablePin) {

                        int statusId = App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.Returns ? 21 : 2;

                        App.SetProcessing(true);
                        Communicator.SubmitSignature(AppModel.ConvertBitmapToBase64String(bitmap), fullName, Current.shipment_id, nullablePin, statusId, new IServerResponse() {
                            @Override
                            public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                                App.Object.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            if (success) {
                                                if (objs != null) {
                                                    KeyRef u = (KeyRef) objs[0];
                                                    MessageCtrl.Toast(u.response_txt);

                                                    // Update local status immediately to prevent duplicate deliveries
                                                    if (Current != null) {
                                                        Current.status_id = statusId; // statusId is either 2 (delivered) or 21 (delivered returns)
                                                    }

                                                    // Clear cache to force fresh data on next load
                                                    AppModel.Object.SaveVariable(AppModel.MY_SHIPMENTS_CACHE_KEY, "");
                                                    AppModel.Object.SaveVariable(AppModel.RECONCILE_SHIPMENTS_CACHE_KEY, "");

                                                    App.Object.userDistributorMyShipmentsFragment.Load();
                                                    App.Object.userDistributorReconcileShipmentsFragment.Load();
                                                    App.Object.userDistributorReturnShipmentsFragment.Load();
                                                    App.Object.userDistributorShipmentDetailTabCtrl.Hide();
                                                } else {
                                                    MessageCtrl.Toast(getContext().getString(R.string.error_signature_failed));
                                                    // App.SetLoading(false);
                                                }
                                            } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                                                MessageCtrl.Toast(messageToShow);

                                                if (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.MyShipments) {
                                                    App.Object.userDistributorMyShipmentsFragment.ITEMS.remove(Current); // For offline mode
                                                    App.Object.userDistributorMyShipmentsFragment.ApplyFilter();
                                                } else if (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.Returns) {
                                                    App.Object.userDistributorReturnShipmentsFragment.ITEMS.remove(Current); // For offline mode
                                                    App.Object.userDistributorReturnShipmentsFragment.ApplyFilter();
                                                } else {
                                                    App.Object.userDistributorReconcileShipmentsFragment.ITEMS.remove(Current); // For offline mode
                                                    App.Object.userDistributorReconcileShipmentsFragment.ApplyFilter();
                                                }
                                                App.Object.userDistributorShipmentDetailTabCtrl.Hide();
                                            }
                                        } catch (Exception ex) {
                                            AppModel.ApplicationError(ex, "ScanCtrl::SubmitStatus");
                                            // App.SetLoading(false);
                                        } finally {
                                            App.SetProcessing(false);
                                        }
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });

        btnRejected = v.findViewById(R.id.btnRejected);
        btnRejected.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showProblematicDialog();
            }
        });

        btnPrint = v.findViewById(R.id.btnPrint);
        // Hide print button if feature is disabled
        if (!SettingsCtrl.isPrinterEnabled(getContext())) {
            btnPrint.setVisibility(View.GONE);
        } else {
            btnPrint.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (PrinterManager.Instance != null) {
                        PrinterManager.Instance.printShipment(Current);
                    } else if (Printer.Instance != null) {
                        Printer.Instance.printShipment(Current);
                    }
                }
            });
        }

        tvBtnCreateReturn = v.findViewById(R.id.tvBtnCreateReturn);
        btnCreateReturn = v.findViewById(R.id.btnCreateReturn);
        btnCreateReturn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Current.returnShipment == null) {
                    App.SetProcessing(true);
                    Communicator.CreateReturnShipment(Current.tracking_id, new IServerResponse() {
                        @Override
                        public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                            App.Object.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (success) {
                                            if (objs != null) {
                                                ReturnShipment u = (ReturnShipment) objs[0];
                                                Current.returnShipment = u;
                                                initializeReturnShipment();
                                                // Only print if feature is enabled
                                                if (AppConfig.isPrinterEnabled()) {
                                                    Printer.Instance.printReturnShipment(Current.returnShipment);
                                                }
                                            } else
                                                MessageCtrl.Toast(getContext().getString(R.string.error_create_return_failed));
                                        } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                                            MessageCtrl.Toast(messageToShow);
                                        }
                                    } catch (Exception ex) {
                                        AppModel.ApplicationError(ex, "ScanCtrl::CreateReturnShipment");
                                    } finally {
                                        App.SetLoading(false);
                                    }
                                }
                            });
                        }
                    });
                } else {
                    App.Object.viewReturnShipmentDetail.Initialize(Current);
                }
            }
        });

        Initialize();

        return v;
    }

    public void SubmitPicture(String comments) {
        App.SetUploading(true);
        App.HideKeyboard();

        Communicator.UploadShipmentPicture(Current.shipment_id, comments, AttachmentManager.AttachFile, new IServerResponse() {
            @Override
            public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                App.Object.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (success && objs != null && objs[0] != null && objs[0].toString().equalsIgnoreCase("true")) {
                                MessageCtrl.Toast(getContext().getString(R.string.pictures_success));
                                // Refresh the pictures tab if it exists
                                if (App.Object.userDistributorPicturesFragment != null) {
                                    App.Object.userDistributorPicturesFragment.Initialize();
                                }
                                // Reload shipment data to get updated picture count
                                Initialize();
                            } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                                MessageCtrl.Toast(messageToShow);
                            }
                        } catch (Exception ex) {
                            AppModel.ApplicationError(ex, "Response::UploadItem");
                        } finally {
                            App.SetLoading(false);
                        }
                    }
                });
            }
        }, new IUploadListener() {
            @Override
            public void OnProgressChanged(int percent) {
                MessageCtrl.ToastShort(percent + "% Uploaded");
            }
        });
    }
    
    public void SubmitPictureWithProgress(String comments, File imageFile, final IUploadListener progressListener, final Runnable onSuccess) {
        App.HideKeyboard();

        Communicator.UploadShipmentPicture(Current.shipment_id, comments, imageFile, new IServerResponse() {
            @Override
            public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                App.Object.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (success && objs != null && objs[0] != null && objs[0].toString().equalsIgnoreCase("true")) {
                                MessageCtrl.Toast(getContext().getString(R.string.pictures_success));
                                // Refresh the pictures tab if it exists
                                if (App.Object.userDistributorPicturesFragment != null) {
                                    App.Object.userDistributorPicturesFragment.Initialize();
                                }
                                // Reload shipment data to get updated picture count
                                Initialize();
                                if (onSuccess != null) {
                                    onSuccess.run();
                                }
                            } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                                MessageCtrl.Toast(messageToShow);
                            }
                        } catch (Exception ex) {
                            AppModel.ApplicationError(ex, "Response::UploadPictureWithProgress");
                        }
                    }
                });
            }
        }, progressListener);
    }

    public void Initialize() {
        Current = App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.MyShipments ? App.Object.userDistributorMyShipmentsFragment.SELECTED : (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.Returns ?  App.Object.userDistributorReturnShipmentsFragment.SELECTED : App.Object.userDistributorReconcileShipmentsFragment.SELECTED);

        if (Current != null && sid != null) {
            sid.setText(Current.shipment_id);
            status.setText(Current.status_name);
            status.setTextColor(Color.parseColor(Current.getTxtColor()));
            status.setBackgroundColor(Color.parseColor(Current.getBgColor()));

            // Set tracking ID with exchange tracking ID using exchange symbol if present
            String displayTrackingId = Current.tracking_id;
            if (!AppModel.IsNullOrEmpty(Current.exchange_tracking_id)) {
                displayTrackingId = displayTrackingId + " ↔ " + Current.exchange_tracking_id;
            }
            tid.setText(displayTrackingId);
            sendName.setText(Current.sender_name);
            
            // Set sender phone and visibility of call icon
            if (!AppModel.IsNullOrEmpty(Current.sender_phone)) {
                sendPh.setText(Current.sender_phone);
                ivCallSender.setVisibility(View.VISIBLE);
            } else {
                sendPh.setText("-");
                ivCallSender.setVisibility(View.GONE);
            }
            
            sendAddr.setText(Current.sender_address);
            recName.setText(Current.receiver_name);
            recPh.setText(Current.receiver_phone);
            
            // Merge receiver address and city with comma separator
            String fullAddress = Current.receiver_address;
            if (!AppModel.IsNullOrEmpty(Current.receiver_city)) {
                if (!AppModel.IsNullOrEmpty(fullAddress)) {
                    fullAddress = fullAddress + ", " + Current.receiver_city;
                } else {
                    fullAddress = Current.receiver_city;
                }
            }
            recAddr.setText(fullAddress);
            
            recCod.setText(Current.receiver_cod);
            
            // Set instructions if available
            if (!AppModel.IsNullOrEmpty(Current.instructions)) {
                recInstructions.setText(Current.instructions);
                instructionsContainer.setVisibility(View.VISIBLE);
            } else {
                instructionsContainer.setVisibility(View.GONE);
            }

            ivCall.setVisibility(AppModel.IsNullOrEmpty(Current.receiver_phone) ? View.GONE : View.VISIBLE);
            ivSms.setVisibility(AppModel.IsNullOrEmpty(Current.receiver_phone) || AppModel.IsNullOrEmpty(Current.sms_text) ? View.GONE : View.VISIBLE);

            btnInDelivery.setVisibility(App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.Returns ? View.VISIBLE : View.GONE);
            btnDelivered.setVisibility(App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.MyShipments || App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.Returns ? View.VISIBLE : View.GONE);
            btnRejected.setVisibility(App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.MyShipments ? View.VISIBLE : View.GONE);
            initializeReturnShipment();
        }
    }

    public void initializeReturnShipment() {
        if (Current.returnShipment == null)
            tvBtnCreateReturn.setText("Create Return Shipment");
        else
            tvBtnCreateReturn.setText("View Return Shipment " + Current.tracking_id);
    }

    private void showProblematicDialog() {
        final Dialog dialog = new Dialog(getContext());
        dialog.setContentView(R.layout.dialog_problematic_reason);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        final RadioButton radio1 = dialog.findViewById(R.id.radio1);
        final RadioButton radio2 = dialog.findViewById(R.id.radio2);
        final RadioButton radio3 = dialog.findViewById(R.id.radio3);
        final RadioButton radio4 = dialog.findViewById(R.id.radio4);
        final RadioButton radio5 = dialog.findViewById(R.id.radio5);
        final RadioButton radio6 = dialog.findViewById(R.id.radio6);
        final EditText et_OtherNote = dialog.findViewById(R.id.et_OtherNote);

        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnSubmit = dialog.findViewById(R.id.btnSubmit);

        btnCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnSubmit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Validate that a reason is selected
                if (!radio1.isChecked() && !radio2.isChecked() && !radio3.isChecked() &&
                    !radio4.isChecked() && !radio5.isChecked() && !radio6.isChecked()) {
                    MessageCtrl.Toast(getContext().getString(R.string.problematic_error_select_reason));
                    return;
                }

                String noteText = "";
                String noteType = "";

                if (radio1.isChecked()) {
                    noteText = "";
                    noteType = "1";
                } else if (radio2.isChecked()) {
                    noteText = "";
                    noteType = "2";
                } else if (radio3.isChecked()) {
                    noteText = "";
                    noteType = "3";
                } else if (radio4.isChecked()) {
                    noteText = "";
                    noteType = "4";
                } else if (radio5.isChecked()) {
                    noteText = "";
                    noteType = "5";
                } else if (radio6.isChecked()) {
                    if (AppModel.IsNullOrEmpty(et_OtherNote.getText().toString())) {
                        MessageCtrl.Toast(getContext().getString(R.string.problematic_error_enter_reason));
                        return;
                    }
                    noteText = et_OtherNote.getText().toString();
                    noteType = "6";
                }

                dialog.dismiss();
                submitProblematicStatus(noteText, noteType);
            }
        });

        dialog.show();
    }

    private void submitProblematicStatus(final String noteText, final String noteType) {
        App.SetProcessing(true);

        // First, update the status to problematic
        Communicator.SendDistributorRequest(Current.tracking_id, "odbiena", 3, null, new IServerResponse() {
            @Override
            public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                if (success && objs != null) {
                    // Status updated successfully, now add the note
                    Communicator.SendShipmentComments(Current.shipment_id, noteType, noteText, new IServerResponse() {
                        @Override
                        public void onCompleted(final boolean noteSuccess, final String noteMessage, final Object... noteObjs) {
                            App.Object.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (noteSuccess) {
                                            MessageCtrl.Toast(getContext().getString(R.string.problematic_success));
                                            // Refresh the shipments list
                                            App.Object.userDistributorMyShipmentsFragment.Load();
                                        } else {
                                            MessageCtrl.Toast(getContext().getString(R.string.problematic_partial_success));
                                            App.Object.userDistributorMyShipmentsFragment.Load();
                                        }
                                    } catch (Exception ex) {
                                        AppModel.ApplicationError(ex, "UserDistributorShipmentDetail::submitProblematicStatus::noteCallback");
                                    } finally {
                                        App.SetProcessing(false);
                                    }
                                }
                            });
                        }
                    });
                } else {
                    App.Object.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (!AppModel.IsNullOrEmpty(messageToShow)) {
                                    MessageCtrl.Toast(messageToShow);
                                    // Offline mode - update locally
                                    Current.status_id = 3;
                                    Current.status_name = "odbiena";
                                    Current.hasPendingSync = true;
                                    App.Object.userDistributorMyShipmentsFragment.ApplyFilter();
                                } else {
                                    MessageCtrl.Toast(getContext().getString(R.string.problematic_error_failed));
                                }
                            } catch (Exception ex) {
                                AppModel.ApplicationError(ex, "UserDistributorShipmentDetail::submitProblematicStatus");
                            } finally {
                                App.SetProcessing(false);
                            }
                        }
                    });
                }
            }
        });
    }
}