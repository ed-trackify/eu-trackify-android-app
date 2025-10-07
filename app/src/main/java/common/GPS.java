package common;

import android.app.Service;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public class GPS {

    public static double Lat;
    public static double Lon;
    public static boolean IsConnected;

    public static final String INTERNAL_GPS_Provider = LocationManager.NETWORK_PROVIDER;

    public LocationListener listener = new LocationListener() {

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // UpdateStatus(status == LocationProvider.AVAILABLE ? DeviceStatus.Connected : status == LocationProvider.OUT_OF_SERVICE ? DeviceStatus.NotConnected : DeviceStatus.Connecting);
        }

        @Override
        public void onProviderEnabled(String provider) {
            IsConnected = true;
        }

        @Override
        public void onProviderDisabled(String provider) {
            IsConnected = false;
        }

        @Override
        public void onLocationChanged(Location location) {
            try {
                if (location != null) {
                    Lat = location.getLatitude();
                    Lon = location.getLongitude();

                    //Lat = 41.993638;
                    //Lon = 21.445184;

                    // Heading = (int) location.getBearing();
                    // int speed = (int) location.getSpeed();
                    // if (speed != Speed) {
                    // Speed = speed;
                    // }
                    // Compass = ConvertHeadingToDirection(Heading);
                    // Quality = (int) location.getAccuracy();
                    // Satellites = location.getExtras() == null ? 0 : location.getExtras().getInt("satellites", 0);
                    // Time = new Date(location.getTime());

                    for (IGpsUpdateListener listener : listeners_update) {
                        listener.onUpdate();
                    }
                }
            } catch (Exception ex) {
                AppModel.ApplicationError(ex, "GPS::onLocationChanged");
            }
        }
    };

    public void Start() {

        try {
            LocationManager lm = (LocationManager) App.Object.getSystemService(Service.LOCATION_SERVICE);
            boolean isEnabled = lm.isProviderEnabled(INTERNAL_GPS_Provider);
            if (isEnabled)
                listener.onProviderEnabled(INTERNAL_GPS_Provider);

            lm.requestLocationUpdates(INTERNAL_GPS_Provider, 0, 0, listener, Looper.getMainLooper());
            Location loc = lm.getLastKnownLocation(INTERNAL_GPS_Provider);
            if (loc != null) {
                listener.onLocationChanged(loc);
            }

        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "GPS::Start");
        }
    }

    public static boolean IsValidPoint(Double latitude, Double longitude) {
        return latitude != null && longitude != null && latitude != 0 && longitude != 0 && latitude != 0.0 && longitude != 0.0 && latitude != -180 && longitude != -180 && latitude != -180.0 && longitude != -180.0;
    }

    private List<IGpsStatusChangedListener> listeners_status = new ArrayList<IGpsStatusChangedListener>();

    public interface IGpsStatusChangedListener {
        public void onStatusChanged(boolean isConnected);
    }

    public synchronized void addStatusChangedListener(IGpsStatusChangedListener listener) {
        listeners_status.add(listener);
        listener.onStatusChanged(IsConnected);
    }

    public synchronized void removeStatusChangedListener(IGpsStatusChangedListener listener) {
        listeners_status.remove(listener);
    }

    public void UpdateStatus(boolean isConnected) {
        if (IsConnected != isConnected) {

            IsConnected = isConnected;

            for (IGpsStatusChangedListener listener : listeners_status) {
                listener.onStatusChanged(isConnected);
            }
        }
    }

    // <<< ****** Update ****** >>>
    private List<IGpsUpdateListener> listeners_update = new ArrayList<IGpsUpdateListener>();

    public interface IGpsUpdateListener {
        public void onUpdate();
    }

    public synchronized void addGpsUpdateListener(IGpsUpdateListener listener) {
        listeners_update.add(listener);
        listener.onUpdate();
    }

    public synchronized void removeGpsUpdateListener(IGpsUpdateListener listener) {
        listeners_update.remove(listener);
    }
}
