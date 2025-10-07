package common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import eu.trackify.net.R;

import java.util.List;

import common.Communicator.IServerResponse;

public class UserDistributorExpenses extends Fragment {

	Button btnAddExpense;
	SpinnerCustom cbo_Type;
	TextView et_Amount, et_Description;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.ctrl_distributor_expenses, null);

		et_Amount = (EditText) v.findViewById(R.id.et_Amount);
		et_Description = (EditText) v.findViewById(R.id.et_Description);

		cbo_Type = (SpinnerCustom) v.findViewById(R.id.cbo_Type);
		cbo_Type.SetSettings("description", R.color.Black);

		btnAddExpense = (Button) v.findViewById(R.id.btnAddExpense);
		btnAddExpense.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ExpenseType type = (ExpenseType) cbo_Type.getSelectedItem();

				if (type == null) {
					MessageCtrl.Toast("Please select Expense Type");
					return;
				} else if (AppModel.IsNullOrEmpty(et_Amount.getText().toString())) {
					MessageCtrl.Toast("Please enter Amount");
					return;
				} else if (AppModel.IsNullOrEmpty(et_Description.getText().toString())) {
					MessageCtrl.Toast("Please enter Description");
					return;
				}

				AddExpense(type.expense_type_id, et_Amount.getText().toString(), et_Description.getText().toString());
			}
		});

		LoadExpenseTypes();
		App.Object.userDistributorMyShipmentsFragment.Load();

		return v;
	}

	protected void AddExpense(String expense_type_id, String amount, String desc) {
		App.SetProcessing(true);
		Communicator.AddExpense(expense_type_id, amount, desc, new IServerResponse() {
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
									// MessageCtrl.Toast("Expense successfully added");
									et_Amount.setText("");
									et_Description.setText("");
								} else
									MessageCtrl.Toast("Expense addition failed, Please try again");
							} else if (!AppModel.IsNullOrEmpty(messageToShow))
								MessageCtrl.Toast(messageToShow);
						} catch (Exception ex) {
							AppModel.ApplicationError(ex, "ScanCtrl::AddExpense");
						} finally {
							App.SetProcessing(false);
						}
					}
				});
			}
		});
	}

	protected void LoadExpenseTypes() {
		Communicator.GetExpenseTypes(new IServerResponse() {
			@Override
			public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
				App.Object.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						try {
							if (success && objs != null) {
								List<ExpenseType> items = (List<ExpenseType>) objs[0];
								cbo_Type.Initialize(items, null);
							} else
								LoadExpenseTypes();
						} catch (Exception ex) {
							AppModel.ApplicationError(ex, "ScanCtrl::AddExpense");
						}
					}
				});
			}
		});
	}
}