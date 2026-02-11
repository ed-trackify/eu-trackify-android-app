package common;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
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
import java.util.List;

import common.Communicator.IServerResponse;
import common.CountingInputStreamEntity.IUploadListener;
import common.SignatureCtrl.ISignature;
import common.UserDistributorShipmentsFragment.ShipmentsType;

public class UserDistributorShipmentDetail extends Fragment {

    public View btnInDelivery, btnDelivered, btnRejected, btnPrint, btnCreateReturn;
    View llStatusCheckButtons, btnPacked, btnPacking;
    View btnWhatsApp;
    View btnPrevShipment, btnNextShipment, btnCopyTracking;
    View packageInfoContainer, llCodBadge;
    View tvActionsHeader, tvAdditionalHeader;
    TextView sid, tid, status, sendName, sendPh, sendAddr, recName, recPh, recAddr, recCod, recInstructions, tvBtnCreateReturn;
    TextView tvDescription, tvShipmentPosition;
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

        // New UI elements
        btnPrevShipment = v.findViewById(R.id.btnPrevShipment);
        btnNextShipment = v.findViewById(R.id.btnNextShipment);
        btnCopyTracking = v.findViewById(R.id.btnCopyTracking);
        tvShipmentPosition = (TextView) v.findViewById(R.id.tvShipmentPosition);
        packageInfoContainer = v.findViewById(R.id.packageInfoContainer);
        tvDescription = (TextView) v.findViewById(R.id.tvDescription);
        llCodBadge = v.findViewById(R.id.llCodBadge);
        btnWhatsApp = v.findViewById(R.id.btnWhatsApp);
        tvActionsHeader = v.findViewById(R.id.tvActionsHeader);
        tvAdditionalHeader = v.findViewById(R.id.tvAdditionalHeader);

