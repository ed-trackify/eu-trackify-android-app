package common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import eu.trackify.net.R;

public class SignatureCtrl extends LinearLayout {
	SignatureView sv;
	EditText et_FullName, et_Pin;
	View ll_pin, tvSignHint, btnClear, btnCancel;
	boolean hasSignature = false;

	public interface ISignature {
		void onSignatureSubmit(Bitmap bitmap, String fullName, String nullablePin);
	}

	ISignature Callback;

	public SignatureCtrl(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.ctrl_signature, this);

		if (!this.isInEditMode()) {
			RelativeLayout ctrl_root = (RelativeLayout) findViewById(R.id.ctrl_root);
			ctrl_root.setOnTouchListener(new OnTouchListener() {
				@SuppressLint("ClickableViewAccessibility")
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					return true;
				}
			});

			sv = (SignatureView) findViewById(R.id.sig);
			et_FullName = (EditText) findViewById(R.id.et_FullName);
			ll_pin = findViewById(R.id.ll_pin);
			et_Pin = (EditText) findViewById(R.id.et_Pin);
			tvSignHint = findViewById(R.id.tvSignHint);
			btnClear = findViewById(R.id.btnClear);
			btnCancel = findViewById(R.id.btnCancel);
			
			// Hide hint when user starts drawing
			sv.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					if (event.getAction() == MotionEvent.ACTION_DOWN) {
						tvSignHint.setVisibility(View.GONE);
						hasSignature = true;
					}
					return false;
				}
			});
			
			// Clear signature button
			btnClear.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					sv.clearSignature();
					tvSignHint.setVisibility(View.VISIBLE);
					hasSignature = false;
				}
			});
			
			// Cancel button
			btnCancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Hide();
				}
			});

			findViewById(R.id.btnDone).setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View arg0) {
					// Validate PIN if required
					String pin = null;
					if (ll_pin.getVisibility() == View.VISIBLE) {
						pin = et_Pin.getText().toString();
						if (AppModel.IsNullOrEmpty(pin)) {
							MessageCtrl.Toast(getContext().getString(R.string.signature_error_pin_required));
							et_Pin.requestFocus();
							return;
						}
						if (pin.length() != 4) {
							MessageCtrl.Toast(getContext().getString(R.string.signature_error_pin_length));
							et_Pin.requestFocus();
							return;
						}
					}

					// Validate full name
					String fName = et_FullName.getText().toString().trim();
					if (AppModel.IsNullOrEmpty(fName)) {
						MessageCtrl.Toast(getContext().getString(R.string.signature_error_name_required));
						et_FullName.requestFocus();
						return;
					}

					// Validate signature
					if (!hasSignature) {
						MessageCtrl.Toast(getContext().getString(R.string.signature_error_signature_required));
						return;
					}

					Callback.onSignatureSubmit(sv.getImage(), fName, pin);
					Hide();
				}
			});
		}
	}

	public void Show(ShipmentWithDetail obj, ISignature callback) {
		Callback = callback;

		// Reset all fields
		et_Pin.setText("");
		et_FullName.setText("");
		sv.clearSignature();
		hasSignature = false;
		tvSignHint.setVisibility(View.VISIBLE);
		
		// Show/hide PIN field based on requirement
		ll_pin.setVisibility(obj.pin_verification == 1 ? VISIBLE : GONE);
		
		// Show the signature screen
		this.setVisibility(View.VISIBLE);
		
		// Focus on first input field
		if (obj.pin_verification == 1) {
			et_Pin.requestFocus();
		} else {
			et_FullName.requestFocus();
		}
	}

	public void Hide() {
		this.setVisibility(View.GONE);
	}
}
