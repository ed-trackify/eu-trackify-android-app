package common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import eu.trackify.net.R;

public class SendProblemCommentsCtrl extends LinearLayout {

	String code;
	EditText et_Comments;
	Button btnSubmit, btnCancel;

	public SendProblemCommentsCtrl(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = inflater.inflate(R.layout.popup_send_problem_comments, this);

		if (!this.isInEditMode()) {
			et_Comments = (EditText) v.findViewById(R.id.et_Comments);

			btnSubmit = (Button) v.findViewById(R.id.btnSubmit);
			btnCancel = (Button) v.findViewById(R.id.btnCancel);

			btnSubmit.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					try {
						final String txt = et_Comments.getText().toString();

						if (AppModel.IsNullOrEmpty(txt)) {
							MessageCtrl.Toast("Please describe the problem");
							return;
						}

						App.Object.userDistributorMyShipmentsFragment.OnCommentsSubmit(code, txt);
						btnCancel.performClick();
					} catch (Exception ex) {
						AppModel.ApplicationError(ex, "SendCommentsCtrl::btnSave");
					}
				}
			});

			btnCancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					try {
						SetVisibility(code, false);
					} catch (Exception ex) {
						AppModel.ApplicationError(ex, "SendCommentsCtrl::btnCancel");
					}
				}
			});

			RelativeLayout rl_popupRootView = (RelativeLayout) findViewById(R.id.rl_popupRootView);
			rl_popupRootView.setOnTouchListener(new OnTouchListener() {
				@SuppressLint("ClickableViewAccessibility")
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					return true;
				}
			});

		}
	}

	public void SetVisibility(String code, boolean makeVisible) {
		this.code = code;
		et_Comments.setText("");
		this.setVisibility(makeVisible ? View.VISIBLE : View.GONE);
		if (makeVisible)
			et_Comments.requestFocus();
	}
}
