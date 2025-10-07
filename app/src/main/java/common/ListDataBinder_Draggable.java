package common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.terlici.dragndroplist.DragNDropAdapter;
import com.terlici.dragndroplist.DragNDropListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import common.ListDataBinder.BindedListType;

public class ListDataBinder_Draggable extends ArrayAdapter implements DragNDropAdapter {

	LayoutInflater inflater;
	HashMap<ShipmentWithDetail, View> itemsGenerated = new HashMap<ShipmentWithDetail, View>();
	BindedListType bindedListType;
	DragNDropListView ListView;

	private int mPosition[];
	private int mHandler;

	public List<ShipmentWithDetail> Items = new ArrayList<ShipmentWithDetail>();
	public LinkedList<String> OrderedUniqueKeys = new LinkedList<String>();

	public ListDataBinder_Draggable(BindedListType BindedListType, DragNDropListView listView, int handler) {
		super(App.Object, android.R.layout.simple_spinner_item);

		mHandler = handler;
		this.inflater = (LayoutInflater) App.Object.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.bindedListType = BindedListType;
		this.ListView = listView;
		ListView.setDragNDropAdapter(this);
	}

	private void Setup() {
		mPosition = new int[Items.size()];
		for (int i = 0; i < Items.size(); ++i)
			mPosition[i] = i;
	}

	public void Initialize(List<ShipmentWithDetail> items) {
		for (ShipmentWithDetail i : items) {
			if (!OrderedUniqueKeys.contains(i.tracking_id))
				OrderedUniqueKeys.add(i.tracking_id);
		}

		Items = items;
		itemsGenerated.clear();
		Setup();
		this.notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		return Items.size();
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
				view = ListDataBinder.GetShipmentsRowView(inflater, bindedListType, obj, false);
			}

			if (view != null) {
				view.setTag(source);
				itemsGenerated.put((ShipmentWithDetail) source, view);
			}
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "ListDataBinder_Draggable::getView(" + bindedListType + ")");
		}
		return view;
	}

	public void ScrollToItem(ShipmentWithDetail item) {
		if (Items.contains(item)) {
			int indx = Items.indexOf(item);
			if (indx >= 0)
				ListView.smoothScrollToPosition(indx);
		}
	}

	@Override
	public void onItemDrag(DragNDropListView parent, View view, int position, long id) {

	}

	@Override
	public ShipmentWithDetail getItem(int position) {
		return Items.get(mPosition[position]);
	}

	@Override
	public void onItemDrop(DragNDropListView parent, View view, int startPosition, int endPosition, long id) {

		String startUnique = getItem(startPosition).tracking_id;
		String endUnique = getItem(endPosition).tracking_id;
		int startUniqueIndex = OrderedUniqueKeys.indexOf(startUnique);
		int endUniqueIndex = OrderedUniqueKeys.indexOf(endUnique);

		int position = mPosition[startPosition];

		if (startPosition < endPosition) {
			for (int i = startPosition; i < endPosition; ++i)
				mPosition[i] = mPosition[i + 1];
			for (int i = startUniqueIndex; i < endUniqueIndex; ++i)
				OrderedUniqueKeys.set(i, OrderedUniqueKeys.get(i + 1));
		} else if (endPosition < startPosition) {
			for (int i = startPosition; i > endPosition; --i)
				mPosition[i] = mPosition[i - 1];
			for (int i = startUniqueIndex; i > endUniqueIndex; --i)
				OrderedUniqueKeys.set(i, OrderedUniqueKeys.get(i - 1));
		}

		mPosition[endPosition] = position;
		OrderedUniqueKeys.set(endUniqueIndex, startUnique);
	}

	@Override
	public int getDragHandler() {
		return mHandler;
	}
}
