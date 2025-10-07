package common;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.cardview.widget.CardView;

public class SMSQueueStatusView extends CardView {
    private TextView statusText;
    private TextView queueSizeText;
    private TextView capacityText;
    private Handler updateHandler;
    private Runnable updateRunnable;
    private static final int UPDATE_INTERVAL = 2000; // Update every 2 seconds
    
    public SMSQueueStatusView(Context context) {
        super(context);
        init(context);
    }
    
    public SMSQueueStatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    private void init(Context context) {
        // Card settings
        setCardElevation(4);
        setRadius(8);
        setCardBackgroundColor(Color.parseColor("#F5F5F5"));
        
        // Create layout
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 12, 16, 12);
        
        // Title
        TextView title = new TextView(context);
        title.setText("SMS Queue Status");
        title.setTextSize(14);
        title.setTextColor(Color.parseColor("#666666"));
        title.setGravity(Gravity.CENTER);
        layout.addView(title);
        
        // Queue size
        queueSizeText = new TextView(context);
        queueSizeText.setTextSize(16);
        queueSizeText.setTextColor(Color.BLACK);
        queueSizeText.setGravity(Gravity.CENTER);
        queueSizeText.setPadding(0, 4, 0, 0);
        layout.addView(queueSizeText);
        
        // Capacity
        capacityText = new TextView(context);
        capacityText.setTextSize(14);
        capacityText.setTextColor(Color.parseColor("#666666"));
        capacityText.setGravity(Gravity.CENTER);
        capacityText.setPadding(0, 4, 0, 0);
        layout.addView(capacityText);
        
        // Status
        statusText = new TextView(context);
        statusText.setTextSize(12);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 4, 0, 0);
        layout.addView(statusText);
        
        addView(layout);
        
        // Setup update handler
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateStatus();
                updateHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
    }
    
    public void startUpdating() {
        updateHandler.post(updateRunnable);
    }
    
    public void stopUpdating() {
        updateHandler.removeCallbacks(updateRunnable);
    }
    
    private void updateStatus() {
        try {
            SMSQueueManager manager = SMSQueueManager.getInstance(getContext());
            int queueSize = manager.getQueueSize();
            int sent = manager.getSentCount();
            int remaining = manager.getRemainingCapacity();
            
            // Update queue size
            queueSizeText.setText("Queue: " + queueSize + " pending");
            if (queueSize > 10) {
                queueSizeText.setTextColor(Color.parseColor("#FF6B6B"));
            } else if (queueSize > 5) {
                queueSizeText.setTextColor(Color.parseColor("#FFA500"));
            } else {
                queueSizeText.setTextColor(Color.parseColor("#4CAF50"));
            }
            
            // Update capacity
            capacityText.setText("Sent: " + sent + "/30 (Next reset in 30 min)");
            
            // Update status
            if (queueSize == 0) {
                statusText.setText("✓ All messages sent");
                statusText.setTextColor(Color.parseColor("#4CAF50"));
            } else if (remaining == 0) {
                statusText.setText("⚠ Rate limit reached - waiting");
                statusText.setTextColor(Color.parseColor("#FF6B6B"));
            } else {
                statusText.setText("➤ Processing queue...");
                statusText.setTextColor(Color.parseColor("#2196F3"));
            }
            
            // Show/hide based on queue activity
            if (queueSize > 0 || sent > 0) {
                setVisibility(VISIBLE);
            }
            
        } catch (Exception e) {
            AppModel.ApplicationError(e, "SMSQueueStatusView::updateStatus");
        }
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startUpdating();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopUpdating();
    }
}