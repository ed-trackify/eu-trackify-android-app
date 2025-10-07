package common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import eu.trackify.net.R;

public class ChangePasswordDialog extends LinearLayout {

    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private Button btnCancel;
    private Button btnChangePassword;
    private ImageButton btnToggleNewPassword;
    private ImageButton btnToggleConfirmPassword;
    private TextView tvPasswordStrength;
    private TextView tvPasswordMatch;
    private boolean isNewPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    public ChangePasswordDialog(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.dialog_change_password, this);

        if (!this.isInEditMode()) {
            initializeViews(v);
            setupClickListeners();
        }
    }

    private void initializeViews(View v) {
        etCurrentPassword = v.findViewById(R.id.et_CurrentPassword);
        etNewPassword = v.findViewById(R.id.et_NewPassword);
        etConfirmPassword = v.findViewById(R.id.et_ConfirmPassword);
        btnCancel = v.findViewById(R.id.btnCancel);
        btnChangePassword = v.findViewById(R.id.btnChangePassword);
        btnToggleNewPassword = v.findViewById(R.id.btn_ToggleNewPassword);
        btnToggleConfirmPassword = v.findViewById(R.id.btn_ToggleConfirmPassword);
        tvPasswordStrength = v.findViewById(R.id.tv_PasswordStrength);
        tvPasswordMatch = v.findViewById(R.id.tv_PasswordMatch);

        // Set up background click to block touches
        RelativeLayout rootView = v.findViewById(R.id.rl_dialogRootView);
        rootView.setOnTouchListener(new OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true; // Block touches to background
            }
        });

        // Set up password visibility toggles
        setupPasswordToggles();

        // Set up real-time password validation
        setupPasswordValidation();
    }

    private void setupClickListeners() {
        btnCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearFields();
                setVisibility(View.GONE);
            }
        });

        btnChangePassword.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                changePassword();
            }
        });
    }

    private void changePassword() {
        String currentPassword = etCurrentPassword.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Validation
        if (currentPassword.isEmpty()) {
            MessageCtrl.Toast(getContext().getString(R.string.password_error_current_required));
            etCurrentPassword.requestFocus();
            return;
        }

        if (newPassword.isEmpty()) {
            MessageCtrl.Toast(getContext().getString(R.string.password_error_new_required));
            etNewPassword.requestFocus();
            return;
        }

        if (newPassword.length() < 6) {
            MessageCtrl.Toast(getContext().getString(R.string.password_error_min_length));
            etNewPassword.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            MessageCtrl.Toast(getContext().getString(R.string.password_error_no_match));
            etConfirmPassword.requestFocus();
            return;
        }

        if (currentPassword.equals(newPassword)) {
            MessageCtrl.Toast(getContext().getString(R.string.password_error_same_as_old));
            etNewPassword.requestFocus();
            return;
        }

        // Check password strength
        String strengthMessage = PasswordSecurityUtil.getPasswordStrengthMessage(newPassword);
        if (strengthMessage.startsWith("Password")) { // It's an error message
            MessageCtrl.Toast(strengthMessage);
            etNewPassword.requestFocus();
            return;
        }

        // Show loading
        App.SetProcessing(true);

        // Send plain text passwords - WordPress handles hashing server-side
        // IMPORTANT: Must use HTTPS for security

        // Call API to change password with plain text values
        Communicator.ChangePassword(currentPassword, newPassword, new Communicator.IServerResponse() {
            @Override
            public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
                App.Object.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        App.SetLoading(false);

                        if (success) {
                            MessageCtrl.Toast(getContext().getString(R.string.password_success));

                            // Update stored password if auto-login is enabled
                            String storedUsername = AppModel.Object.GetVariable(AppModel.USER_KEY);
                            if (!AppModel.IsNullOrEmpty(storedUsername)) {
                                // Encrypt and store the new password for auto-login
                                String deviceId = PasswordSecurityUtil.getDeviceId(getContext());
                                String encryptedPassword = PasswordSecurityUtil.encryptForStorage(newPassword, deviceId);

                                if (encryptedPassword != null) {
                                    AppModel.Object.SaveVariable(AppModel.PASS_KEY, encryptedPassword);
                                    AppModel.Object.SaveVariable(AppModel.PASS_ENCRYPTED_KEY, "true");
                                }
                            }

                            clearFields();
                            setVisibility(View.GONE);
                        } else {
                            if (!AppModel.IsNullOrEmpty(messageToShow)) {
                                MessageCtrl.Toast(messageToShow);
                            } else {
                                MessageCtrl.Toast(getContext().getString(R.string.password_error_failed));
                            }
                        }
                    }
                });
            }
        });
    }

    public void show() {
        clearFields();
        setVisibility(View.VISIBLE);
        etCurrentPassword.requestFocus();
    }

    private void clearFields() {
        etCurrentPassword.setText("");
        etNewPassword.setText("");
        etConfirmPassword.setText("");
        if (tvPasswordStrength != null) {
            tvPasswordStrength.setVisibility(View.GONE);
        }
        if (tvPasswordMatch != null) {
            tvPasswordMatch.setVisibility(View.GONE);
        }
        // Reset password visibility
        isNewPasswordVisible = false;
        isConfirmPasswordVisible = false;
        if (etNewPassword != null) {
            etNewPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        if (etConfirmPassword != null) {
            etConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
    }

    public void SetVisibility(boolean makeVisible) {
        if (makeVisible) {
            show();
        } else {
            clearFields();
            setVisibility(View.GONE);
        }
    }

    private void setupPasswordToggles() {
        if (btnToggleNewPassword != null) {
            btnToggleNewPassword.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    togglePasswordVisibility(etNewPassword, btnToggleNewPassword, true);
                }
            });
        }

        if (btnToggleConfirmPassword != null) {
            btnToggleConfirmPassword.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    togglePasswordVisibility(etConfirmPassword, btnToggleConfirmPassword, false);
                }
            });
        }
    }

    private void togglePasswordVisibility(EditText editText, ImageButton button, boolean isNewPassword) {
        if (editText == null || button == null) return;

        int cursorPosition = editText.getSelectionStart();

        if (isNewPassword) {
            isNewPasswordVisible = !isNewPasswordVisible;
            if (isNewPasswordVisible) {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                button.setImageResource(android.R.drawable.ic_secure);
            } else {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                button.setImageResource(android.R.drawable.ic_menu_view);
            }
        } else {
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
            if (isConfirmPasswordVisible) {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                button.setImageResource(android.R.drawable.ic_secure);
            } else {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                button.setImageResource(android.R.drawable.ic_menu_view);
            }
        }

        editText.setSelection(cursorPosition);
    }

    private void setupPasswordValidation() {
        if (etNewPassword != null) {
            etNewPassword.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    String password = s.toString();
                    if (password.length() > 0 && tvPasswordStrength != null) {
                        String strengthMessage = PasswordSecurityUtil.getPasswordStrengthMessage(password);
                        tvPasswordStrength.setText(strengthMessage);
                        tvPasswordStrength.setVisibility(View.VISIBLE);

                        // Set color based on strength
                        if (strengthMessage.contains("Strong")) {
                            tvPasswordStrength.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        } else if (strengthMessage.contains("Good") || strengthMessage.contains("Acceptable")) {
                            tvPasswordStrength.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                        } else {
                            tvPasswordStrength.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        }
                    } else if (tvPasswordStrength != null) {
                        tvPasswordStrength.setVisibility(View.GONE);
                    }

                    // Check password match
                    checkPasswordMatch();
                }
            });
        }

        if (etConfirmPassword != null) {
            etConfirmPassword.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    checkPasswordMatch();
                }
            });
        }
    }

    private void checkPasswordMatch() {
        if (etNewPassword == null || etConfirmPassword == null || tvPasswordMatch == null) return;

        String newPassword = etNewPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        if (confirmPassword.length() > 0) {
            if (newPassword.equals(confirmPassword)) {
                tvPasswordMatch.setText("✓ Passwords match");
                tvPasswordMatch.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                tvPasswordMatch.setVisibility(View.VISIBLE);
            } else {
                tvPasswordMatch.setText("✗ Passwords do not match");
                tvPasswordMatch.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                tvPasswordMatch.setVisibility(View.VISIBLE);
            }
        } else {
            tvPasswordMatch.setVisibility(View.GONE);
        }
    }
}