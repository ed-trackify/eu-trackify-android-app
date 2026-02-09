package common;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import eu.trackify.net.R;
import com.google.gson.Gson;
import com.terlici.dragndroplist.DragNDropListView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import common.Communicator.IServerResponse;
import common.ListDataBinder.BindedListType;
import common.UserDistributorShipmentsFragment.ShipmentsType;

public class Draggable_UserDistributorShipmentsFragment extends Fragment {

	boolean isDeliveryScan;
	boolean isStatusCheckScan = false;
	boolean isReturnReceivedScan = false;

	DragNDropListView lv_results;
	View emptyStateContainer;
	ListDataBinder_Draggable binder;
	EditText et_Search;
	CheckBox chkMultiscan;

	// Status filter chips
	TextView filterStatusInDelivery;
	TextView filterStatusPickedUp;
	TextView filterStatusProblematic;

	// Active status filters (1=In Delivery, 4=Picked Up, 3=Problematic)
	List<Integer> activeStatusFilters = new ArrayList<>();

	ShipmentWithDetail SELECTED;
	public List<ShipmentWithDetail> ITEMS = new ArrayList<ShipmentWithDetail>();

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View v = inflater.inflate(R.layout.ctrl_draggable_distributor_user_shipments, null);

		chkMultiscan = v.findViewById(R.id.chkMultiscan);
		et_Search = (EditText) v.findViewById(R.id.et_Search);
		lv_results = (DragNDropListView) v.findViewById(R.id.lv_results);
		emptyStateContainer = v.findViewById(R.id.emptyStateContainer);
		binder = new ListDataBinder_Draggable(BindedListType.MyShipments, lv_results, R.id.ivHandler);

