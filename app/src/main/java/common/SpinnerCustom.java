package common;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class SpinnerCustom extends Spinner {

	public List<Object> Items = new ArrayList<Object>();
	String VariableToDisplay = null;
	int ForegroundColor = Color.BLACK;

	public SpinnerCustom(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@SuppressWarnings("unchecked")
	public void Initialize(Object items, Object selectedItem) {
		Items = (List<Object>) items;
		SpinnerAdapter adapter = new SpinnerAdapter(getContext(), android.R.layout.simple_spinner_item, Items);
		this.setAdapter(adapter);
		SelectItem(selectedItem);
	}

	public void SetSettings(String variableToDisplay, Integer colorIdForeground) {
		if (variableToDisplay != null)
			VariableToDisplay = variableToDisplay;
		if (colorIdForeground != null)
			ForegroundColor = AppModel.Object.GetColorResource(colorIdForeground);
	}

	public void SelectItem(final Object item) {
		if (item != null) {
			// ANDROID_COMPATIBILITY
			if (Build.VERSION.SDK_INT < VERSION_CODES.HONEYCOMB)
				this.post(new Runnable() {
					@Override
					public void run() {
						setSelection(Items.indexOf(item));
					}
				});
			else
				setSelection(Items.indexOf(item));
		}
	}

	public Object GetListAsObject() {
		return Items;
	}

	private class SpinnerAdapter extends ArrayAdapter<Object> {
		Context context;
		List<Object> items = new ArrayList<Object>();

		public SpinnerAdapter(final Context context, final int textViewResourceId, final List<Object> objects) {
			super(context, textViewResourceId, objects);
			this.items = objects;
			this.context = context;
		}

		@Override
		public View getDropDownView(int position, View convertView, ViewGroup parent) {

			if (convertView == null) {
				// LayoutInflater inflater = LayoutInflater.from(context);
				// convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
				// convertView = inflater.inflate(R.layout.textview, parent, false);
			}

			Object item = items.get(position);
			// TextView tv = (TextView) convertView.findViewById(android.R.id.text1);
			TextView tv = new TextView(context); // (TextView) convertView.findViewById(R.id.tv1);

			try {
				tv.setText(VariableToDisplay == null ? item.toString() : item.getClass().getDeclaredField(VariableToDisplay).get(item).toString());
			} catch (Exception e) {
				e.printStackTrace();
			}

			tv.setTextSize(20F);
			tv.setPadding(6, 6, 6, 6);
			return tv;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				LayoutInflater inflater = LayoutInflater.from(context);
				convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
			}

			// android.R.id.text1 is default text view in resource of the android.
			// android.R.layout.simple_spinner_item is default layout in resources of android.

			Object item = items.get(position);
			TextView tv = (TextView) convertView.findViewById(android.R.id.text1);

			try {
				tv.setText(VariableToDisplay == null ? item.toString() : item.getClass().getDeclaredField(VariableToDisplay).get(item).toString());
			} catch (Exception e) {
				e.printStackTrace();
			}

			tv.setPadding(6, 6, 6, 6);
			tv.setTextSize(25F);
			tv.setTextColor(ForegroundColor);
			return convertView;
		}
	}
}
