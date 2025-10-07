package common;

import com.google.gson.annotations.SerializedName;

public class SMSReply {
    @SerializedName("phone_from")
    public String phoneFrom;

    @SerializedName("phone_to")
    public String phoneTo;

    @SerializedName("message")
    public String message;

    @SerializedName("received_timestamp")
    public long receivedTimestamp;

    @SerializedName("shipment_id")
    public Long shipmentId;

    @SerializedName("original_queue_id")
    public Integer originalQueueId;

    @SerializedName("original_message")
    public String originalMessage;

    @SerializedName("original_sent_timestamp")
    public Long originalSentTimestamp;

    @SerializedName("device_info")
    public DeviceInfo deviceInfo;

    // For local storage and tracking
    public transient String smsId; // Android SMS database ID to track which messages we've processed
    public transient boolean isSubmitted = false;
    public transient int retryCount = 0;
    public transient long lastRetryTime = 0;

    public static class DeviceInfo {
        @SerializedName("driver_id")
        public Long driverId;

        @SerializedName("driver_username")
        public String driverUsername;

        @SerializedName("device_model")
        public String deviceModel;

        @SerializedName("android_version")
        public String androidVersion;

        @SerializedName("app_version")
        public String appVersion;

        @SerializedName("sim_operator")
        public String simOperator;

        public DeviceInfo() {
            this.deviceModel = android.os.Build.MODEL;
            this.androidVersion = android.os.Build.VERSION.RELEASE;
            try {
                this.appVersion = App.Object.getPackageManager()
                    .getPackageInfo(App.Object.getPackageName(), 0).versionName;
            } catch (Exception e) {
                this.appVersion = "Unknown";
            }
        }
    }

    public SMSReply() {
        this.receivedTimestamp = System.currentTimeMillis();
        this.deviceInfo = new DeviceInfo();
        if (App.CurrentUser != null) {
            this.deviceInfo.driverId = (long) App.CurrentUser.user_id;
            this.deviceInfo.driverUsername = App.CurrentUser.user;
        }
    }
}