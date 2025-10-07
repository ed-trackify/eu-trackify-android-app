package common;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import eu.trackify.net.R;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import common.Communicator.IServerResponse;
import common.ListDataBinder.BindedListType;

public class UserDistributorShipmentsFragment extends Fragment {

    public enum ShipmentsType {
        MyShipments, ReconcileShipments, Returns, Pickup, InDelivery, Problematic
    }

    public ShipmentsType Type;

    public UserDistributorShipmentsFragment(ShipmentsType type) {
        Type = type;
    }

    int scanStatusId;

    ListView lv_results;
    ListDataBinder<ShipmentWithDetail> binder;
    EditText et_Search;
    CheckBox chkMultiscan;
    Button btnScanDelivery, btnScanPick;
    View llDeliveredInfo;
    TextView tvDeliveredCount;

    // Client filter chips
    View clientFilterContainer;
    TextView filterWurth01;
    TextView filterWurthN;
    TextView filterOthers;

    // Active client filters
    List<String> activeClientFilters = new ArrayList<>();

    ShipmentWithDetail SELECTED;
    List<ShipmentWithDetail> ITEMS = new ArrayList<ShipmentWithDetail>();

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.ctrl_distributor_user_shipments, null);

        chkMultiscan = v.findViewById(R.id.chkMultiscan);
        btnScanDelivery = v.findViewById(R.id.btnScanDelivery);
        btnScanPick = v.findViewById(R.id.btnScanPick);
        et_Search = (EditText) v.findViewById(R.id.et_Search);
        lv_results = (ListView) v.findViewById(R.id.lv_results);
        llDeliveredInfo = v.findViewById(R.id.llDeliveredInfo);
        tvDeliveredCount = (TextView) v.findViewById(R.id.tvDeliveredCount);

        // Initialize client filter chips
        clientFilterContainer = v.findViewById(R.id.clientFilterContainer);
        filterWurth01 = v.findViewById(R.id.filterWurth01);
        filterWurthN = v.findViewById(R.id.filterWurthN);
        filterOthers = v.findViewById(R.id.filterOthers);

        binder = new ListDataBinder<ShipmentWithDetail>(Type == ShipmentsType.MyShipments ? BindedListType.MyShipments : BindedListType.ReconcileShipments, lv_results);



        btnScanPick.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                scanStatusId = Type == ShipmentsType.Returns ? 20 : 4;
                App.Object.ShowScanner();
            }
        });

        btnScanDelivery.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                scanStatusId = Type == ShipmentsType.Returns ? 7 : 1;
                App.Object.ShowScanner();
            }
        });

        if (Type == ShipmentsType.ReconcileShipments) {
            chkMultiscan.setVisibility(View.GONE);
            btnScanPick.setVisibility(View.GONE);
            btnScanDelivery.setVisibility(View.GONE);
            // Show client filter chips for delivered screen
            clientFilterContainer.setVisibility(View.VISIBLE);
            setupClientFilterChips();
        } else if (Type == ShipmentsType.Returns) {
            btnScanPick.setText(getString(R.string.scan_return_in_delivery));
            btnScanPick.setBackgroundColor(Color.parseColor("#c44c23"));
            btnScanPick.setTextColor(Color.parseColor("#FFFFFF"));
            btnScanDelivery.setText(getString(R.string.scan_deliver_returned));
            btnScanDelivery.setBackgroundColor(Color.parseColor("#910000"));
            btnScanDelivery.setTextColor(Color.parseColor("#FFFFFF"));
        }

        lv_results.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SELECTED = (ShipmentWithDetail) view.getTag();
                InitializeSelectedItem();
            }
        });

        et_Search.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
                ApplyFilter();
            }

            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {

            }

            @Override
            public void afterTextChanged(Editable arg0) {

            }
        });

        Load();

        return v;
    }

    private void InitializeSelectedItem() {
        if (App.Object.userDistributorShipmentDetailTabCtrl.Show(Type)) {
            App.Object.userDistributorShipmentDetail.Initialize();
            App.Object.userDistributorNotesFragment.Initialize();
            App.Object.userDistributorPicturesFragment.Initialize();
            App.Object.userDistributorShipmentDetailTabCtrl.Initialize();
        }
    }
    
    public void RefreshSelectedShipment() {
        // Reload shipments from server to get updated data including new pictures
        Load();
    }

    public void Load() {
        App.SetLoading(true);

        App.Object.runOnUiThread(new Runnable() {
            @SuppressWarnings("unchecked")
            @Override
            public void run() {
                try {
                    Communicator.LoadShipmentsWithDetails(Type, new IServerResponse() {
                        @Override
                        public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                            App.Object.runOnUiThread(new Runnable() {
                                @SuppressWarnings("unchecked")
                                @Override
                                public void run() {
                                    try {
                                        if (success) {
                                            if (objs != null) {
                                                ITEMS.clear();
                                                ShipmentResponse resp = (ShipmentResponse) objs[0];

                                                if (Type == ShipmentsType.ReconcileShipments) {
                                                    // Count delivered shipments for tab title
                                                    int deliveredCount = 0;
                                                    for (ShipmentWithDetail sd : resp.shipments) {
                                                        if (sd.status_id == 2) {
                                                            deliveredCount++;
                                                        }
                                                    }
                                                    // Update tab title with format: "(X) Delivered\n(Ymkd)" with newline
                                                    int codAmount = (int) resp.cod_to_reconcile;
                                                    String tabTitle = String.format(getContext().getString(R.string.tab_title_delivered_count), deliveredCount, String.valueOf(codAmount));
                                                    App.Object.userDistributorTabCtrl.ChangeTabTitle(tabTitle, App.Object.userDistributorReconcileShipmentsFragment);
                                                    // Show delivered count info bar
                                                    updateDeliveredCount();
                                                } else if (Type == ShipmentsType.Returns) {
                                                    App.Object.userDistributorTabCtrl.ChangeTabTitle("Returns", App.Object.userDistributorReturnShipmentsFragment);
                                                }

                                                AppModel.Object.SaveVariable(Type == ShipmentsType.MyShipments ? AppModel.MY_SHIPMENTS_CACHE_KEY : AppModel.RECONCILE_SHIPMENTS_CACHE_KEY, new Gson().toJson(resp));

                                                if (resp.shipments.size() == 0) {
                                                    MessageCtrl.Toast("No record found");
                                                    ApplyFilter();
                                                } else {
                                                    for (ShipmentWithDetail sd : resp.shipments) {
                                                        sd.GenerateNotes();
                                                        sd.GeneratePictures();
                                                        ITEMS.add(sd);

                                                        if (SELECTED != null && SELECTED.shipment_id.equals(sd.shipment_id))
                                                            SELECTED = sd;
                                                    }
                                                    ApplyFilter();

                                                    if (App.Object.userDistributorShipmentDetailTabCtrl.getVisibility() == View.VISIBLE && SELECTED != null)
                                                        InitializeSelectedItem();
                                                }

                                                if (resp.settings != null) {
                                                    App.Object.ApplySettings(resp.settings);
                                                }

                                                App.SetLoading(false);
                                            } else {
                                                MessageCtrl.Toast("Loading failed, Please try again");
                                                App.SetLoading(false);
                                            }
                                        } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                                            MessageCtrl.Toast(messageToShow);

                                            String sJson = AppModel.Object.GetSettingVariable(Type == ShipmentsType.MyShipments ? AppModel.MY_SHIPMENTS_CACHE_KEY : AppModel.RECONCILE_SHIPMENTS_CACHE_KEY);
                                            ShipmentResponse cached = new Gson().fromJson(sJson, ShipmentResponse.class);
                                            ITEMS.clear();

                                            if (Type == ShipmentsType.ReconcileShipments) {
                                                // Count delivered shipments for tab title
                                                int deliveredCount = 0;
                                                for (ShipmentWithDetail sd : cached.shipments) {
                                                    if (sd.status_id == 2) {
                                                        deliveredCount++;
                                                    }
                                                }
                                                // Update tab title with format: "(X) Delivered\n(Ymkd)" with newline
                                                int codAmount = (int) cached.cod_to_reconcile;
                                                String tabTitle = String.format(getContext().getString(R.string.tab_title_delivered_count), deliveredCount, String.valueOf(codAmount));
                                                App.Object.userDistributorTabCtrl.ChangeTabTitle(tabTitle, App.Object.userDistributorReconcileShipmentsFragment);
                                                // Show delivered count info bar
                                                updateDeliveredCount();
                                            } else if (Type == ShipmentsType.Returns) {
                                                App.Object.userDistributorTabCtrl.ChangeTabTitle("Returns", App.Object.userDistributorReturnShipmentsFragment);
                                            }

                                            for (ShipmentWithDetail sd : cached.shipments) {
                                                sd.GenerateNotes();
                                                ITEMS.add(sd);
                                            }
                                            ApplyFilter();
                                            App.SetLoading(false);
                                        }
                                    } catch (Exception ex) {
                                        AppModel.ApplicationError(ex, "ScanCtrl::LoadShipments");
                                        App.SetLoading(false);
                                    }
                                }
                            });
                        }
                    });

                } catch (Exception ex) {
                    AppModel.ApplicationError(ex, "ScanCtrl::Load");
                    App.SetLoading(false);
                }
            }
        });
    }

    public void ApplyFilter() {
        String txt = et_Search.getText().toString();
        // Set the search query for text highlighting in ListDataBinder
        ListDataBinder.currentSearchQuery = AppModel.IsNullOrEmpty(txt) ? "" : txt.toLowerCase();

        List<ShipmentWithDetail> filtered = ITEMS;

        // Apply text search filter
        if (!AppModel.IsNullOrEmpty(txt)) {
            filtered = GetSearched(txt.toLowerCase());
        }

        // Apply client filters if any are active
        if (!activeClientFilters.isEmpty()) {
            filtered = GetClientFiltered(filtered);
        }

        binder.Initialize(filtered);
    }

    private List<ShipmentWithDetail> GetSearched(String txt) {
        List<ShipmentWithDetail> matched = new ArrayList<ShipmentWithDetail>();
        for (ShipmentWithDetail s : ITEMS) {
            // Search in: tracking ID, description, receiver name, receiver phone, receiver address
            boolean matches = false;

            if (s.shipment_id != null && s.shipment_id.toLowerCase().contains(txt)) matches = true;
            if (s.description != null && s.description.toLowerCase().contains(txt)) matches = true;
            if (s.description_title != null && s.description_title.toLowerCase().contains(txt)) matches = true;
            if (s.tracking_id != null && s.tracking_id.toLowerCase().contains(txt)) matches = true;
            if (s.receiver_name != null && s.receiver_name.toLowerCase().contains(txt)) matches = true;
            if (s.receiver_phone != null && s.receiver_phone.toLowerCase().contains(txt)) matches = true;
            if (s.receiver_address != null && s.receiver_address.toLowerCase().contains(txt)) matches = true;

            if (matches) {
                matched.add(s);
            }
        }
        return matched;
    }

    private List<ShipmentWithDetail> GetClientFiltered(List<ShipmentWithDetail> items) {
        List<ShipmentWithDetail> filtered = new ArrayList<ShipmentWithDetail>();
        for (ShipmentWithDetail s : items) {
            boolean matches = false;

            for (String filterType : activeClientFilters) {
                if (filterType.equals("wurth01")) {
                    // Wurth/1: client_id = 2785 AND tracking_id ends with -01
                    if (s.client_id != null && s.client_id.equals("2785") &&
                        s.tracking_id != null && s.tracking_id.endsWith("-01")) {
                        matches = true;
                        break;
                    }
                } else if (filterType.equals("wurthN")) {
                    // Wurth/N: client_id = 2785 AND tracking_id does NOT end with -01
                    if (s.client_id != null && s.client_id.equals("2785") &&
                        (s.tracking_id == null || !s.tracking_id.endsWith("-01"))) {
                        matches = true;
                        break;
                    }
                } else if (filterType.equals("others")) {
                    // Others: all other clients (client_id != 2785 or null)
                    if (s.client_id == null || !s.client_id.equals("2785")) {
                        matches = true;
                        break;
                    }
                }
            }

            if (matches) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    public void SetScannedCode(String code) {
        // Scanned = code;
        if (code != null) {
            boolean multiScanEnabled = chkMultiscan.isChecked();
            if (multiScanEnabled)
                App.Object.ShowScanner();

            // When scanning, we need to determine the correct status and statusId
            // scanStatusId tells us what type of scan was initiated:
            // 1 = In Delivery (regular), 7 = In Delivery (returns)
            // 4 = Delivered (regular), 20 = Delivered (returns)

            // For multi-scan to work properly with SMS, we need to send the correct status
            String status = "prezemena";
            int actualStatusId = scanStatusId;

            // Only send SMS for regular "In Delivery" scan (status ID 1)
            // Status ID 7 is for returns and should not trigger SMS
            if (scanStatusId == 1) {
                // This triggers SMS on the server side
                status = "prezemena";
                actualStatusId = scanStatusId; // Keep the original status ID for proper SMS handling

                // Also send SMS locally for immediate delivery (matching single scan behavior)
                sendSMSForShipment(code);
            } else if (scanStatusId == 7) {
                // Returns "In Delivery" - no SMS needed
                status = "prezemena";
                actualStatusId = scanStatusId;
            }

            SendKey(code, status, actualStatusId, false);
            KeyRef.PlayBeep();
        }
    }

    public void OnCommentsSubmit(String code, String comments) {
        SubmitRequest(code, "odbiena", 3, comments);
    }

    private void sendSMSForShipment(final String scannedCode) {
        // Try to send SMS immediately for multi-scan, matching single scan behavior
        AppModel.ApplicationError(null, "=== MULTI-SCAN SMS SEND START ===");
        AppModel.ApplicationError(null, "SMS: Attempting immediate send for code: " + scannedCode);

        // Create SMS data
        SMSHelper.SMSData smsData = new SMSHelper.SMSData();

        // Set tracking ID directly from scan
        smsData.trackingId = scannedCode;
        smsData.shipmentId = scannedCode;
        smsData.statusId = 1; // In Delivery
        smsData.driverName = App.CurrentUser != null ? App.CurrentUser.user : "";

        AppModel.ApplicationError(null, "SMS: Tracking ID set to: " + smsData.trackingId);

        // Try to find receiver details from current list
        boolean foundDetails = false;

        synchronized (ITEMS) {
            for (ShipmentWithDetail s : ITEMS) {
                if (s.tracking_id != null && s.tracking_id.equalsIgnoreCase(scannedCode)) {
                    AppModel.ApplicationError(null, "SMS: Found shipment details for: " + scannedCode);
                    smsData.receiverPhone = s.receiver_phone;
                    smsData.receiverName = s.receiver_name;
                    foundDetails = true;
                    break;
                }
            }
        }

        if (!foundDetails) {
            AppModel.ApplicationError(null, "SMS: No details found in list, will try to send with tracking ID only");
        }

        AppModel.ApplicationError(null, "SMS: Final data - Phone: " + smsData.receiverPhone +
            ", Tracking: " + smsData.trackingId);

        // Try to send immediately using the SMS queue
        SMSHelper.sendSMS(getContext(), smsData);
        boolean sent = true; // SMS is queued if not sent immediately

        if (sent) {
            AppModel.ApplicationError(null, "SMS: Successfully sent/queued for: " + scannedCode);
        } else {
            AppModel.ApplicationError(null, "SMS: Failed or already sent for: " + scannedCode);
        }

        AppModel.ApplicationError(null, "=== MULTI-SCAN SMS SEND END ===");
    }

    private void SendKey(String code, String status, int statusId, boolean sendComments) {
        try {
            if (AppModel.IsNullOrEmpty(code)) {
                MessageCtrl.Toast("Please scan barcode first");
                return;
            }

            if (sendComments)
                App.Object.sendProblemCommentsCtrl.SetVisibility(code, true);
            else
                SubmitRequest(code, status, statusId, null);

        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "ScanCtrl::SendKey");
        }
    }

    private void SubmitRequest(final String code, String status, int statudId, String comments) {
        App.SetProcessing(true);
        Communicator.SendDistributorRequest(code, status, statudId, comments, new IServerResponse() {
            @Override
            public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                App.Object.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (success) {
                                if (objs != null) {
                                    KeyRef u = (KeyRef) objs[0];
                                    if (!AppModel.IsNullOrEmpty(u.response_txt))
                                        MessageCtrl.Toast(u.response_txt);

                                    Load();

                                    App.Object.userDistributorReconcileShipmentsFragment.Load();
                                    App.Object.userDistributorReturnShipmentsFragment.Load();

                                } else
                                    MessageCtrl.Toast("Scanned key sending failed, Please try again");
                            } else if (!AppModel.IsNullOrEmpty(messageToShow)) {
                                MessageCtrl.Toast(messageToShow);

                                boolean isNew = true;
                                for (ShipmentWithDetail s : ITEMS) {
                                    if (s.tracking_id.equalsIgnoreCase(code)) {
                                        isNew = false;
                                        break;
                                    }
                                }

                                if (isNew) {
                                    ShipmentWithDetail s = new ShipmentWithDetail();
                                    s.tracking_id = code;
                                    s.description_title = code;
                                    ITEMS.add(s);
                                    AppModel.Object.SaveVariable(AppModel.MY_SHIPMENTS_CACHE_KEY, new Gson().toJson(ITEMS));
                                    ApplyFilter();
                                }
                            }
                        } catch (Exception ex) {
                            AppModel.ApplicationError(ex, "ScanCtrl::SendKey");
                        } finally {
                            App.SetProcessing(false);
                        }
                    }
                });
            }
        });
    }

    /**
     * Updates the delivered shipments count display for COD tab
     * Only shows for ReconcileShipments type
     */
    private void updateDeliveredCount() {
        if (Type != ShipmentsType.ReconcileShipments || llDeliveredInfo == null) {
            return;
        }

        // Count delivered shipments (status_id == 2)
        int deliveredCount = 0;
        for (ShipmentWithDetail s : ITEMS) {
            if (s.status_id == 2) {
                deliveredCount++;
            }
        }

        // Show the info bar and update count
        if (deliveredCount > 0) {
            llDeliveredInfo.setVisibility(View.VISIBLE);
            String countText = String.format(getContext().getString(R.string.delivered_count_label), deliveredCount);
            tvDeliveredCount.setText(countText);
        } else {
            llDeliveredInfo.setVisibility(View.GONE);
        }

        // Update client filter counts if on delivered screen
        if (Type == ShipmentsType.ReconcileShipments) {
            updateClientFilterCounts();
        }
    }

    private void setupClientFilterChips() {
        if (filterWurth01 == null || filterWurthN == null || filterOthers == null) {
            return;
        }

        filterWurth01.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleClientFilter("wurth01", filterWurth01);
            }
        });

        filterWurthN.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleClientFilter("wurthN", filterWurthN);
            }
        });

        filterOthers.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleClientFilter("others", filterOthers);
            }
        });
    }

    private void toggleClientFilter(String filterType, TextView chip) {
        if (activeClientFilters.contains(filterType)) {
            activeClientFilters.remove(filterType);
            chip.setBackgroundResource(getChipBackground(filterType, false));
        } else {
            activeClientFilters.add(filterType);
            chip.setBackgroundResource(getChipBackground(filterType, true));
        }
        ApplyFilter();
    }

    private int getChipBackground(String filterType, boolean selected) {
        switch (filterType) {
            case "wurth01":
                return selected ? R.drawable.chip_in_delivery_selected : R.drawable.chip_in_delivery;
            case "wurthN":
                return selected ? R.drawable.chip_picked_up_selected : R.drawable.chip_picked_up;
            case "others":
                return selected ? R.drawable.chip_problematic_selected : R.drawable.chip_problematic;
            default:
                return R.drawable.chip_in_delivery;
        }
    }

    private void updateClientFilterCounts() {
        int wurth01Count = 0;
        int wurthNCount = 0;
        int othersCount = 0;

        for (ShipmentWithDetail s : ITEMS) {
            if (s.client_id != null && s.client_id.equals("2785")) {
                if (s.tracking_id != null && s.tracking_id.endsWith("-01")) {
                    wurth01Count++;
                } else {
                    wurthNCount++;
                }
            } else {
                othersCount++;
            }
        }

        if (filterWurth01 != null) {
            filterWurth01.setText("Wurth/1 (" + wurth01Count + ")");
        }
        if (filterWurthN != null) {
            filterWurthN.setText("Wurth/N (" + wurthNCount + ")");
        }
        if (filterOthers != null) {
            filterOthers.setText("Others (" + othersCount + ")");
        }
    }
}
