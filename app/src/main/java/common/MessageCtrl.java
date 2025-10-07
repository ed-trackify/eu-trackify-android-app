package common;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import eu.trackify.net.R;

import java.util.concurrent.atomic.AtomicBoolean;

public class MessageCtrl {

	final static int MESSAGE_PANEL_HEIGHT = 200;
	final static int MESSAGE_PANEL_HEIGHT_COMPATIBILITY = 160;
	final static int MESSAGE_PANEL_WIDTH_COMPATIBILITY = 500;
	final static int BUTTON_PANEL_HEIGHT = 74;

	public enum MessageBoxResult {
		OK, NONE, YES, NO, CANCEL
	}

	public enum MessageBoxImage {
		None, Error, Question, Warning, Information
	}

	public interface IMessageBoxResult {
		public void onResult(MessageBoxResult result);
	}

	public static void Show(final String text) {
		Show(text, "Information", false, MessageBoxImage.Information, null);
	}

	public static void Toast(final String text) {

		App.Object.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					Toast.makeText(App.Object, text, Toast.LENGTH_LONG).show();
				} catch (Exception ex) {
					AppModel.ApplicationError(ex, "MessageCtrl::Toast");
				}
			}
		});
	}

	public static void ToastShort(final String text) {

		App.Object.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					Toast.makeText(App.Object, text, Toast.LENGTH_SHORT).show();
				} catch (Exception ex) {
					AppModel.ApplicationError(ex, "MessageCtrl::ToastShort");
				}
			}
		});
	}

	public static void Show(final String text, final String caption) {
		Show(text, caption, false, MessageBoxImage.Information, null);
	}

	public static void Show(final String text, final String caption, MessageBoxImage image) {
		Show(text, caption, false, image, null);
	}

	public static void Confirm(final String text, String caption, final IMessageBoxResult resultCallback) {
		Show(text, caption, true, MessageBoxImage.Question, resultCallback);
	}

	public static void Show(final String text, final String caption, final boolean isConfirmationBox, final MessageBoxImage image, final IMessageBoxResult resultCallback) {

		App.Object.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {

					final AtomicBoolean isUserDismissed = new AtomicBoolean(true);

					AlertDialog.Builder builder = new AlertDialog.Builder(App.Object).setTitle(caption).setMessage(text);
					if (isConfirmationBox) {
						builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								isUserDismissed.set(false);
								SendCallback(dialog, resultCallback, MessageBoxResult.YES);
							}
						}).setNegativeButton("No", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								isUserDismissed.set(false);
								SendCallback(dialog, resultCallback, MessageBoxResult.NO);
							}
						});
					} else {
						builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								isUserDismissed.set(false);
								SendCallback(dialog, resultCallback, MessageBoxResult.OK);
							}
						});
					}
					builder.setCancelable(false);

					switch (image) {
					case Warning:
						builder.setIcon(androidx.core.content.ContextCompat.getDrawable(App.Object, R.drawable.exclamation_icon));
						break;
					case Error:
						builder.setIcon(androidx.core.content.ContextCompat.getDrawable(App.Object, R.drawable.hand_icon));
						break;
					case Question:
						builder.setIcon(androidx.core.content.ContextCompat.getDrawable(App.Object, R.drawable.question_icon));
						break;
					default:
						builder.setIcon(androidx.core.content.ContextCompat.getDrawable(App.Object, R.drawable.info_icon));
						break;
					}

					builder.create().show();
				} catch (Exception ex) {
					AppModel.ApplicationError(ex, "MessageCtrl::Show");
					if (resultCallback != null)
						resultCallback.onResult(MessageBoxResult.NONE);
				}
			}
		});
	}

	private static void SendCallback(final DialogInterface dialog, final IMessageBoxResult resultCallback, final MessageBoxResult result) {
		App.Object.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					dialog.dismiss();
				} catch (Exception ex) {
					// May be UI Thread issue..
				}

				if (resultCallback != null)
					resultCallback.onResult(result);
			}
		});
	}
}
