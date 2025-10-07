package common.route;

import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import common.AppModel;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RouteCalculator {

    public interface IRoutingResponse {
        public void onRouteCalculated(boolean success, String requestGroupId, RouteDetail route);
    }

    private static final String BASE_URL = "https://trueway-directions2.p.rapidapi.com/FindDrivingRoute?stops=[STOPS]&optimize=true";

    public static void Calculate(final List<LatLng> stops, final String requestGroupId, final IRoutingResponse callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<List<LatLng>> items = CreateChunks(stops, 20);
                for (List<LatLng> lls : items) {
                    CalculateChunk(lls, requestGroupId, callback);
                }
            }
        }).start();
    }

    private static List<List<LatLng>> CreateChunks(List<LatLng> stops, int chunkLength) {
        List<List<LatLng>> items = new ArrayList<>();
        List<LatLng> chunk = new ArrayList<>();
        for (LatLng ll : stops) {
            if (chunk.size() == chunkLength) {
                items.add(chunk);
                LatLng lastItem = chunk.get(chunk.size() - 1);
                chunk = new ArrayList<>();
                chunk.add(lastItem);
            }
            chunk.add(ll);
        }
        if (chunk.size() > 1)
            items.add(chunk);
        return items;
    }

    private static void CalculateChunk(final List<LatLng> stops, final String requestGroupId, final IRoutingResponse callback) {
        try {
            OkHttpClient client = new OkHttpClient();

            String llString = "";
            for (LatLng ll : stops) {
                String str = ll.latitude + "," + ll.longitude;
                llString += llString.isEmpty() ? str : ";" + str;
            }
            String finalUrl = BASE_URL.replace("[STOPS]", URLEncoder.encode(llString));

            Request request = new Request.Builder()
                    .url(finalUrl)
                    .get()
                    .addHeader("x-rapidapi-host", "trueway-directions2.p.rapidapi.com")
                    .addHeader("x-rapidapi-key", "4e6fa228b2msh5f7d3859007466dp17e1e5jsn4b58320ecd72")
                    .build();

            Response response = client.newCall(request).execute();
            callback.onRouteCalculated(true, requestGroupId, new Gson().fromJson(response.body().string(), RouteResponse.class).route);

        } catch (Exception ex) {
            callback.onRouteCalculated(false, requestGroupId, null);
            AppModel.ApplicationError(ex, "Communicator::LoadShipmentsWithDetails");
        }
    }

    public static double Distance(double lat1, double lon1, double lat2, double lon2, String unit) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        if (unit.equalsIgnoreCase("K")) {
            dist = dist * 1.609344;
        } else if (unit.equalsIgnoreCase("N")) {
            dist = dist * 0.8684;
        }
        return (dist);
    }

    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    /*::  This function converts decimal degrees to radians             :*/
    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    private static double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    /*::  This function converts radians to decimal degrees             :*/
    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    private static double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }
}