        // Copy tracking ID to clipboard
        OnClickListener copyTrackingListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Current == null || AppModel.IsNullOrEmpty(Current.tracking_id)) return;
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("tracking_id", Current.tracking_id);
                clipboard.setPrimaryClip(clip);
                MessageCtrl.Toast(getContext().getString(R.string.tracking_id_copied));
            }
        };
        tid.setOnClickListener(copyTrackingListener);
        btnCopyTracking.setOnClickListener(copyTrackingListener);

        // Open address in Google Maps
        recAddr.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Current == null) return;
                String uri;
                if (Current.lat != null && Current.lon != null && Current.lat != 0 && Current.lon != 0) {
                    String label = Uri.encode(Current.receiver_name != null ? Current.receiver_name : "");
                    uri = "geo:" + Current.lat + "," + Current.lon + "?q=" + Current.lat + "," + Current.lon + "(" + label + ")";
                } else {
                    String address = recAddr.getText().toString();
                    if (AppModel.IsNullOrEmpty(address) || address.equals("-")) return;
                    uri = "geo:0,0?q=" + Uri.encode(address);
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                    startActivity(intent);
                } catch (Exception ex) {
                    AppModel.ApplicationError(ex, "UserDistributorShipmentDetail::openMaps");
                }
            }
        });

        // Prev/Next shipment navigation
        btnPrevShipment.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateShipment(-1);
            }
        });
        btnNextShipment.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateShipment(1);
            }
        });

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

        // WhatsApp button
        btnWhatsApp.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Current == null || AppModel.IsNullOrEmpty(Current.receiver_phone)) return;
                String phone = Current.receiver_phone.replaceAll("[\\s\\-()]", "");
                if (phone.startsWith("+")) phone = phone.substring(1);
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone));
                    startActivity(intent);
                } catch (Exception ex) {
                    MessageCtrl.Toast(getContext().getString(R.string.whatsapp_not_installed));
                    AppModel.ApplicationError(ex, "UserDistributorShipmentDetail::whatsapp");
                }
            }
        });

        btnInDelivery = v.findViewById(R.id.btnInDelivery);
        btnInDelivery.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showConfirmationDialog(getContext().getString(R.string.confirm_in_delivery), new Runnable() {
                    @Override
                    public void run() {
                        doInDelivery();
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

                showConfirmationDialog(getContext().getString(R.string.confirm_deliver), new Runnable() {
                    @Override
                    public void run() {
                        doDeliver();
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

        // StatusCheck buttons: Packed and Packing
        llStatusCheckButtons = v.findViewById(R.id.llStatusCheckButtons);
        btnPacked = v.findViewById(R.id.btnPacked);
        btnPacking = v.findViewById(R.id.btnPacking);

        btnPacked.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Current == null) return;
                submitStatusCheckAction(14, "prezemena"); // Packed
            }
        });

        btnPacking.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Current == null) return;
                submitStatusCheckAction(12, "prezemena"); // Packing
            }
        });

        Initialize();

        return v;
    }

    // ── Confirmation dialog ──────────────────────────────────────────────

    private void showConfirmationDialog(String message, final Runnable onConfirm) {
        new AlertDialog.Builder(getContext())
            .setMessage(message)
            .setPositiveButton(getContext().getString(R.string.confirm_yes), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    onConfirm.run();
                }
            })
            .setNegativeButton(getContext().getString(R.string.confirm_cancel), null)
            .show();
    }

    // ── In Delivery action (extracted for confirmation) ──────────────────

    private void doInDelivery() {
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

    // ── Deliver action (extracted for confirmation) ──────────────────────

    private void doDeliver() {
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
                                                Current.status_id = statusId;
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
                                        }
                                    } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                                        MessageCtrl.Toast(messageToShow);

                                        if (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.MyShipments) {
                                            App.Object.userDistributorMyShipmentsFragment.ITEMS.remove(Current);
                                            App.Object.userDistributorMyShipmentsFragment.ApplyFilter();
                                        } else if (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.Returns) {
                                            App.Object.userDistributorReturnShipmentsFragment.ITEMS.remove(Current);
                                            App.Object.userDistributorReturnShipmentsFragment.ApplyFilter();
                                        } else {
                                            App.Object.userDistributorReconcileShipmentsFragment.ITEMS.remove(Current);
                                            App.Object.userDistributorReconcileShipmentsFragment.ApplyFilter();
                                        }
                                        App.Object.userDistributorShipmentDetailTabCtrl.Hide();
                                    }
                                } catch (Exception ex) {
                                    AppModel.ApplicationError(ex, "ScanCtrl::SubmitStatus");
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
                                if (App.Object.userDistributorPicturesFragment != null) {
                                    App.Object.userDistributorPicturesFragment.Initialize();
                                }
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
                                if (App.Object.userDistributorPicturesFragment != null) {
                                    App.Object.userDistributorPicturesFragment.Initialize();
                                }
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

    // ── Initialize ───────────────────────────────────────────────────────

    public void Initialize() {
        if (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.StatusCheck) {
            Current = StatusCheckCtrl.SELECTED;
        } else {
            Current = App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.MyShipments ? App.Object.userDistributorMyShipmentsFragment.SELECTED : (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.Returns ?  App.Object.userDistributorReturnShipmentsFragment.SELECTED : App.Object.userDistributorReconcileShipmentsFragment.SELECTED);
        }

        if (Current != null && sid != null) {
            sid.setText(Current.shipment_id);
            status.setText(Current.status_name);
            status.setTextColor(Color.parseColor(Current.getTxtColor()));
            status.setBackgroundColor(Color.parseColor(Current.getBgColor()));

            // Set tracking ID with exchange tracking ID using exchange symbol if present
            String displayTrackingId = Current.tracking_id;
            if (!AppModel.IsNullOrEmpty(Current.exchange_tracking_id)) {
                displayTrackingId = displayTrackingId + " \u21c4 " + Current.exchange_tracking_id;
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

            // ── COD styling (#8) ─────────────────────────────────────────
            recCod.setText(Current.receiver_cod);
            boolean hasCod = !AppModel.IsNullOrEmpty(Current.receiver_cod)
                    && !Current.receiver_cod.equals("0")
                    && !Current.receiver_cod.equals("0.00")
                    && !Current.receiver_cod.equals("0.0");
            if (hasCod) {
                llCodBadge.setVisibility(View.VISIBLE);
                // Check if high COD (>= 50)
                try {
                    double codValue = Double.parseDouble(Current.receiver_cod);
                    if (codValue >= 50) {
                        llCodBadge.setBackgroundColor(Color.parseColor("#DC2626")); // red for high COD
                    } else {
                        llCodBadge.setBackgroundColor(Color.parseColor("#1A243C")); // primary
                    }
                } catch (Exception e) {
                    llCodBadge.setBackgroundColor(Color.parseColor("#1A243C"));
                }
            } else {
                llCodBadge.setVisibility(View.GONE);
            }

            // ── Description / Package info (#2) ─────────────────────────
            if (!AppModel.IsNullOrEmpty(Current.description)) {
                packageInfoContainer.setVisibility(View.VISIBLE);
                String desc = Current.description;
                if (!AppModel.IsNullOrEmpty(Current.description_title) && !Current.description_title.equals(Current.description)) {
                    desc = Current.description_title + "\n" + Current.description;
                }
                tvDescription.setText(desc);
            } else if (!AppModel.IsNullOrEmpty(Current.description_title)) {
                packageInfoContainer.setVisibility(View.VISIBLE);
                tvDescription.setText(Current.description_title);
            } else {
                packageInfoContainer.setVisibility(View.GONE);
            }

            // Set instructions if available
            if (!AppModel.IsNullOrEmpty(Current.instructions)) {
                recInstructions.setText(Current.instructions);
                instructionsContainer.setVisibility(View.VISIBLE);
            } else {
                instructionsContainer.setVisibility(View.GONE);
            }

            ivCall.setVisibility(AppModel.IsNullOrEmpty(Current.receiver_phone) ? View.GONE : View.VISIBLE);
            ivSms.setVisibility(AppModel.IsNullOrEmpty(Current.receiver_phone) || AppModel.IsNullOrEmpty(Current.sms_text) ? View.GONE : View.VISIBLE);
            btnWhatsApp.setVisibility(AppModel.IsNullOrEmpty(Current.receiver_phone) ? View.GONE : View.VISIBLE);

            if (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.StatusCheck) {
                // StatusCheck mode: show Packed/Packing, hide standard buttons
                btnInDelivery.setVisibility(View.GONE);
                btnDelivered.setVisibility(View.GONE);
                btnRejected.setVisibility(View.GONE);
                btnPrint.setVisibility(View.GONE);
                btnCreateReturn.setVisibility(View.GONE);
                tvAdditionalHeader.setVisibility(View.GONE);
                llStatusCheckButtons.setVisibility(View.VISIBLE);
            } else {
                // Normal mode
                llStatusCheckButtons.setVisibility(View.GONE);
                tvAdditionalHeader.setVisibility(View.VISIBLE);
                btnInDelivery.setVisibility(App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.Returns ? View.VISIBLE : View.GONE);
                btnDelivered.setVisibility(App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.MyShipments || App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.Returns ? View.VISIBLE : View.GONE);
                btnRejected.setVisibility(App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.MyShipments ? View.VISIBLE : View.GONE);
            }
            initializeReturnShipment();

            // ── Prev/Next navigation (#10) ───────────────────────────────
            updateNavigationArrows();
        }
    }

    // ── Navigation between shipments (#10) ───────────────────────────────

    private List<ShipmentWithDetail> getShipmentsList() {
        ShipmentsType type = App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType;
        if (type == ShipmentsType.MyShipments) return App.Object.userDistributorMyShipmentsFragment.ITEMS;
        if (type == ShipmentsType.Returns) return App.Object.userDistributorReturnShipmentsFragment.ITEMS;
        if (type == ShipmentsType.ReconcileShipments) return App.Object.userDistributorReconcileShipmentsFragment.ITEMS;
        return null;
    }

    private int findCurrentIndex(List<ShipmentWithDetail> items) {
        if (Current == null || items == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).shipment_id != null && items.get(i).shipment_id.equals(Current.shipment_id)) {
                return i;
            }
        }
        return -1;
    }

    private void updateNavigationArrows() {
        ShipmentsType type = App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType;
        if (type == ShipmentsType.StatusCheck) {
            btnPrevShipment.setVisibility(View.GONE);
            btnNextShipment.setVisibility(View.GONE);
            tvShipmentPosition.setVisibility(View.GONE);
            return;
        }

        List<ShipmentWithDetail> items = getShipmentsList();
        if (items == null || items.isEmpty()) {
            btnPrevShipment.setVisibility(View.INVISIBLE);
            btnNextShipment.setVisibility(View.INVISIBLE);
            tvShipmentPosition.setVisibility(View.GONE);
            return;
        }

        int currentIndex = findCurrentIndex(items);
        btnPrevShipment.setVisibility(View.VISIBLE);
        btnNextShipment.setVisibility(View.VISIBLE);
        btnPrevShipment.setAlpha(currentIndex > 0 ? 1.0f : 0.25f);
        btnNextShipment.setAlpha(currentIndex < items.size() - 1 ? 1.0f : 0.25f);
        btnPrevShipment.setClickable(currentIndex > 0);
        btnNextShipment.setClickable(currentIndex < items.size() - 1);

        // Show position indicator
        if (items.size() > 1) {
            tvShipmentPosition.setVisibility(View.VISIBLE);
            tvShipmentPosition.setText(String.format(getContext().getString(R.string.shipment_position), currentIndex + 1, items.size()));
        } else {
            tvShipmentPosition.setVisibility(View.GONE);
        }
    }

    private void navigateShipment(int direction) {
        List<ShipmentWithDetail> items = getShipmentsList();
        if (items == null || items.isEmpty()) return;

        int currentIndex = findCurrentIndex(items);
        if (currentIndex < 0) return;

        int newIndex = currentIndex + direction;
        if (newIndex < 0 || newIndex >= items.size()) return;

        ShipmentWithDetail newSelection = items.get(newIndex);
        ShipmentsType type = App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType;

        // Update SELECTED on the appropriate fragment
        if (type == ShipmentsType.MyShipments) {
            App.Object.userDistributorMyShipmentsFragment.SELECTED = newSelection;
        } else if (type == ShipmentsType.Returns) {
            App.Object.userDistributorReturnShipmentsFragment.SELECTED = newSelection;
        } else if (type == ShipmentsType.ReconcileShipments) {
            App.Object.userDistributorReconcileShipmentsFragment.SELECTED = newSelection;
        }

        // Re-initialize all tabs
        Initialize();
        App.Object.userDistributorNotesFragment.Initialize();
        App.Object.userDistributorPicturesFragment.Initialize();
        App.Object.userDistributorSMSHistoryFragment.Initialize();
    }

    // ── StatusCheck action ───────────────────────────────────────────────

    private void submitStatusCheckAction(final int statusId, String status) {
        App.SetProcessing(true);
        Communicator.SendDistributorRequest(Current.tracking_id, status, statusId, null, new IServerResponse() {
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
                                }
                            }
                            // Refresh all shipment lists
                            if (App.Object.userDistributorMyShipmentsFragment != null)
                                App.Object.userDistributorMyShipmentsFragment.Load();
                            if (App.Object.userDistributorReconcileShipmentsFragment != null)
                                App.Object.userDistributorReconcileShipmentsFragment.Load();
                            if (App.Object.userDistributorReturnShipmentsFragment != null)
                                App.Object.userDistributorReturnShipmentsFragment.Load();

                            // Close detail view
                            App.Object.userDistributorShipmentDetailTabCtrl.Hide();
                        } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                            MessageCtrl.Toast(messageToShow);
                        }
                    }
                });
            }
        });
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
