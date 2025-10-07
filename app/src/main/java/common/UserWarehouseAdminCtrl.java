package common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import eu.trackify.net.R;

import common.Communicator.IServerResponse;

public class UserWarehouseAdminCtrl extends LinearLayout {

	Button btnStockOut, btnScan;
	TextView tv_Scanned;

	String Scanned = null;

	public UserWarehouseAdminCtrl(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = inflater.inflate(R.layout.ctrl_warehouse_admin_scan, this);

		if (!this.isInEditMode()) {
			tv_Scanned = (TextView) v.findViewById(R.id.tv_Scanned);

			btnStockOut = (Button) v.findViewById(R.id.btnStockOut);
			btnStockOut.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					SendKey();
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

	private void SendKey() {
		try {

			if (AppModel.IsNullOrEmpty(Scanned)) {
				MessageCtrl.Toast("Please scan barcode first");
				return;
			}

			Communicator.SendWarehouseAdmin(Scanned, new IServerResponse() {
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