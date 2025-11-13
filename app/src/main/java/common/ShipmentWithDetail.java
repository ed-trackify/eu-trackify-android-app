package common;

import java.util.ArrayList;
import java.util.List;

public class ShipmentWithDetail {

    public String shipment_id;
    public int status_id;
    public String status_name;
    public String description;
    public String description_title;

    public String receiver_cod;
    public String sender_phone;
    public String receiver_address;
    public String receiver_city;
    public String sender_name;
    public String sender_address;
    public String tracking_id;
    public String exchange_tracking_id;
    public String client_id;
    public String receiver_phone;
    public String receiver_name;
    public String receiver_country_id;
    public String instructions;

    public Double lat;
    public Double lon;

    public String sms_text;
    public int is_urgent;

    public String bg_color;
    public String txt_color;

    public String getBgColor() {
        return bg_color == null ? "#FFFFFF" : bg_color;
    }

    public String getTxtColor() {
        return txt_color == null ? "#000000" : txt_color;
    }

    public int pin_verification = 0; // "pin based verification is required for delivery status, use case is money delivery" - (1=yes or 0=no)

    public NoteItem[] notes;
    public List<NoteItem> _Notes = new ArrayList<NoteItem>();

    public void GenerateNotes() {
        if (notes != null)
            for (NoteItem noteItem : notes) {
                _Notes.add(noteItem);
            }
    }

    public PictureItem[] images;
    public List<PictureItem> _Images = new ArrayList<PictureItem>();

    public void GeneratePictures() {
        if (images != null)
            for (PictureItem noteItem : images) {
                _Images.add(noteItem);
            }
    }

    public boolean hasPendingSync;

    public ReturnShipment returnShipment;
}
