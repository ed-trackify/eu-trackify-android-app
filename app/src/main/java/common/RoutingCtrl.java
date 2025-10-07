package common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import eu.trackify.net.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import common.route.RouteCalculator;
import common.route.RouteDetail;

public class RoutingCtrl extends LinearLayout {

    LayoutInflater inflater;

    GoogleMap map;
    List<Polyline> routeLines = new ArrayList<>();
    List<Marker> markers = new ArrayList<>();
    Marker markerCurrentLocation;
    String REQUEST_GROUP_ID;

    List<ShipmentWithDetail> SHIPMENTS = new ArrayList<>();

    public RoutingCtrl(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.ctrl_routing, this);

        if (!this.isInEditMode()) {

            SupportMapFragment mapFragment = (SupportMapFragment) App.Object.getSupportFragmentManager().findFragmentById(R.id.map);
            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    map = googleMap;
                    map.getUiSettings().setZoomControlsEnabled(true);
                    map.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                        @Override
                        public void onMapLoaded() {
                            if (SHIPMENTS.size() > 0)
                                CalculateRoute(SHIPMENTS);
                        }
                    });
                }
            });

            findViewById(R.id.transparentImage).setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    int action = event.getAction();
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_MOVE:
                            findViewById(R.id.btnAutoCenter).setVisibility(VISIBLE);
                            return false;
                        default:
                            return true;
                    }
                }
            });

            findViewById(R.id.btnAutoCenter).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    view.setVisibility(GONE);
                    centerToCurrentLocation();
                }
            });

            findViewById(R.id.btnRecalculate).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    CalculateRoute(SHIPMENTS);
                }
            });

            AppModel.Object.gps.addGpsUpdateListener(new GPS.IGpsUpdateListener() {
                @Override
                public void onUpdate() {
                    LatLng ll = new LatLng(GPS.Lat, GPS.Lon);

                    if (markerCurrentLocation != null)
                        markerCurrentLocation.remove();

                    if (map != null) {
                        markerCurrentLocation = map.addMarker(new MarkerOptions()
                                .position(ll)
                                .icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(App.Object.getResources(), R.drawable.pin)))
                                .title(App.CurrentUser != null ? App.CurrentUser.user : ll.toString()));

                        centerToCurrentLocation();
                    }
                }
            });
        }
    }

    private void centerToCurrentLocation() {
        if (map != null && markerCurrentLocation != null && findViewById(R.id.btnAutoCenter).getVisibility() == View.GONE)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(markerCurrentLocation.getPosition(), map.getCameraPosition().zoom));
    }

    public void show() {
        App.Object.routingCtrl.setVisibility(VISIBLE);
        if (!GPS.IsConnected)
            MessageCtrl.Toast("Location service not enabled");
    }

    public BitmapDescriptor createMarkerBitmap(Integer nullableSeq) {
        View view = inflater.inflate(R.layout.pin_marker, null);
        ((TextView) view.findViewById(R.id.tv)).setText("" + (nullableSeq == null ? "S" : nullableSeq));
        DisplayMetrics displayMetrics = new DisplayMetrics();
        App.Object.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        view.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        view.measure(displayMetrics.widthPixels, displayMetrics.heightPixels);
        view.layout(0, 0, 0, 0);
        view.buildDrawingCache();
        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    public void CalculateRoute(List<ShipmentWithDetail> items) {
        try {
            SHIPMENTS = items;

            if (map != null) {

                REQUEST_GROUP_ID = UUID.randomUUID().toString();

                for (Marker m : markers)
                    m.remove();
                markers.clear();

                for (Polyline r : routeLines)
                    r.remove();
                routeLines.clear();

                List<LatLng> latLngs = new ArrayList<>();

                if (GPS.IsValidPoint(GPS.Lat, GPS.Lon))
                    latLngs.add(new LatLng(GPS.Lat, GPS.Lon));

                for (ShipmentWithDetail s : items) {
                    if (GPS.IsValidPoint(s.lat, s.lon)) {
                        markers.add(map.addMarker(new MarkerOptions()
                                .position(new LatLng(s.lat, s.lon))
                                .icon(createMarkerBitmap(null))
                                .title(s.receiver_address)));
                        latLngs.add(markers.get(markers.size() - 1).getPosition());
                    }
                }

                if (latLngs.size() > 1) {
                    LatLngBounds.Builder builder = LatLngBounds.builder();
                    for (LatLng ll : latLngs)
                        builder.include(ll);
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));

                    RouteCalculator.Calculate(latLngs, REQUEST_GROUP_ID, new RouteCalculator.IRoutingResponse() {
                        @Override
                        public void onRouteCalculated(final boolean success, final String requestGroupId, final RouteDetail route) {
                            if (!REQUEST_GROUP_ID.equalsIgnoreCase(requestGroupId))
                                return;

                            App.Object.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (success && route != null) {
                                        routeLines.add(map.addPolyline(new PolylineOptions().width(30f).color(App.Object.getResources().getColor(R.color.Black_Bg2)).addAll(route.toLatLngs())));
                                        markStopsOrdering();
                                    } else
                                        MessageCtrl.Toast("Unable to calculate route, please try again");
                                }
                            });
                        }
                    });
                }
            }
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "RoutingCtrl::CalculateRoute");
        }
    }

    private void markStopsOrdering() {
        int order = 1;
        for (Polyline pl : routeLines) {
            for (LatLng ll : pl.getPoints()) {
                for (Marker m : markers) {
                    double distance = RouteCalculator.Distance(ll.latitude, ll.longitude, m.getPosition().latitude, m.getPosition().longitude, "M");
                    if (m.getTag() == null && distance <= 5) {
                        m.setTag(ll);
                        m.setIcon(createMarkerBitmap(order));
                        order++;
                        break;
                    }
                }
            }
        }
    }
}