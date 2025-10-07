package common.route;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class RouteDetail {

    public long duration;
    public long distance;
    public RouteGeometry geometry;

    public List<LatLng> toLatLngs() {
        List<LatLng> items = new ArrayList<>();
        if (geometry != null && geometry.coordinates != null && geometry.coordinates.length > 0) {
            for (double[] ll : geometry.coordinates) {
                items.add(new LatLng(ll[0], ll[1]));
            }
        }
        return items;
    }
}
