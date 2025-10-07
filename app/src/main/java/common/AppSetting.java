package common;

import java.util.ArrayList;
import java.util.List;

public class AppSetting {
    public Integer print_label;
    public Integer routing;

    public boolean showPrintLabel(){
        return print_label != null && print_label == 1;
    }

    public boolean showRouting(){
        return routing != null && routing == 1;
    }
}
