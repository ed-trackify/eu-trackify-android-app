package common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import eu.trackify.net.R;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ListDataBinder<E> extends ArrayAdapter<E> {

	public enum BindedListType {
		MyShipments, ReconcileShipments, Note, Pictures
	}

	LayoutInflater inflater;
	HashMap<E, View> itemsGenerated = new HashMap<E, View>();
	BindedListType bindedListType;
	AbsListView ListView;
	View selectedView;

	public List<E> Items = new ArrayList<E>();

	public ListDataBinder(BindedListType BindedListType, AbsListView listView) {
		super(App.Object, android.R.layout.simple_spinner_item);

		this.inflater = (LayoutInflater) App.Object.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.bindedListType = BindedListType;
		this.ListView = listView;
		// ANDROID_COMPATIBILITY
		if (ListView instanceof GridView)
			((GridView) ListView).setAdapter(this);
		else if (ListView instanceof android.widget.ListView)
			((android.widget.ListView) ListView).setAdapter(this);
	}

	public void Initialize(List<E> items) {
		Items = items;
		itemsGenerated.clear();
		this.notifyDataSetChanged();
	}

	public void Clear() {
		Items.clear();
		itemsGenerated.clear();
		this.notifyDataSetChanged();
	}

	public void Add(E item) {
		Items.add(item);
		this.notifyDataSetChanged();
	}

	public void Remove(E item) {
		Items.remove(item);
		this.notifyDataSetChanged();
	}

	public void Insert(E item, int position) {
		Items.add(position, item);
		itemsGenerated.clear();
		this.notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		return Items.size();
	}

	@Override
	public E getItem(int position) {
		return Items.get(position);
	}

	@SuppressLint("InflateParams")
	@SuppressWarnings("unchecked")
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		View view = convertView;
		Object source = getItem(position);
		if (itemsGenerated.containsKey(source))
			return itemsGenerated.get(source);

		try {

			if (bindedListType == BindedListType.MyShipments || bindedListType == BindedListType.ReconcileShipments) {
				final ShipmentWithDetail obj = (ShipmentWithDetail) source;
				view = GetShipmentsRowView(inflater, bindedListType, obj, false);
			}

			if (bindedListType == BindedListType.Note) {
				final NoteItem obj = (NoteItem) source;

				view = inflater.inflate(obj.isMyNote() ? R.layout.row_note_my : R.layout.row_note_other, null);

				TextView tvLeft = (TextView) view.findViewById(R.id.tvLeft);
				TextView tvRight = (TextView) view.findViewById(R.id.tvRight);
				TextView tvComm = (TextView) view.findViewById(R.id.tvComm);
				tvLeft.setText(obj.isMyNote() ? "Me" : obj.driver_name);
				tvRight.setText(obj.comment_timestamp);
				tvComm.setText(obj.comment);
			}

			if (bindedListType == BindedListType.Pictures) {
				final PictureItem obj = (PictureItem) source;

				view = inflater.inflate(R.layout.row_picture, null);

				ImageView iv = (ImageView) view.findViewById(R.id.iv);
				Picasso.get().load(obj.url).into(iv);

				TextView tvComm = (TextView) view.findViewById(R.id.tv);
				tvComm.setText(obj.description);
			}

			if (view != null) {
				view.setTag(source);
				itemsGenerated.put((E) source, view);
			}
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "ListDataBinder::getView(" + bindedListType + ")");
		}
		return view;
	}

	// Search query for text highlighting
	public static String currentSearchQuery = "";

	public static View GetShipmentsRowView(LayoutInflater _inflater, BindedListType listType, final ShipmentWithDetail obj, boolean isDraggable) {
		View view = _inflater.inflate(R.layout.row_shipment_improved, null);

		// Store the object in the view tag for click handling
		view.setTag(obj);
		
		// Apply background color to the entire card
		View cardContainer = view.findViewById(R.id.cardContainer);
		try {
			// Override background color for ReconcileShipments (CoD tab) to delivered green
			if (listType == BindedListType.ReconcileShipments) {
				cardContainer.setBackgroundColor(Color.parseColor("#81e57b"));
			} else {
				cardContainer.setBackgroundColor(Color.parseColor(obj.getBgColor()));
			}
		} catch (Exception e) {
			// Fallback to white if parsing fails
			cardContainer.setBackgroundColor(Color.WHITE);
		}

		view.findViewById(R.id.ivSync).setVisibility(obj.hasPendingSync ? View.VISIBLE : View.GONE);
		view.findViewById(R.id.ivHandler).setVisibility(isDraggable ? View.VISIBLE : View.GONE);

		// Handle call button
		View btnCall = view.findViewById(R.id.btnCall);
		btnCall.setVisibility(AppModel.IsNullOrEmpty(obj.receiver_phone) ? View.GONE : View.VISIBLE);
		btnCall.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				try {
					Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + obj.receiver_phone));
					App.Object.startActivity(intent);
				} catch (Exception ex) {
					MessageCtrl.Toast("Invalid phone number");
					AppModel.ApplicationError(ex, "ScanCtrl::recPh");
				}
			}
		});
		
		// Handle notes count badge
		View notesIndicator = view.findViewById(R.id.notesIndicator);
		TextView tvNoteCount = (TextView) view.findViewById(R.id.tvNoteCount);
		if (obj._Notes != null && obj._Notes.size() > 0) {
			notesIndicator.setVisibility(View.VISIBLE);
			tvNoteCount.setText(String.valueOf(obj._Notes.size()));
		} else {
			notesIndicator.setVisibility(View.GONE);
		}
		
		// Handle pictures count badge
		View picturesIndicator = view.findViewById(R.id.picturesIndicator);
		TextView tvPictureCount = (TextView) view.findViewById(R.id.tvPictureCount);
		if (obj._Images != null && obj._Images.size() > 0) {
			picturesIndicator.setVisibility(View.VISIBLE);
			tvPictureCount.setText(String.valueOf(obj._Images.size()));
		} else {
			picturesIndicator.setVisibility(View.GONE);
		}

		// Set status badge - keep its own color for contrast
		TextView tvStatus = (TextView) view.findViewById(R.id.tvStatus);
		tvStatus.setText(obj.status_name);
		// Status badge keeps its default background from drawable
		
		// Set tracking ID (without # prefix as some tracking IDs contain #)
		// Add exchange tracking ID with exchange symbol if present
		TextView tvTrackingId = (TextView) view.findViewById(R.id.tvTrackingId);
		String displayTrackingId = obj.tracking_id;
		if (!AppModel.IsNullOrEmpty(obj.exchange_tracking_id)) {
			displayTrackingId = displayTrackingId + " ↔ " + obj.exchange_tracking_id;
		}
		tvTrackingId.setText(TextHighlighter.highlight(displayTrackingId, currentSearchQuery));

		// Set receiver phone number (top right)
		TextView tvReceiverPhone = (TextView) view.findViewById(R.id.tvReceiverPhone);
		if (!AppModel.IsNullOrEmpty(obj.receiver_phone)) {
			tvReceiverPhone.setVisibility(View.VISIBLE);
			tvReceiverPhone.setText(TextHighlighter.highlight(obj.receiver_phone, currentSearchQuery));
		} else {
			tvReceiverPhone.setVisibility(View.GONE);
		}
		
		// Set customer name
		TextView tvCustomerName = (TextView) view.findViewById(R.id.tvCustomerName);
		tvCustomerName.setText(TextHighlighter.highlight(obj.receiver_name, currentSearchQuery));
		
		// Set address with city
		TextView tvAddress = (TextView) view.findViewById(R.id.tvAddress);
		String fullAddress = obj.receiver_address;
		if (!AppModel.IsNullOrEmpty(obj.receiver_city)) {
			fullAddress = fullAddress + ", " + obj.receiver_city;
		}
		tvAddress.setText(TextHighlighter.highlight(fullAddress, currentSearchQuery));
		
		// Set COD amount if applicable - format with MKD suffix
		View llCod = view.findViewById(R.id.llCod);
		TextView tvCodAmount = (TextView) view.findViewById(R.id.tvCodAmount);
		if (!AppModel.IsNullOrEmpty(obj.receiver_cod)) {
			llCod.setVisibility(View.VISIBLE);
			tvCodAmount.setText(obj.receiver_cod + " MKD");
		} else {
			llCod.setVisibility(View.GONE);
		}

		// For ReconcileShipments (CoD tab), set all text to black for better visibility on green background
		if (listType == BindedListType.ReconcileShipments) {
			tvTrackingId.setTextColor(Color.BLACK);
			tvReceiverPhone.setTextColor(Color.BLACK);
			tvCustomerName.setTextColor(Color.BLACK);
			tvAddress.setTextColor(Color.BLACK);
			tvStatus.setTextColor(Color.BLACK);
			tvCodAmount.setTextColor(Color.BLACK);
		}

		// Don't set click listener on cardContainer - let the ListView handle it
		// This ensures the onItemClick in the fragment is triggered properly

		return view;
	}

	public void ScrollToItem(E item) {
		if (Items.contains(item)) {
			int indx = Items.indexOf(item);
			if (indx >= 0)
				ListView.smoothScrollToPosition(indx);
		}
	}
}
