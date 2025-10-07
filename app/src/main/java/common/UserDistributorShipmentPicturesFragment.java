package common;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.io.File;

import eu.trackify.net.R;

import common.ListDataBinder.BindedListType;
import common.UserDistributorShipmentsFragment.ShipmentsType;

public class UserDistributorShipmentPicturesFragment extends Fragment {

    ListView lv_results;
    ListDataBinder<PictureItem> binder;
    View btnAddPicture;
    View emptyState;
    TextView tvPictureCount;

    ShipmentWithDetail Current;
    
    // Broadcast receiver for picture upload notifications
    private BroadcastReceiver pictureUploadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.mex.delivery.PICTURE_UPLOADED".equals(intent.getAction())) {
                // Refresh the pictures list when a picture was uploaded
                Initialize();
            }
        }
    };

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.ctrl_pictures_tab, null);

        lv_results = (ListView) v.findViewById(R.id.lv_results);
        binder = new ListDataBinder<PictureItem>(BindedListType.Pictures, lv_results);
        emptyState = v.findViewById(R.id.emptyState);
        tvPictureCount = (TextView) v.findViewById(R.id.tvPictureCount);
        
        btnAddPicture = v.findViewById(R.id.btnAddPicture);
        btnAddPicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                App.Object.imageUploadCtrl.show(new ImageUploadCtrl.IImageUploadCallback() {
                    @Override
                    public void onImageSelected(File imageFile) {
                        // Image selected, preview shown
                    }
                    
                    @Override
                    public void onUploadComplete(boolean success) {
                        if (success) {
                            // RefreshAllTabs is called from ImageUploadCtrl, so no need to call Initialize() here
                            MessageCtrl.Toast("Picture uploaded successfully");
                        }
                    }
                });
            }
        });

        Initialize();

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register broadcast receiver for picture upload notifications
        IntentFilter filter = new IntentFilter("com.mex.delivery.PICTURE_UPLOADED");
        getContext().registerReceiver(pictureUploadReceiver, filter);
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Unregister broadcast receiver
        try {
            getContext().unregisterReceiver(pictureUploadReceiver);
        } catch (Exception e) {
            // Receiver might not be registered
        }
    }
    
    public void Initialize() {
        try {
            Current = App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.MyShipments ? App.Object.userDistributorMyShipmentsFragment.SELECTED : (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.Returns ?  App.Object.userDistributorReturnShipmentsFragment.SELECTED : App.Object.userDistributorReconcileShipmentsFragment.SELECTED);
            if (Current != null) {
                binder.Initialize(Current._Images);
                App.Object.userDistributorShipmentDetailTabCtrl.ChangeTabTitle("Pictures (" + Current._Images.size() + ")", this);
                
                // Update picture count
                int pictureCount = Current._Images.size();
                tvPictureCount.setText(pictureCount + (pictureCount == 1 ? " item" : " items"));
                
                // Show/hide empty state
                if (pictureCount == 0) {
                    emptyState.setVisibility(View.VISIBLE);
                    lv_results.setVisibility(View.GONE);
                } else {
                    emptyState.setVisibility(View.GONE);
                    lv_results.setVisibility(View.VISIBLE);
                }
                
                // Allow picture upload while shipment is available to the user
                // Show button for any shipment the user currently has access to
                boolean allowPictureUpload = true;
                
                // Only hide for Reconciled shipments which are typically completed/archived
                if (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.ReconcileShipments) {
                    allowPictureUpload = false;
                }
                
                // Show or hide the Add Picture button based on shipment availability
                if (allowPictureUpload && Current != null) {
                    btnAddPicture.setVisibility(View.VISIBLE);
                } else {
                    btnAddPicture.setVisibility(View.GONE);
                    // Update empty state message when add button is hidden
                    if (pictureCount == 0) {
                        TextView emptyText = (TextView) emptyState.findViewById(android.R.id.text2);
                        if (emptyText != null) {
                            emptyText.setText("No pictures available for completed shipments");
                        }
                    }
                }
            }
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "UserDistributorShipmentPicturesFragment::Load");
        }
    }
}
