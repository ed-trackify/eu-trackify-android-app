package common;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import eu.trackify.net.R;

import java.util.List;

public class SMSHistoryAdapter extends BaseAdapter {
    
    private Context context;
    private List<UserDistributorSMSHistoryFragment.SMSMessage> messages;
    private LayoutInflater inflater;
    
    public SMSHistoryAdapter(Context context, List<UserDistributorSMSHistoryFragment.SMSMessage> messages) {
        this.context = context;
        this.messages = messages;
        this.inflater = LayoutInflater.from(context);
    }
    
    @Override
    public int getCount() {
        return messages.size();
    }
    
    @Override
    public Object getItem(int position) {
        return messages.get(position);
    }
    
    @Override
    public long getItemId(int position) {
        return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_sms_message, parent, false);
            holder = new ViewHolder();
            holder.statusText = convertView.findViewById(R.id.statusText);
            holder.typeText = convertView.findViewById(R.id.typeText);
            holder.timeText = convertView.findViewById(R.id.timeText);
            holder.phoneText = convertView.findViewById(R.id.phoneText);
            holder.messageText = convertView.findViewById(R.id.messageText);
            holder.pinCodeText = convertView.findViewById(R.id.pinCodeText);
            holder.attemptsText = convertView.findViewById(R.id.attemptsText);
            holder.messageContainer = convertView.findViewById(R.id.messageContainer);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        UserDistributorSMSHistoryFragment.SMSMessage sms = messages.get(position);
        
        // Set status with color
        holder.statusText.setText(sms.getStatusDisplay());
        setStatusColor(holder.statusText, sms.status);
        
        // Set type and time
        holder.typeText.setText(sms.getTypeDisplay());
        holder.timeText.setText(sms.getFormattedDate());
        
        // Set phone
        holder.phoneText.setText(sms.phone != null ? sms.phone : "Unknown");
        
        // Set message
        holder.messageText.setText(sms.message != null ? sms.message : "No message content");
        
        // Show PIN if available
        if (sms.pin_code != null && !sms.pin_code.isEmpty()) {
            holder.pinCodeText.setVisibility(View.VISIBLE);
            holder.pinCodeText.setText("PIN: " + sms.pin_code);
            
            // Add click listener to copy PIN
            holder.pinCodeText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyToClipboard("PIN", sms.pin_code);
                }
            });
        } else {
            holder.pinCodeText.setVisibility(View.GONE);
        }
        
        // Show attempts if more than 1
        if (sms.attempts > 1) {
            holder.attemptsText.setVisibility(View.VISIBLE);
            holder.attemptsText.setText("Attempts: " + sms.attempts);
        } else {
            holder.attemptsText.setVisibility(View.GONE);
        }
        
        // Add long click to copy message
        holder.messageContainer.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (sms.message != null) {
                    copyToClipboard("SMS Message", sms.message);
                }
                return true;
            }
        });
        
        return convertView;
    }
    
    private void setStatusColor(TextView statusText, String status) {
        if (status == null) {
            statusText.setBackgroundResource(R.drawable.status_unknown);
            return;
        }
        
        switch (status.toLowerCase()) {
            case "sent":
                statusText.setBackgroundResource(R.drawable.status_sent);
                statusText.setTextColor(context.getResources().getColor(R.color.White));
                break;
            case "pending":
                statusText.setBackgroundResource(R.drawable.status_pending);
                statusText.setTextColor(context.getResources().getColor(R.color.Black));
                break;
            case "failed":
                statusText.setBackgroundResource(R.drawable.status_failed);
                statusText.setTextColor(context.getResources().getColor(R.color.White));
                break;
            default:
                statusText.setBackgroundResource(R.drawable.status_unknown);
                statusText.setTextColor(context.getResources().getColor(R.color.Black));
                break;
        }
    }
    
    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, label + " copied", Toast.LENGTH_SHORT).show();
    }
    
    static class ViewHolder {
        TextView statusText;
        TextView typeText;
        TextView timeText;
        TextView phoneText;
        TextView messageText;
        TextView pinCodeText;
        TextView attemptsText;
        LinearLayout messageContainer;
    }
}