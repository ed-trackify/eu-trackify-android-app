package common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import eu.trackify.net.R;

public class ModalCtrl extends LinearLayout {

	TextView tv;

	public ModalCtrl(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.ctrl_modal, this);

		if (!this.isInEditMode()) {
			RelativeLayout ctrl_root = (RelativeLayout) findViewById(R.id.ctrl_root);
			ctrl_root.setOnTouchListener(new OnTouchListener() {
				@SuppressLint("ClickableViewAccessibility")
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					return true;
				}
			});

			tv = (TextView) findViewById(R.id.tv);
		}
	}

	public void Show() {
		this.setVisibility(View.VISIBLE);
	}

	public void Hide() {
		this.setVisibility(View.GONE);
	}

	public void SetText(String text) {
		tv.setText(text);
	}
}
