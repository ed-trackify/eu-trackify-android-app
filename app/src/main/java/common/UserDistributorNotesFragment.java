package common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import eu.trackify.net.R;

import java.util.Date;

import common.Communicator.IServerResponse;
import common.ListDataBinder.BindedListType;
import common.UserDistributorShipmentsFragment.ShipmentsType;

public class UserDistributorNotesFragment extends Fragment {

	ListView lv_results;
	ListDataBinder<NoteItem> binder;

	View btnSendComments;
	TextView et_OtherNote;
	View btnToggleAddNote;
	View ll_Top;
	ImageView ivExpandIcon;
	TextView tvNoteCount;

	RadioButton radio1, radio2, radio3, radio4, radio5, radio6;

	ShipmentWithDetail Current;
	boolean isAddNoteExpanded = false;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.ctrl_distributor_notes, null);

		lv_results = (ListView) v.findViewById(R.id.lv_results);
		// Remove touch listener as we're not using ScrollView anymore
		
		binder = new ListDataBinder<NoteItem>(BindedListType.Note, lv_results);
		
		// Initialize new UI elements
		btnToggleAddNote = v.findViewById(R.id.btnToggleAddNote);
		ll_Top = v.findViewById(R.id.ll_Top);
		ivExpandIcon = (ImageView) v.findViewById(R.id.ivExpandIcon);
		tvNoteCount = (TextView) v.findViewById(R.id.tvNoteCount);

		radio1 = (RadioButton) v.findViewById(R.id.radio1);
		radio2 = (RadioButton) v.findViewById(R.id.radio2);
		radio3 = (RadioButton) v.findViewById(R.id.radio3);
		radio4 = (RadioButton) v.findViewById(R.id.radio4);
		radio5 = (RadioButton) v.findViewById(R.id.radio5);
		radio6 = (RadioButton) v.findViewById(R.id.radio6);

		et_OtherNote = (TextView) v.findViewById(R.id.et_OtherNote);
		
		// Add toggle button click handler
		btnToggleAddNote.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				toggleAddNoteSection();
			}
		});
		
		btnSendComments = v.findViewById(R.id.btnSendComments);
		btnSendComments.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (radio1.isChecked())
					SendComments("1", "");
				else if (radio2.isChecked())
					SendComments("2", "");
				else if (radio3.isChecked())
					SendComments("3", "");
				else if (radio4.isChecked())
					SendComments("4", "");
				else if (radio5.isChecked())
					SendComments("5", "");
				else if (radio6.isChecked()) {
					if (AppModel.IsNullOrEmpty(et_OtherNote.getText().toString())) {
						MessageCtrl.Toast(getContext().getString(R.string.note_error_more_info));
						return;
					}
					SendComments("6", et_OtherNote.getText().toString());
				}

			}
		});

		Initialize();

		return v;
	}

	private void SendComments(final String noteType, final String comments) {
		App.SetProcessing(true);
		Communicator.SendShipmentComments(Current.shipment_id, noteType, comments, new IServerResponse() {
			@Override
			public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
				App.Object.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						try {
							if (success) {
								if (objs != null) {
									MessageCtrl.Toast(getContext().getString(R.string.note_success));
									AddNote(comments);
								} else
									MessageCtrl.Toast(getContext().getString(R.string.note_error_failed));
							} else if (!AppModel.IsNullOrEmpty(messageToShow)) {
								MessageCtrl.Toast(messageToShow);
								AddNote(comments);
							}
						} catch (Exception ex) {
							AppModel.ApplicationError(ex, "ScanCtrl::SendComments");
						} finally {
							App.SetProcessing(false);
						}
					}
				});
			}
		});
	}

	public void AddNote(String comment) {
		try {
			NoteItem i = new NoteItem();
			i.shipment_id = Current.shipment_id;
			i.comment = comment;
			i.comment_timestamp = AppModel.SERVER_FORMAT.format(new Date());
			i.driver_name = App.CurrentUser.user;
			i.user_id = "" + App.CurrentUser.user_id;

			Current._Notes.add(i);
			Initialize();
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "UserDistributorNotesFragment::AddNote");
		}
	}

	private void toggleAddNoteSection() {
		isAddNoteExpanded = !isAddNoteExpanded;
		ll_Top.setVisibility(isAddNoteExpanded ? View.VISIBLE : View.GONE);
		ivExpandIcon.setRotation(isAddNoteExpanded ? 180 : 0);
		
		// Collapse the section after sending note
		if (!isAddNoteExpanded) {
			et_OtherNote.setText("");
			radio1.setChecked(true);
		}
	}
	
	public void Initialize() {
		try {
			if (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.StatusCheck) {
				Current = StatusCheckCtrl.SELECTED;
			} else {
				Current = App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.MyShipments ? App.Object.userDistributorMyShipmentsFragment.SELECTED : (App.Object.userDistributorShipmentDetailTabCtrl.LoadFromShipmentType == ShipmentsType.Returns ?  App.Object.userDistributorReturnShipmentsFragment.SELECTED : App.Object.userDistributorReconcileShipmentsFragment.SELECTED);
			}
			if (Current != null) {
				binder.Initialize(Current._Notes);
				int noteCount = Current._Notes.size();
				String tabTitle = getContext().getString(R.string.tab_notes) + " (" + noteCount + ")";
				App.Object.userDistributorShipmentDetailTabCtrl.ChangeTabTitle(tabTitle, this);

				// Update note count
				if (tvNoteCount != null) {
					String countText = String.format(getContext().getString(noteCount == 1 ? R.string.notes_count_single : R.string.notes_count), noteCount);
					tvNoteCount.setText(countText);
				}
				
				// Reset form
				et_OtherNote.setText("");
				
				// Collapse add note section after successful submission
				if (isAddNoteExpanded) {
					toggleAddNoteSection();
				}
			}
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "UserDistributorNotesFragment::Load");
		}
	}
}
