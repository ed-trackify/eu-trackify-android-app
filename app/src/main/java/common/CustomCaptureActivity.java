package common;

import android.os.Bundle;
import android.view.View;

import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.CompoundBarcodeView;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.lang.reflect.Field;

public class CustomCaptureActivity extends CaptureActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Field barcodeScannerViewField = CaptureActivity.class.getDeclaredField("barcodeScannerView");
            barcodeScannerViewField.setAccessible(true);
            DecoratedBarcodeView barcodeScannerView = (DecoratedBarcodeView) barcodeScannerViewField.get(this);
            barcodeScannerView.getViewFinder().setVisibility(View.INVISIBLE);
            barcodeScannerView.getStatusView().setVisibility(View.GONE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
