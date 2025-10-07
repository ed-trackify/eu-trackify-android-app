package common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import eu.trackify.net.R;
import com.google.gson.Gson;

import common.Communicator.IServerResponse;

//edo.driver/edo.driver9/edo.client
//Edo123/Edo123!

public class LoginCtrl extends LinearLayout {

	Button btnLogIn;
	EditText et_Username, et_Password;

	public LoginCtrl(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = inflater.inflate(R.layout.ctrl_login, this);

		if (!this.isInEditMode()) {
			et_Username = (EditText) v.findViewById(R.id.et_Username);
			et_Password = (EditText) v.findViewById(R.id.et_Password);

			btnLogIn = (Button) v.findViewById(R.id.btnLogIn);
			btnLogIn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					try {
						HideKeyboard();

						final String username = et_Username.getText().toString();
						final String password = et_Password.getText().toString();

						if (AppModel.IsNullOrEmpty(username)) {
							MessageCtrl.Toast(getContext().getString(R.string.login_error_username_required));
							return;
						} else if (AppModel.IsNullOrEmpty(password)) {
							MessageCtrl.Toast(getContext().getString(R.string.login_error_password_required));
							return;
						}

						AppModel.Object.SetEnability(btnLogIn, false);

						// Send plain text password - WordPress handles hashing server-side
						// IMPORTANT: Must use HTTPS for security
						Communicator.Login(username, password, new IServerResponse() {
							@Override
							public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
								App.Object.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										try {
											if (success) {
												if (objs != null) {
													UserRef u = (UserRef) objs[0];
													if (!AppModel.IsNullOrEmpty(u.authenticated) && u.authenticated.equalsIgnoreCase("true")) {
														App.CurrentUser = u;

														AppModel.Object.SaveVariable(AppModel.USER_CACHE_KEY, new Gson().toJson(u));
														AppModel.Object.SaveVariable(AppModel.AUTH_TOKEN, u.auth_key);
														AppModel.Object.SaveVariable(AppModel.USER_KEY, username);

														// Encrypt password for storage
														String deviceId = PasswordSecurityUtil.getDeviceId(getContext());
														String encryptedPassword = PasswordSecurityUtil.encryptForStorage(password, deviceId);
														if (encryptedPassword != null) {
															AppModel.Object.SaveVariable(AppModel.PASS_KEY, encryptedPassword);
															AppModel.Object.SaveVariable(AppModel.PASS_ENCRYPTED_KEY, "true");
														}

														// App.Object.ShowCtrl(UserType.Driver);
														App.Object.ShowCtrl(u.GetUserType());
													} else if (!AppModel.IsNullOrEmpty(u.error_message))
														MessageCtrl.Toast(u.error_message);
													else
														MessageCtrl.Toast(getContext().getString(R.string.login_error_invalid_credentials));
												} else
													MessageCtrl.Toast(getContext().getString(R.string.login_error_invalid_credentials));
											} else if (!AppModel.IsNullOrEmpty(messageToShow))
												MessageCtrl.Toast(messageToShow);
										} catch (Exception ex) {
											AppModel.ApplicationError(ex, "LoginCtrl::Login");
										} finally {
											AppModel.Object.SetEnability(btnLogIn, true);
										}
									}
								});
							}
						}, null);

					} catch (Exception ex) {
						AppModel.ApplicationError(ex, "LoginCtrl::btnLogIn");
					}
				}
			});

			// Exit button removed - users should use device back button or home button
			Button btnExit = (Button) v.findViewById(R.id.btnExit);
			if (btnExit != null) {
				btnExit.setVisibility(View.GONE); // Hide the exit button
			}
		}
	}

	public boolean AutoLogin(final IServerResponse response) {
		final String username = AppModel.Object.GetVariable(AppModel.USER_KEY);
		String storedPassword = AppModel.Object.GetVariable(AppModel.PASS_KEY);
		final String isEncrypted = AppModel.Object.GetVariable(AppModel.PASS_ENCRYPTED_KEY);

		// Decrypt password if it was stored encrypted
		String password = null;
		if (!AppModel.IsNullOrEmpty(storedPassword)) {
			if ("true".equals(isEncrypted)) {
				String deviceId = PasswordSecurityUtil.getDeviceId(getContext());
				password = PasswordSecurityUtil.decryptFromStorage(storedPassword, deviceId);
			} else {
				// Legacy: password stored in plain text, migrate to encrypted
				password = storedPassword;
			}
		}

		if (!AppModel.IsNullOrEmpty(username) && !AppModel.IsNullOrEmpty(password)) {
			final String finalPassword = password;
			et_Username.setText(username);
			et_Password.setText("********"); // Don't show actual password

			// Send plain text password - WordPress handles hashing server-side
			// IMPORTANT: Must use HTTPS for security
			Communicator.Login(username, password, new IServerResponse() {
				@Override
				public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
					App.Object.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							try {
								if (success && objs != null) {
									UserRef u = (UserRef) objs[0];
									if (!AppModel.IsNullOrEmpty(u.authenticated) && u.authenticated.equalsIgnoreCase("true")) {
										App.CurrentUser = u;

										AppModel.Object.SaveVariable(AppModel.USER_CACHE_KEY, new Gson().toJson(u));
										AppModel.Object.SaveVariable(AppModel.AUTH_TOKEN, u.auth_key);
										AppModel.Object.SaveVariable(AppModel.USER_KEY, username);

										// Re-encrypt password if it was stored in plain text
										if (!"true".equals(isEncrypted)) {
											String deviceId = PasswordSecurityUtil.getDeviceId(getContext());
											String encryptedPassword = PasswordSecurityUtil.encryptForStorage(finalPassword, deviceId);
											if (encryptedPassword != null) {
												AppModel.Object.SaveVariable(AppModel.PASS_KEY, encryptedPassword);
												AppModel.Object.SaveVariable(AppModel.PASS_ENCRYPTED_KEY, "true");
											}
										}

										// App.Object.ShowCtrl(UserType.Driver);
										App.Object.ShowCtrl(u.GetUserType());
										response.onCompleted(true, "");
									} else if (!AppModel.IsNullOrEmpty(u.error_message))
										MessageCtrl.Toast(u.error_message);
									else {
										MessageCtrl.Toast(getContext().getString(R.string.login_error_invalid_credentials));
										response.onCompleted(false, "");
									}
								} else if (!AppModel.IsNullOrEmpty(messageToShow)) {
									MessageCtrl.Toast(messageToShow);
									response.onCompleted(false, "");
								}
							} catch (Exception ex) {
								AppModel.ApplicationError(ex, "Response::Login");
							}
						}
					});
				}
			}, new Runnable() {
				@Override
				public void run() {
					String uJson = AppModel.Object.GetSettingVariable(AppModel.USER_CACHE_KEY);
					if (!AppModel.IsNullOrEmpty(uJson)) {
						App.CurrentUser = new Gson().fromJson(uJson, UserRef.class);
						App.Object.ShowCtrl(App.CurrentUser.GetUserType());
						response.onCompleted(true, "");
					}
				}
			});

			return true;
		} else
			return false;
	}

	public void HideKeyboard() {
		View view = App.Object.getCurrentFocus();
		if (view != null) {
			InputMethodManager imm = (InputMethodManager) App.Object.getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
		}
	}

	public void Initialize() {
		et_Username.setText("");
		et_Password.setText("");
		// et_Username.setText("dkace");
		// et_Password.setText("Ace123");
		AppModel.Object.SetEnability(btnLogIn, true);
	}
}