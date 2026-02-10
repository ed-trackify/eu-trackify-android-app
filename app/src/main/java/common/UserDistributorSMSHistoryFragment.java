package common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import eu.trackify.net.R;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import common.Communicator.IServerResponse;

public class UserDistributorSMSHistoryFragment extends Fragment {

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private SMSHistoryAdapter adapter;
    private List<SMSMessage> messages = new ArrayList<>();
    private boolean isLoaded = false;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sms_history, container, false);
        
        listView = view.findViewById(R.id.smsListView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyView = view.findViewById(R.id.emptyView);
        
        adapter = new SMSHistoryAdapter(getActivity(), messages);
        listView.setAdapter(adapter);
        
        // Don't load automatically, wait for Initialize() to be called
        
        return view;
    }
    
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser && !isLoaded) {
            loadSMSHistory();
        }
    }
    
    public void Initialize() {
        // Called when shipment is selected, but don't load yet
        // Wait for tab to be actually viewed
        isLoaded = false;
    }
    
    private void loadSMSHistory() {
        isLoaded = true;

        // Determine shipment ID based on context
        String shipmentId = null;
        if (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType ==
                common.UserDistributorShipmentsFragment.ShipmentsType.StatusCheck) {
            if (StatusCheckCtrl.SELECTED != null) {
                shipmentId = StatusCheckCtrl.SELECTED.shipment_id;
            }
        } else if (App.Object.userDistributorMyShipmentsFragment != null &&
                App.Object.userDistributorMyShipmentsFragment.SELECTED != null) {
            shipmentId = App.Object.userDistributorMyShipmentsFragment.SELECTED.shipment_id;
        }

        if (shipmentId != null && !shipmentId.isEmpty()) {
            showLoading(true);

            Communicator.GetSMSHistory(shipmentId, new IServerResponse() {
                @Override
                public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showLoading(false);

                                if (success && objs != null && objs.length > 0) {
                                    SMSHistoryResponse response = (SMSHistoryResponse) objs[0];

                                    if (response.success && response.messages != null) {
                                        messages.clear();
                                        messages.addAll(response.messages);
                                        adapter.notifyDataSetChanged();

                                        // Update tab count
                                        if (App.Object.userDistributorShipmentDetailTabCtrl != null) {
                                            App.Object.userDistributorShipmentDetailTabCtrl.UpdateTabCount(
                                                UserDistributorSMSHistoryFragment.this,
                                                messages.size()
                                            );
                                        }

                                        if (messages.isEmpty()) {
                                            showEmptyState("No SMS messages sent for this shipment");
                                        } else {
                                            showList();
                                        }
                                    } else {
                                        showEmptyState(response.error != null ? response.error : "Failed to load SMS history");
                                    }
                                } else {
                                    showEmptyState("Failed to load SMS history");
                                }
                            }
                        });
                    }
                }
            });
        } else {
            showEmptyState("No shipment selected");
        }
    }
    
    private void showLoading(boolean show) {
        if (progressBar != null && listView != null && emptyView != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            listView.setVisibility(show ? View.GONE : View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }
    
    private void showEmptyState(String message) {
        if (progressBar != null && listView != null && emptyView != null) {
            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(message);
        }
    }
    
    private void showList() {
        if (progressBar != null && listView != null && emptyView != null) {
            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }
    
    // Data Models
    public static class SMSHistoryResponse {
        public boolean success;
        public String shipment_id;
        public String tracking_id;
        public int sms_count;
        public List<SMSMessage> messages;
        public String error;
    }
    
    public static class SMSMessage {
        public int id;
        public String phone;
        public String message;
        public String pin_code;
        public String status;
        public String created_at;
        public String sent_at;
        public int attempts;
        public String response;
        public String type;
        public String source;
        public Integer user_id;
        
        public String getFormattedDate() {
            try {
                SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                SimpleDateFormat output = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                Date date = input.parse(created_at);
                return output.format(date != null ? date : new Date());
            } catch (Exception e) {
                return created_at != null ? created_at : "";
            }
        }
        
        public String getStatusDisplay() {
            if (status == null) return "UNKNOWN";
            return status.toUpperCase();
        }
        
        public String getTypeDisplay() {
            if (type == null) return "Unknown";
            
            switch (type) {
                case "auto_warehouse":
                    return "Auto (Warehouse)";
                case "auto_delivery":
                    return "Auto (Delivery)";
                case "queued":
                    return "Queued";
                case "manual":
                    return "Manual";
                default:
                    return type;
            }
        }
    }
}