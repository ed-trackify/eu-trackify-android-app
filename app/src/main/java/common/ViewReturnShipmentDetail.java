package common;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import eu.trackify.net.R;

import common.Communicator.IServerResponse;
import common.CountingInputStreamEntity.IUploadListener;
import common.SignatureCtrl.ISignature;
import common.UserDistributorShipmentsFragment.ShipmentsType;

public class ViewReturnShipmentDetail extends LinearLayout {

    public View btnPrint, btnBack;
    TextView sid, tid, status, sendName, sendPh, sendAddr, recName, recPh, recAddr, recCod, specInst;

    ShipmentWithDetail Current;

    public ViewReturnShipmentDetail(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.ctrl_view_return_shipmentdetail, this);

        if (!this.isInEditMode()) {

            sid = (TextView) findViewById(R.id.sid);
            tid = (TextView) findViewById(R.id.tid);
            status = (TextView) findViewById(R.id.status);
            sendName = (TextView) findViewById(R.id.sendName);
            sendPh = (TextView) findViewById(R.id.sendPh);
            sendAddr = (TextView) findViewById(R.id.sendAddr);
            recName = (TextView) findViewById(R.id.recName);
            recPh = (TextView) findViewById(R.id.recPh);
            recAddr = (TextView) findViewById(R.id.recAddr);
            recCod = (TextView) findViewById(R.id.recCod);
            specInst = (TextView) findViewById(R.id.specInst);

            btnPrint = findViewById(R.id.btnPrint);
            // Hide print button if feature is disabled
            if (!SettingsCtrl.isPrinterEnabled(getContext())) {
                btnPrint.setVisibility(View.GONE);
            } else {
                btnPrint.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (PrinterManager.Instance != null) {
                            PrinterManager.Instance.printReturnShipment(Current.returnShipment);
                        } else if (Printer.Instance != null) {
                            Printer.Instance.printReturnShipment(Current.returnShipment);
                        }
                    }
                });
            }

            btnBack = findViewById(R.id.btnBack);
            btnBack.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    App.Object.viewReturnShipmentDetail.setVisibility(GONE);
                }
            });
        }
    }

    public void Initialize(ShipmentWithDetail current) {
        Current = current;

        if (Current != null && Current.returnShipment != null) {
            ReturnShipment rs = Current.returnShipment;

            sid.setText(Current.shipment_id);
            status.setText("Return Shipment");
            tid.setText(rs.tracking_id);
            sendName.setText(rs.sender_name);
            sendPh.setText(rs.sender_phone);
            sendAddr.setText(rs.sender_address);
            recName.setText(rs.receiver_name);
            recPh.setText(rs.receiver_phone);
            recAddr.setText(rs.receiver_address);
            recCod.setText(rs.cod);
            specInst.setText(rs.special_instructions);
        }

        App.Object.viewReturnShipmentDetail.setVisibility(VISIBLE);
    }
}