package common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import eu.trackify.net.R;
import com.svenkapudija.imageresizer.ImageResizer;

import java.io.File;
import java.text.DecimalFormat;

import common.CountingInputStreamEntity.IUploadListener;

public class ImageUploadCtrl extends LinearLayout {

    // UI Elements
    private LinearLayout llImageOptions, llImagePreview, llProgress;
    private ImageView ivPreview;
    private EditText etComments;
    private TextView tvImageSize, tvImageDimensions, tvProgress, btnCancel, btnChangeImage;
    private Button btnSubmit;
    private View btnCamera, btnGallery;
    private ProgressBar progressBar;
    
    // Image handling
    private File selectedImageFile;
    private boolean isImageSelected = false;
    
    // Callback
    public interface IImageUploadCallback {
        void onImageSelected(File imageFile);
        void onUploadComplete(boolean success);
    }
    
    private IImageUploadCallback callback;

    public ImageUploadCtrl(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.popup_image_upload, this);

        if (!this.isInEditMode()) {
            initializeViews(v);
            setupClickListeners();
            setupTouchInterception();
        }
    }

    private void initializeViews(View v) {
        // Layout containers
        llImageOptions = v.findViewById(R.id.llImageOptions);
        llImagePreview = v.findViewById(R.id.llImagePreview);
        llProgress = v.findViewById(R.id.llProgress);
        
        // Image views
        ivPreview = v.findViewById(R.id.ivPreview);
        
        // Text views
        tvImageSize = v.findViewById(R.id.tvImageSize);
        tvImageDimensions = v.findViewById(R.id.tvImageDimensions);
        tvProgress = v.findViewById(R.id.tvProgress);
        etComments = v.findViewById(R.id.et_Comments);
        
        // Buttons
        btnCancel = v.findViewById(R.id.btnCancel);
        btnChangeImage = v.findViewById(R.id.btnChangeImage);
        btnSubmit = v.findViewById(R.id.btnSubmit);
        btnCamera = v.findViewById(R.id.btnCamera);
        btnGallery = v.findViewById(R.id.btnGallery);
        
        // Progress
        progressBar = v.findViewById(R.id.progressBar);
    }

    private void setupClickListeners() {
        // Cancel button
        btnCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });
        
        // Camera option
        btnCamera.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AttachmentManager.AttachImage(new Runnable() {
                    @Override
                    public void run() {
                        onImageSelected();
                    }
                });
            }
        });
        
        // Gallery option
        btnGallery.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                selectFromGallery();
            }
        });
        
        // Change image button
        btnChangeImage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showImageOptions();
            }
        });
        
        // Submit button
        btnSubmit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadImage();
            }
        });
    }

    private void setupTouchInterception() {
        RelativeLayout rlPopupRootView = findViewById(R.id.rl_popupRootView);
        rlPopupRootView.setOnTouchListener(new OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
    }

    private void selectFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        App.Object.galleryLauncher.launch(intent);
    }

    public void onGalleryImageSelected(Uri uri) {
        AttachmentManager.OnFileSelect(App.Object, uri, new Runnable() {
            @Override
            public void run() {
                onImageSelected();
            }
        });
    }

    private void onImageSelected() {
        if (AttachmentManager.AttachFile != null && AttachmentManager.AttachFile.exists()) {
            selectedImageFile = AttachmentManager.AttachFile;
            isImageSelected = true;
            showImagePreview();
        } else {
            MessageCtrl.Toast(getContext().getString(R.string.pictures_error_select));
        }
    }

    private void showImageOptions() {
        llImageOptions.setVisibility(VISIBLE);
        llImagePreview.setVisibility(GONE);
        llProgress.setVisibility(GONE);
    }

    private void showImagePreview() {
        try {
            // Load and display the image
            Bitmap bitmap = BitmapFactory.decodeFile(selectedImageFile.getAbsolutePath());
            ivPreview.setImageBitmap(bitmap);
            
            // Show image info
            long fileSize = selectedImageFile.length();
            String sizeStr = formatFileSize(fileSize);
            tvImageSize.setText("Size: " + sizeStr);
            
            if (bitmap != null) {
                tvImageDimensions.setText("Dimensions: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            }
            
            // Update UI visibility
            llImageOptions.setVisibility(GONE);
            llImagePreview.setVisibility(VISIBLE);
            llProgress.setVisibility(GONE);
            
        } catch (Exception e) {
            AppModel.ApplicationError(e, "ImageUploadCtrl::showImagePreview");
            MessageCtrl.Toast(getContext().getString(R.string.pictures_error_load));
        }
    }

    private void uploadImage() {
        if (!isImageSelected || selectedImageFile == null) {
            MessageCtrl.Toast(getContext().getString(R.string.pictures_error_no_image));
            return;
        }
        
        // Get comments
        final String comments = etComments.getText().toString().trim();
        
        // Resize image to standard quality (800px max dimension)
        resizeImage(800);
        
        // Show progress
        llImagePreview.setVisibility(GONE);
        llProgress.setVisibility(VISIBLE);
        progressBar.setProgress(0);
        tvProgress.setText("0%");
        
        // Upload the image
        App.Object.userDistributorShipmentDetail.SubmitPictureWithProgress(
            comments, 
            selectedImageFile,
            new IUploadListener() {
                @Override
                public void OnProgressChanged(final int percent) {
                    App.Object.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setProgress(percent);
                            tvProgress.setText(percent + "%");
                        }
                    });
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    App.Object.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            hide();
                            
                            // Reload shipment data from server to get updated pictures
                            try {
                                // Check which fragment is active and reload its data
                                if (App.Object.userDistributorShipmentDetailTabCtrl != null && 
                                    App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType != null) {
                                    
                                    UserDistributorShipmentsFragment.ShipmentsType shipmentType = 
                                        App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType;
                                    
                                    if (shipmentType == UserDistributorShipmentsFragment.ShipmentsType.MyShipments && 
                                        App.Object.userDistributorMyShipmentsFragment != null) {
                                        App.Object.userDistributorMyShipmentsFragment.RefreshSelectedShipment();
                                    } else if (shipmentType == UserDistributorShipmentsFragment.ShipmentsType.Returns && 
                                        App.Object.userDistributorReturnShipmentsFragment != null) {
                                        App.Object.userDistributorReturnShipmentsFragment.RefreshSelectedShipment();
                                    } else if (shipmentType == UserDistributorShipmentsFragment.ShipmentsType.ReconcileShipments && 
                                        App.Object.userDistributorReconcileShipmentsFragment != null) {
                                        App.Object.userDistributorReconcileShipmentsFragment.RefreshSelectedShipment();
                                    }
                                }
                            } catch (Exception e) {
                                AppModel.ApplicationError(e, "ImageUploadCtrl::uploadImage::refreshShipment");
                            }
                            
                            if (callback != null) {
                                callback.onUploadComplete(true);
                            }
                        }
                    });
                }
            }
        );
    }


    private void resizeImage(int maxDimension) {
        try {
            if (selectedImageFile != null && selectedImageFile.exists()) {
                Bitmap resized = ImageResizer.resize(selectedImageFile, maxDimension, maxDimension);
                ImageResizer.saveToFile(resized, selectedImageFile);
                
                // Update file size display after resize
                long fileSize = selectedImageFile.length();
                String sizeStr = formatFileSize(fileSize);
                tvImageSize.setText("Size: " + sizeStr);
            }
        } catch (Exception e) {
            AppModel.ApplicationError(e, "ImageUploadCtrl::resizeImage");
        }
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public void show(IImageUploadCallback callback) {
        this.callback = callback;
        reset();
        this.setVisibility(View.VISIBLE);
    }

    public void hide() {
        this.setVisibility(View.GONE);
        reset();
    }

    private void reset() {
        // Reset UI to initial state
        showImageOptions();
        etComments.setText("");
        isImageSelected = false;
        selectedImageFile = null;
        ivPreview.setImageBitmap(null);
        progressBar.setProgress(0);
        tvProgress.setText("0%");
    }
}