		// Set Return Received button
		Button btnReturnReceived = v.findViewById(R.id.btnReturnReceived);
		btnReturnReceived.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				isStatusCheckScan = false;
				isDeliveryScan = false;
				isReturnReceivedScan = true;
				App.Object.ShowScanner();
			}
		});

		// Tint the drawable icon white for better visibility on red background
		Drawable[] drawables = btnReturnReceived.getCompoundDrawables();
		if (drawables[0] != null) {
			drawables[0].setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
		}

		// Tint Packed button icon dark to match text color on light background
		Button btnScanPick = v.findViewById(R.id.btnScanPick);
		Drawable[] packedDrawables = btnScanPick.getCompoundDrawables();
		if (packedDrawables[0] != null) {
			packedDrawables[0].setColorFilter(Color.parseColor("#1A243C"), PorterDuff.Mode.SRC_IN);
		}

		// Tint Check button icon white
		Button btnStatusCheck = v.findViewById(R.id.btnStatusCheck);
		Drawable[] checkDrawables = btnStatusCheck.getCompoundDrawables();
		if (checkDrawables[0] != null) {
			checkDrawables[0].setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
		}

		btnScanPick.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				isStatusCheckScan = false;
				isDeliveryScan = false;
				App.Object.ShowScanner();
			}
		});

		// Status Check button - scans without changing status
		v.findViewById(R.id.btnStatusCheck).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				isStatusCheckScan = true;
				App.Object.ShowScanner();
			}
		});

		// Initialize status filter chips
		filterStatusInDelivery = v.findViewById(R.id.filterStatusInDelivery);
		filterStatusPickedUp = v.findViewById(R.id.filterStatusPickedUp);
		filterStatusProblematic = v.findViewById(R.id.filterStatusProblematic);

		// Status: 1=In Delivery, 4=Picked Up, 3=Problematic
		filterStatusInDelivery.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				toggleStatusFilter(1, filterStatusInDelivery);
			}
		});

		filterStatusPickedUp.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				toggleStatusFilter(4, filterStatusPickedUp);
			}
		});

		filterStatusProblematic.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				toggleStatusFilter(3, filterStatusProblematic);
			}
		});

		lv_results.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				SELECTED = (ShipmentWithDetail) view.getTag();
				InitializeSelectedItem();
			}
		});

		et_Search.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
				ApplyFilter();
			}

			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {

			}

			@Override
			public void afterTextChanged(Editable arg0) {

			}
		});

		Load();

		return v;
	}

	private void InitializeSelectedItem() {
		if (App.Object.userDistributorShipmentDetailTabCtrl.Show(ShipmentsType.MyShipments)) {
			App.Object.userDistributorShipmentDetail.Initialize();
			App.Object.userDistributorNotesFragment.Initialize();
			App.Object.userDistributorPicturesFragment.Initialize();
			App.Object.userDistributorShipmentDetailTabCtrl.Initialize();
		}
	}
	
	public void RefreshSelectedShipment() {
		// Reload shipments from server to get updated data including new pictures
		Load();
	}

	public void Load() {
		App.SetLoading(true);

		App.Object.runOnUiThread(new Runnable() {
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				try {
					Communicator.LoadShipmentsWithDetails(ShipmentsType.MyShipments, new IServerResponse() {
						@Override
						public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
							App.Object.runOnUiThread(new Runnable() {
								@SuppressWarnings("unchecked")
								@Override
								public void run() {
									try {
										if (success) {
											if (objs != null) {
												ITEMS.clear();
												ShipmentResponse resp = (ShipmentResponse) objs[0];

												AppModel.Object.SaveVariable(AppModel.MY_SHIPMENTS_CACHE_KEY, new Gson().toJson(resp));

												// Update tab title with shipment count
												int shipmentCount = resp.shipments.size();
												String tabTitle = String.format(getContext().getString(R.string.tab_title_todo_count), shipmentCount);
												App.Object.userDistributorTabCtrl.ChangeTabTitle(tabTitle, App.Object.userDistributorMyShipmentsFragment);

												if (resp.shipments.size() == 0) {
													ApplyFilter();
												} else {
													for (ShipmentWithDetail sd : resp.shipments) {
														sd.GenerateNotes();
														sd.GeneratePictures();
														ITEMS.add(sd);

														if (SELECTED != null && SELECTED.shipment_id.equals(sd.shipment_id))
															SELECTED = sd;
													}
													updateStatusCounts();
													ApplyFilter();
													App.Object.routingCtrl.CalculateRoute(ITEMS);

													if (App.Object.userDistributorShipmentDetailTabCtrl.getVisibility() == View.VISIBLE && SELECTED != null)
														InitializeSelectedItem();
												}
												App.SetLoading(false);
											} else {
												MessageCtrl.Toast("Loading failed, Please try again");
												App.SetLoading(false);
											}
										} else if (!AppModel.IsNullOrEmpty(messageToShow)) {
											MessageCtrl.Toast(messageToShow);

											String sJson = AppModel.Object.GetSettingVariable(AppModel.MY_SHIPMENTS_CACHE_KEY);
											ShipmentResponse cached = new Gson().fromJson(sJson, ShipmentResponse.class);
											ITEMS.clear();

											// Update tab title with shipment count
											int shipmentCount = cached.shipments.size();
											String tabTitle = String.format(getContext().getString(R.string.tab_title_todo_count), shipmentCount);
											App.Object.userDistributorTabCtrl.ChangeTabTitle(tabTitle, App.Object.userDistributorMyShipmentsFragment);

											for (ShipmentWithDetail sd : cached.shipments) {
												sd.GenerateNotes();
												ITEMS.add(sd);
											}
											updateStatusCounts();
											ApplyFilter();
											App.SetLoading(false);
										}
									} catch (Exception ex) {
										AppModel.ApplicationError(ex, "ScanCtrl::LoadShipments");
										App.SetLoading(false);
									}
								}
							});
						}
					});

				} catch (Exception ex) {
					AppModel.ApplicationError(ex, "ScanCtrl::Load");
					App.SetLoading(false);
				}
			}
		});
	}

	private void updateStatusCounts() {
		// Count shipments by status
		int inDeliveryCount = 0;
		int pickedUpCount = 0;
		int problematicCount = 0;

		for (ShipmentWithDetail s : ITEMS) {
			if (s.status_id == 1) inDeliveryCount++;
			else if (s.status_id == 4) pickedUpCount++;
			else if (s.status_id == 3) problematicCount++;
		}

		// Update chip text with counts
		if (filterStatusInDelivery != null) {
			String inDeliveryText = String.format(getContext().getString(R.string.filter_in_delivery), inDeliveryCount);
			filterStatusInDelivery.setText(inDeliveryText);
		}
		if (filterStatusPickedUp != null) {
			String pickedUpText = String.format(getContext().getString(R.string.filter_picked_up), pickedUpCount);
			filterStatusPickedUp.setText(pickedUpText);
		}
		if (filterStatusProblematic != null) {
			String problematicText = String.format(getContext().getString(R.string.filter_problematic), problematicCount);
			filterStatusProblematic.setText(problematicText);
		}
	}

	private void toggleStatusFilter(int statusId, TextView chip) {
		if (activeStatusFilters.contains(statusId)) {
			// Deselect - use normal background with thin border
			activeStatusFilters.remove(Integer.valueOf(statusId));
			if (statusId == 1) {
				chip.setBackgroundResource(R.drawable.chip_in_delivery);
			} else if (statusId == 4) {
				chip.setBackgroundResource(R.drawable.chip_picked_up);
			} else if (statusId == 3) {
				chip.setBackgroundResource(R.drawable.chip_problematic);
			}
		} else {
			// Select - use selected background with thick black border
			activeStatusFilters.add(statusId);
			if (statusId == 1) {
				chip.setBackgroundResource(R.drawable.chip_in_delivery_selected);
			} else if (statusId == 4) {
				chip.setBackgroundResource(R.drawable.chip_picked_up_selected);
			} else if (statusId == 3) {
				chip.setBackgroundResource(R.drawable.chip_problematic_selected);
			}
		}
		ApplyFilter();
	}

	public void ApplyFilter() {
		String txt = et_Search.getText().toString();
		// Set the search query for text highlighting in ListDataBinder
		ListDataBinder.currentSearchQuery = AppModel.IsNullOrEmpty(txt) ? "" : txt.toLowerCase();

		// First apply text search filter
		List<ShipmentWithDetail> textFilteredItems = AppModel.IsNullOrEmpty(txt) ? ITEMS : GetSearched(txt.toLowerCase());

		// Then apply status filter
		List<ShipmentWithDetail> allItems = textFilteredItems;
		if (!activeStatusFilters.isEmpty()) {
			allItems = new ArrayList<>();
			for (ShipmentWithDetail s : textFilteredItems) {
				if (activeStatusFilters.contains(s.status_id)) {
					allItems.add(s);
				}
			}
		}

		LinkedList<ShipmentWithDetail> orderedItems = new LinkedList<ShipmentWithDetail>();
		for (String uk : binder.OrderedUniqueKeys) {
			for (ShipmentWithDetail s : allItems) {
				if (s.tracking_id.equalsIgnoreCase(uk)) {
					orderedItems.add(s);
					break;
				}
			}
		}
		for (ShipmentWithDetail s : allItems) {
			if (!binder.OrderedUniqueKeys.contains(s.tracking_id))
				orderedItems.add(s);
		}

		binder.Initialize(orderedItems);

		// Toggle empty state visibility
		if (emptyStateContainer != null) {
			if (orderedItems.isEmpty()) {
				emptyStateContainer.setVisibility(View.VISIBLE);
				lv_results.setVisibility(View.GONE);
			} else {
				emptyStateContainer.setVisibility(View.GONE);
				lv_results.setVisibility(View.VISIBLE);
			}
		}
	}

	private List<ShipmentWithDetail> GetSearched(String txt) {
		List<ShipmentWithDetail> matched = new ArrayList<ShipmentWithDetail>();
		for (ShipmentWithDetail s : ITEMS) {
			// Search in: tracking ID, description, receiver name, receiver phone, receiver address
			boolean matches = false;

			if (s.shipment_id != null && s.shipment_id.toLowerCase().contains(txt)) matches = true;
			if (s.description != null && s.description.toLowerCase().contains(txt)) matches = true;
			if (s.description_title != null && s.description_title.toLowerCase().contains(txt)) matches = true;
			if (s.tracking_id != null && s.tracking_id.toLowerCase().contains(txt)) matches = true;
			if (s.receiver_name != null && s.receiver_name.toLowerCase().contains(txt)) matches = true;
			if (s.receiver_phone != null && s.receiver_phone.toLowerCase().contains(txt)) matches = true;
			if (s.receiver_address != null && s.receiver_address.toLowerCase().contains(txt)) matches = true;

			if (matches) {
				matched.add(s);
			}
		}
		return matched;
	}

	public void SetScannedCode(String code) {
		// Scanned = code;
		if (code != null) {
			// Check if this is a status check scan - pass raw code for multi-candidate lookup
			if (isStatusCheckScan) {
				isStatusCheckScan = false; // Reset the flag
				if (App.Object.statusCheckCtrl != null) {
					App.Object.statusCheckCtrl.show(code);
				}
				KeyRef.PlayBeep();
				return;
			}

			// Check if this is a return received scan - pass raw code for multi-candidate lookup
			if (isReturnReceivedScan) {
				isReturnReceivedScan = false; // Reset the flag
				if (App.Object.returnReceivedCtrl != null) {
					App.Object.returnReceivedCtrl.show(code);
				}
				KeyRef.PlayBeep();
				return;
			}

			// Parse barcode for normal Packed/Delivery scans
			// Handles GS1/ANSI shipping labels, URL QR codes, and plain barcodes
			String parsedCode = BarcodeParser.extractTrackingNumber(code);
			AppModel.ApplicationError(null, "SCAN: Received barcode: " + parsedCode + " (raw=" + code.length() + " chars)");
			AppModel.ApplicationError(null, "SCAN: isDeliveryScan = " + isDeliveryScan);

			boolean multiScanEnabled = chkMultiscan.isChecked();
			if(multiScanEnabled)
				App.Object.ShowScanner();

			ShipmentWithDetail obj = null;
			for (ShipmentWithDetail s : ITEMS) {
				if (s.tracking_id.equalsIgnoreCase(parsedCode)) {
					obj = s;
					break;
				}
			}

			if (isDeliveryScan) {
				// Always send the "In Delivery" request to trigger SMS check
				// SMS will only be sent if not already sent today (handled in SMSHelper)
				AppModel.ApplicationError(null, "SCAN: Sending IN DELIVERY for code: " + parsedCode);
				SendKey(parsedCode,"prezemena", 1, false);

				// Also open details if shipment already exists and is in delivery
				if (obj != null && obj.status_id == 1) {
					SELECTED = obj;
					InitializeSelectedItem();
				}
			} else {
				AppModel.ApplicationError(null, "SCAN: Sending PACKED for code: " + parsedCode);
				SendKey(parsedCode,"prezemena", 14, false);
			}

			KeyRef.PlayBeep();
		}
	}

	public void OnCommentsSubmit(String code, String comments) {
		SubmitRequest(code, "odbiena", 3, comments);
	}

	/**
	 * Trigger a status check scan from outside the fragment (e.g., from StatusCheckCtrl)
	 */
	public void triggerStatusCheckScan() {
		isStatusCheckScan = true;
		App.Object.ShowScanner();
	}

	/**
	 * Trigger a return received scan from outside the fragment (e.g., from ReturnReceivedCtrl)
	 */
	public void triggerReturnReceivedScan() {
		isReturnReceivedScan = true;
		App.Object.ShowScanner();
	}
	
	private void sendSMSForShipment(final String scannedCode) {
		// Try to send SMS immediately, only queue if it fails
		AppModel.ApplicationError(null, "=== SMS SEND START ===");
		AppModel.ApplicationError(null, "SMS: Attempting immediate send for code: " + scannedCode);
		
		// Create SMS data - START WITH THE SCANNED CODE
		SMSHelper.SMSData smsData = new SMSHelper.SMSData();
		
		// THIS IS THE ONLY PLACE WE SET TRACKING ID - DIRECTLY FROM SCAN
		smsData.trackingId = scannedCode;
		smsData.shipmentId = scannedCode; // Use same for shipment ID
		smsData.statusId = 1; // In Delivery
		smsData.driverName = App.CurrentUser != null ? App.CurrentUser.user : "";
		
		AppModel.ApplicationError(null, "SMS: Tracking ID set to: " + smsData.trackingId);
		
		// Now try to find receiver details - but NEVER change the tracking ID
		boolean foundDetails = false;
		
		// First check current list
		synchronized (ITEMS) {
			for (ShipmentWithDetail s : ITEMS) {
				if (s.tracking_id != null && s.tracking_id.equalsIgnoreCase(scannedCode)) {
					// Found it - use ONLY receiver details
					smsData.receiverPhone = s.receiver_phone;
					smsData.receiverName = s.receiver_name;
					smsData.receiverAddress = s.receiver_address;
					smsData.senderName = s.sender_name;
					try {
						smsData.receiverCod = Double.parseDouble(s.receiver_cod);
						AppModel.ApplicationError(null, "SMS: COD for " + scannedCode + " = " + smsData.receiverCod + " (from: " + s.receiver_cod + ")");
					} catch (Exception e) {
						smsData.receiverCod = 0;
						AppModel.ApplicationError(null, "SMS: COD parse error for " + scannedCode + ", defaulting to 0");
					}
					// Don't use server sms_text for scan - use template instead
					// smsData.smsText = s.sms_text;
					foundDetails = true;
					AppModel.ApplicationError(null, "SMS: Found details in current list for: " + scannedCode);
					AppModel.ApplicationError(null, "SMS: Phone=" + smsData.receiverPhone + ", Name=" + smsData.receiverName + ", COD=" + smsData.receiverCod);
					break;
				}
			}
		}
		
		// If not found, check cache
		if (!foundDetails) {
			String cachedData = AppModel.Object.GetVariable(AppModel.MY_SHIPMENTS_CACHE_KEY);
			if (!AppModel.IsNullOrEmpty(cachedData)) {
				try {
					ShipmentResponse cached = new Gson().fromJson(cachedData, ShipmentResponse.class);
					if (cached != null && cached.shipments != null) {
						for (ShipmentWithDetail s : cached.shipments) {
							if (s.tracking_id != null && s.tracking_id.equalsIgnoreCase(scannedCode)) {
								// Found it - use ONLY receiver details
								smsData.receiverPhone = s.receiver_phone;
								smsData.receiverName = s.receiver_name;
								smsData.receiverAddress = s.receiver_address;
								smsData.senderName = s.sender_name;
								try {
									smsData.receiverCod = Double.parseDouble(s.receiver_cod);
									AppModel.ApplicationError(null, "SMS CACHE: COD for " + scannedCode + " = " + smsData.receiverCod + " (from: " + s.receiver_cod + ")");
								} catch (Exception e) {
									smsData.receiverCod = 0;
									AppModel.ApplicationError(null, "SMS CACHE: COD parse error for " + scannedCode + ", defaulting to 0");
								}
								// Don't use server sms_text for scan - use template instead
								// smsData.smsText = s.sms_text;
								foundDetails = true;
								AppModel.ApplicationError(null, "SMS: Found details in cache for: " + scannedCode);
								AppModel.ApplicationError(null, "SMS CACHE: Phone=" + smsData.receiverPhone + ", Name=" + smsData.receiverName + ", COD=" + smsData.receiverCod);
								break;
							}
						}
					}
				} catch (Exception e) {
					AppModel.ApplicationError(null, "SMS: Cache error: " + e.getMessage());
				}
			}
		}
					
		// FINAL CHECK - make absolutely sure tracking ID hasn't changed
		if (!scannedCode.equals(smsData.trackingId)) {
			AppModel.ApplicationError(null, "SMS CRITICAL ERROR: Tracking ID changed from " + scannedCode + " to " + smsData.trackingId);
			smsData.trackingId = scannedCode; // Force it back
		}
		
		// Try to send SMS immediately if we have phone number
		if (foundDetails && !AppModel.IsNullOrEmpty(smsData.receiverPhone)) {
			AppModel.ApplicationError(null, "SMS: Found details - TrackingID: " + smsData.trackingId + " Phone: " + smsData.receiverPhone);

			// Try immediate send first
			SMSHelper.sendSMSImmediate(getContext(), smsData, new SMSHelper.SMSCallback() {
				@Override
				public void onResult(boolean success) {
					if (!success) {
						// Only add to queue if immediate send failed
						AppModel.ApplicationError(null, "SMS: Immediate send failed, adding to queue: " + scannedCode);
						SMSQueueManager queueManager = SMSQueueManager.getInstance(App.Object);
						queueManager.addToQueue(smsData);

						// Log queue status
						AppModel.ApplicationError(null, "SMS Queue Status: Size=" + queueManager.getQueueSize() +
							", Sent=" + queueManager.getSentCount() +
							", Remaining=" + queueManager.getRemainingCapacity());
					} else {
						AppModel.ApplicationError(null, "SMS: Sent immediately for: " + scannedCode);
					}
				}
			});

		} else if (!foundDetails) {
			AppModel.ApplicationError(null, "SMS: No details found for: " + scannedCode);
		} else {
			AppModel.ApplicationError(null, "SMS: No phone number for: " + scannedCode);
		}

		AppModel.ApplicationError(null, "=== SMS SEND END ===");
	}

	private void sendPickupSMSForShipment(final String scannedCode) {
		// Send country-based pickup SMS
		AppModel.ApplicationError(null, "=== PICKUP SMS SEND START ===");
		AppModel.ApplicationError(null, "SMS: Attempting pickup SMS for code: " + scannedCode);

		// Create SMS data - START WITH THE SCANNED CODE
		SMSHelper.SMSData smsData = new SMSHelper.SMSData();

		// Set tracking ID and status
		smsData.trackingId = scannedCode;
		smsData.shipmentId = scannedCode;
		smsData.statusId = 14; // Packed
		smsData.driverName = App.CurrentUser != null ? App.CurrentUser.user : "";

		AppModel.ApplicationError(null, "SMS: Tracking ID set to: " + smsData.trackingId);

		// Try to find receiver details and country ID
		boolean foundDetails = false;

		// First check current list
		synchronized (ITEMS) {
			for (ShipmentWithDetail s : ITEMS) {
				if (s.tracking_id != null && s.tracking_id.equalsIgnoreCase(scannedCode)) {
					// Found it - get receiver details and country ID
					smsData.receiverPhone = s.receiver_phone;
					smsData.receiverName = s.receiver_name;
					smsData.receiverAddress = s.receiver_address;
					smsData.senderName = s.sender_name;
					smsData.countryId = s.receiver_country_id; // Get country ID for localized message
					try {
						smsData.receiverCod = Double.parseDouble(s.receiver_cod);
					} catch (Exception e) {
						smsData.receiverCod = 0;
					}
					foundDetails = true;
					AppModel.ApplicationError(null, "SMS: Found details in current list for: " + scannedCode);
					AppModel.ApplicationError(null, "SMS: Phone=" + smsData.receiverPhone + ", CountryID=" + smsData.countryId);
					break;
				}
			}
		}

		// If not found, check cache
		if (!foundDetails) {
			String cachedData = AppModel.Object.GetVariable(AppModel.MY_SHIPMENTS_CACHE_KEY);
			if (!AppModel.IsNullOrEmpty(cachedData)) {
				try {
					ShipmentResponse cached = new Gson().fromJson(cachedData, ShipmentResponse.class);
					if (cached != null && cached.shipments != null) {
						for (ShipmentWithDetail s : cached.shipments) {
							if (s.tracking_id != null && s.tracking_id.equalsIgnoreCase(scannedCode)) {
								// Found it - get receiver details and country ID
								smsData.receiverPhone = s.receiver_phone;
								smsData.receiverName = s.receiver_name;
								smsData.receiverAddress = s.receiver_address;
								smsData.senderName = s.sender_name;
								smsData.countryId = s.receiver_country_id;
								try {
									smsData.receiverCod = Double.parseDouble(s.receiver_cod);
								} catch (Exception e) {
									smsData.receiverCod = 0;
								}
								foundDetails = true;
								AppModel.ApplicationError(null, "SMS: Found details in cache for: " + scannedCode);
								AppModel.ApplicationError(null, "SMS CACHE: Phone=" + smsData.receiverPhone + ", CountryID=" + smsData.countryId);
								break;
							}
						}
					}
				} catch (Exception e) {
					AppModel.ApplicationError(null, "SMS: Cache error: " + e.getMessage());
				}
			}
		}

		// FINAL CHECK - make absolutely sure tracking ID hasn't changed
		if (!scannedCode.equals(smsData.trackingId)) {
			AppModel.ApplicationError(null, "SMS CRITICAL ERROR: Tracking ID changed from " + scannedCode + " to " + smsData.trackingId);
			smsData.trackingId = scannedCode; // Force it back
		}

		// Try to send SMS immediately if we have phone number
		if (foundDetails && !AppModel.IsNullOrEmpty(smsData.receiverPhone)) {
			AppModel.ApplicationError(null, "SMS: Found details - TrackingID: " + smsData.trackingId + " Phone: " + smsData.receiverPhone + " Country: " + smsData.countryId);

			// Try immediate send first
			SMSHelper.sendSMSImmediate(getContext(), smsData, new SMSHelper.SMSCallback() {
				@Override
				public void onResult(boolean success) {
					if (!success) {
						// Only add to queue if immediate send failed
						AppModel.ApplicationError(null, "SMS: Immediate send failed, adding to queue: " + scannedCode);
						SMSQueueManager queueManager = SMSQueueManager.getInstance(App.Object);
						queueManager.addToQueue(smsData);

						// Log queue status
						AppModel.ApplicationError(null, "SMS Queue Status: Size=" + queueManager.getQueueSize() +
							", Sent=" + queueManager.getSentCount() +
							", Remaining=" + queueManager.getRemainingCapacity());
					} else {
						AppModel.ApplicationError(null, "SMS: Pickup SMS sent immediately for: " + scannedCode);
					}
				}
			});

		} else if (!foundDetails) {
			AppModel.ApplicationError(null, "SMS: No details found for: " + scannedCode);
		} else {
			AppModel.ApplicationError(null, "SMS: No phone number for: " + scannedCode);
		}

		AppModel.ApplicationError(null, "=== PICKUP SMS SEND END ===");
	}

	private void SendKey(String code, String status, int statusId, boolean sendComments) {
		try {
			AppModel.ApplicationError(null, "SENDKEY: code=" + code + ", statusId=" + statusId);
			
			if (AppModel.IsNullOrEmpty(code)) {
				MessageCtrl.Toast("Please scan barcode first");
				return;
			}

			if (sendComments)
				App.Object.sendProblemCommentsCtrl.SetVisibility(code, true);
			else
				SubmitRequest(code, status, statusId, null);

		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "ScanCtrl::SendKey");
		}
	}

	private void SubmitRequest(final String code, final String status, final int statusId, String comments) {
		AppModel.ApplicationError(null, "SUBMIT: Sending to server - code=" + code + ", statusId=" + statusId);
		
		// If marking as delivered (status_id = 2) or rejected (status_id = 3), remove from SMS queue
		if (statusId == 2 || statusId == 3) {
			SMSQueueManager queueManager = SMSQueueManager.getInstance(App.Object);
			queueManager.removeFromQueue(code);
			AppModel.ApplicationError(null, "SMS Queue: Removing " + code + " from queue (status changed to " + statusId + ")");
		}
		
		App.SetProcessing(true);
		Communicator.SendDistributorRequest(code, status, statusId, comments, new IServerResponse() {
			@Override
			public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
				App.Object.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						try {
							if (success) {
								if (objs != null) {
									KeyRef u = (KeyRef) objs[0];
									if (!AppModel.IsNullOrEmpty(u.response_txt))
										MessageCtrl.Toast(u.response_txt);

									// Send SMS if status is "In Delivery" (statusId = 1) or "Packed" (statusId = 14)
									// CRITICAL: Use the exact same code that was just sent to the server
									// IMPORTANT: Send SMS BEFORE calling Load() to avoid race conditions with ITEMS list refresh
									if (statusId == 1) {
										AppModel.ApplicationError(null, "SMS TRIGGER: Sending SMS for scanned code: " + code);
										// Send SMS in background - don't wait for it
										sendSMSForShipment(code);
										// No toast for SMS - just log it
									} else if (statusId == 14) {
										AppModel.ApplicationError(null, "SMS TRIGGER: Sending PACKED SMS for scanned code: " + code);
										// Send packed SMS in background
										sendPickupSMSForShipment(code);
										// No toast for SMS - just log it
									}

									// Delay the Load() call slightly to ensure SMS is processed first
									App.Object.getWindow().getDecorView().postDelayed(new Runnable() {
										@Override
										public void run() {
											Load();
										}
									}, 500);

									if (isDeliveryScan) {
										App.Object.userDistributorReconcileShipmentsFragment.Load();
										App.Object.userDistributorReturnShipmentsFragment.Load();
									}
								} else
									MessageCtrl.Toast("Scanned key sending failed, Please try again");
							} else if (!AppModel.IsNullOrEmpty(messageToShow)) {
								MessageCtrl.Toast(messageToShow);

								boolean isNew = true;
								for (ShipmentWithDetail s : ITEMS) {
									if (s.tracking_id.equalsIgnoreCase(code)) {
										isNew = false;
										break;
									}
								}

								if (isNew) {
									ShipmentWithDetail s = new ShipmentWithDetail();
									s.tracking_id = code;
									s.description_title = code;
									ITEMS.add(s);
									AppModel.Object.SaveVariable(AppModel.MY_SHIPMENTS_CACHE_KEY, new Gson().toJson(ITEMS));
									ApplyFilter();
								}
							}
						} catch (Exception ex) {
							AppModel.ApplicationError(ex, "ScanCtrl::SendKey");
						} finally {
							App.SetProcessing(false);
						}
					}
				});
			}
		});
	}
}
