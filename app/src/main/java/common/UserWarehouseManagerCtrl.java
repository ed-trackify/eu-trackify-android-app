package common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import eu.trackify.net.R;

import common.Communicator.IServerResponse;

public class UserWarehouseManagerCtrl extends LinearLayout {

	Button btnStockIn, btnStockOut, btnScan, btnCourier;
	TextView tv_Scanned;
	EditText et_Quantity;

	String Scanned = null;

	public UserWarehouseManagerCtrl(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = inflater.inflate(R.layout.ctrl_warehouse_mang_scan, this);

		if (!this.isInEditMode()) {
			tv_Scanned = (TextView) v.findViewById(R.id.tv_Scanned);
			et_Quantity = (EditText) v.findViewById(R.id.et_Quantity);

			btnStockIn = (Button) v.findViewById(R.id.btnStockIn);
			btnStockIn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					SendKey(true, null);
				}
			});

			btnStockOut = (Button) v.findViewById(R.id.btnStockOut);
			btnStockOut.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					SendKey(false, null);
				}
			});

			btnCourier = (Button) v.findViewById(R.id.btnCourier);
			btnCourier.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					SendKey(false, 6);
				}
			});

			Button btnScan = (Button) v.findViewById(R.id.btnScan);
			btnScan.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					App.Object.ShowScanner();
				}
			});
		}
	}

	public void SetScannedCode(String code) {
		Scanned = code;
		tv_Scanned.setText(Scanned == null ? "N/A" : Scanned);
	}

	private void SendKey(boolean isStockIn, Integer statusId) {
		try {

			if (AppModel.IsNullOrEmpty(Scanned)) {
				MessageCtrl.Toast("Please scan barcode first");
				return;
			}

			String quantityTxt = et_Quantity.getText().toString();
			if (AppModel.IsNullOrEmpty(quantityTxt)) {
				MessageCtrl.Toast("Please enter quantity");
				return;
			}

			int quantity = 0;
			try {
				quantity = Integer.parseInt(quantityTxt);
			} catch (Exception ex) {
				MessageCtrl.Toast("Please enter valid quantity");
				return;
			}

			Communicator.SendWarehouseManager(Scanned, isStockIn, quantity, statusId, new IServerResponse() {
				@Override
				public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
					App.Object.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							try {
								if (success) {
									if (objs != null) {
										KeyRef u = (KeyRef) objs[0];
										MessageCtrl.Toast(u.response_txt);
										// if (u.updated.equalsIgnoreCase("true")) {
										// MessageCtrl.Toast("Scanned key successfully sent");
										// } else
										// MessageCtrl.Toast("Scanned key sending failed, Please try again");
									} else
										MessageCtrl.Toast("Scanned key sending failed, Please try again");
								} else if (!AppModel.IsNullOrEmpty(messageToShow))
									MessageCtrl.Toast(messageToShow);
							} catch (Exception ex) {
								AppModel.ApplicationError(ex, "ScanCtrl::SendKey");
							}
						}
					});
				}
			});

		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "ScanCtrl::SendKey");
		}
	}
}