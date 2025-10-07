package common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.text.format.DateUtils;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;

import eu.trackify.net.R;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import common.PendingRequestsLoader.PendingRequestItem;

public class AppModel {

	public static final boolean IsBeta = false;

	public static final String APP_FOLDER_NAME = "MEX";
	public final String ORIGINAL_DB_NAME = "DB.sqlite";

	public static final String USER_KEY = "USER_KEY";
	public static final String PASS_KEY = "PASS_KEY";
	public static final String PASS_ENCRYPTED_KEY = "PASS_ENCRYPTED_KEY";
	public static final String USER_CACHE_KEY = "USER_CACHE_KEY";
	public static final String AUTH_TOKEN = "AUTH_TOKEN";
	public static final String MY_SHIPMENTS_CACHE_KEY = "SHIPMENTS_CACHE_KEY";
	public static final String RECONCILE_SHIPMENTS_CACHE_KEY = "RECONCILE_SHIPMENTS_CACHE_KEY";

	public static final String APP_SHARED_PREFERENCE_NAME = "AppSharedPref";
	public static final String LOG_FILE_NAME = "ExceptionsLog";
	public static final String LOG_FOLDER_NAME = "Logs";

	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
	public static final SimpleDateFormat DATE_TWO_FORMAT = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss", Locale.ENGLISH);
	public static final SimpleDateFormat ONLY_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
	public static final SimpleDateFormat ONLY_DATE_TWO_FORMAT = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH);
	public static final SimpleDateFormat DISPLAY_LONG_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy hh:mm aa", Locale.ENGLISH);
	public static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("h:mm aa", Locale.ENGLISH);
	public static final SimpleDateFormat LONG_TIME_FORMAT = new SimpleDateFormat("h:mm:ss aa", Locale.ENGLISH);
	public static final SimpleDateFormat SHORT_TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
	public static final SimpleDateFormat SHORT_TIME_FORMAT_FOR_ROSTER_RUN = new SimpleDateFormat("h:mm", Locale.ENGLISH);
	public static final SimpleDateFormat SHORT_TIME_HMS_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);
	public static final SimpleDateFormat DAY_DATE_FORMAT = new SimpleDateFormat("EEEE, MM/dd/yyyy", Locale.ENGLISH);
	public static final SimpleDateFormat SERVER_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
	public static final SimpleDateFormat DISPLAY_LONG_DAY_DATE_FORMAT = new SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.ENGLISH);

	// Database
	public DatabaseManager db;
	public static PendingRequestsLoader Pendings = new PendingRequestsLoader();

	public Context context;
	public SharedPreferences sharedPreferences;
	public static AppModel Object;
	ConnectivityManager connectivityManager;

	boolean cancel = false;
	static int GPS_DELAY_IN_SECONDS = 5 * 60;
	static int SETTINGS_DELAY_IN_SECONDS = 5 * 60;
	static boolean IS_GPS_UPDATE_ENABLED = false;

	GPS gps = new GPS();

	public AppModel(Context _context) {
		context = _context;
		db = new DatabaseManager(_context, _context.getDatabasePath(ORIGINAL_DB_NAME));
		sharedPreferences = context.getSharedPreferences(APP_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE);
		Object = this;
	}

	public void Start() {
		try {
			Thread SettingsThread = new Thread(new Runnable() {

				int COUNT_SECONDS = SETTINGS_DELAY_IN_SECONDS;

				@Override
				public void run() {

					while (!cancel) {

						if (App.CurrentUser != null) {
							try {
								if (COUNT_SECONDS >= SETTINGS_DELAY_IN_SECONDS) {
									COUNT_SECONDS = 0;

									Communicator.GetSettings(new common.Communicator.IServerResponse() {
										@Override
										public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
											try {
												if (success) {
													if (objs != null) {
														@SuppressWarnings("unchecked")
														SettingsRef item = (SettingsRef) objs[0];
														if (item != null) {
															SETTINGS_DELAY_IN_SECONDS = item.probe_interval;
															GPS_DELAY_IN_SECONDS = item.gps_update_interval;
															IS_GPS_UPDATE_ENABLED = item.gps_tracking == 1;
														}
													}
												}
											} catch (Exception ex) {
												AppModel.ApplicationError(ex, "Response::GetSettings");
											}
										}
									});
								}
							} catch (Exception ex) {
								ApplicationError(ex, "AppModel::SettingsThread(GetSettings)");
							}

							try {
								COUNT_SECONDS++;
								Thread.sleep(1000);
							} catch (Exception ex) {

							}
						}
					}
				}
			}, "SettingsThread");
			SettingsThread.setPriority(Thread.MIN_PRIORITY);
			SettingsThread.start();

			Thread GpsThread = new Thread(new Runnable() {

				int COUNT_SECONDS = GPS_DELAY_IN_SECONDS;

				@Override
				public void run() {

					while (!cancel) {

						if (App.CurrentUser != null) {
							try {
								if (COUNT_SECONDS >= GPS_DELAY_IN_SECONDS) {
									COUNT_SECONDS = 0;

									if (IS_GPS_UPDATE_ENABLED && GPS.IsConnected) {
										Communicator.UpdateGps(new common.Communicator.IServerResponse() {
											@Override
											public void onCompleted(final boolean success, final String messageToShow, final Object... objs) {
												try {
													if (success) {
														if (objs != null) {

														}
													}
												} catch (Exception ex) {
													AppModel.ApplicationError(ex, "Response::UpdateGps");
												}
											}
										});
									}
								}
							} catch (Exception ex) {
								ApplicationError(ex, "AppModel::GpsThread(UpdateGps)");
							}

							try {
								COUNT_SECONDS++;
								Thread.sleep(1000);
							} catch (Exception ex) {

							}
						}
					}
				}
			}, "GpsThread");
			GpsThread.setPriority(Thread.MIN_PRIORITY);
			GpsThread.start();

			Thread SyncThread = new Thread(new Runnable() {

				@Override
				public void run() {

					while (!cancel) {
						try {
							if (AppModel.Object.IsNetworkAvailable(false)) {
								List<PendingRequestItem> pendings = AppModel.Pendings.GetPendings();
								if (pendings.size() > 0) {
									for (PendingRequestItem pi : pendings) {
										if (Communicator.SendPending(pi)) {
											AppModel.Pendings.MarkUploaded(pi.ID);
										}
									}
									App.Object.userDistributorMyShipmentsFragment.Load();
									App.Object.userDistributorReconcileShipmentsFragment.Load();
									App.Object.userDistributorReturnShipmentsFragment.Load();
								}
							}
						} catch (Exception ex) {
							ApplicationError(ex, "AppModel::SettingsThread(GetSettings)");
						}

						try {
							Thread.sleep(1000);
						} catch (Exception ex) {

						}
					}
				}
			}, "SyncThread");
			SyncThread.setPriority(Thread.MIN_PRIORITY);
			SyncThread.start();
		} catch (Exception ex) {
			ApplicationError(ex, "AppModel::Start");
		}

		gps.Start();
	}

	public void Stop() {
		// Shut the application background worker
		cancel = true;
	}

	public void SaveSettingVariables(String key, String value) {
		try {
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString(key, value);
			editor.commit();
		} catch (Exception e) {
			ApplicationError(e, "AppModel::SaveSettings");
		}
	}

	public String GetSettingVariable(String key) {
		try {
			return sharedPreferences.getString(key, "");
		} catch (Exception e) {
			ApplicationError(e, "AppModel::GetSettingVariable");
		}
		return "";
	}

	public void SaveVariable(String key, String value) {
		try {
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString(key, value);
			editor.commit();
		} catch (Exception e) {
			ApplicationError(e, "AppModel::SaveVariable");
		}
	}

	public String GetVariable(String key) {
		try {
			return sharedPreferences.getString(key, null);
		} catch (Exception e) {
			ApplicationError(e, "AppModel::GetVariable");
		}
		return null;
	}

	public int GetSettingVariable_Int(String key) {
		try {
			String value = GetSettingVariable(key);
			if (!IsNullOrEmpty(value))
				return Integer.parseInt(value);
		} catch (Exception e) {
			ApplicationError(e, "AppModel::GetSettingVariable");
		}
		return 0;
	}

	public static <T> List<T> JsonToArrayList(String s, Class<T[]> clazz) {
		T[] arr = new Gson().fromJson(s, clazz);
		return Arrays.asList(arr);
	}

	public static Bitmap ConvertBase64StringToBitmap(String base64String) {
		try {
			if (!AppModel.IsNullOrEmpty(base64String)) {
				byte[] decodedString = Base64.decode(base64String, Base64.DEFAULT);
				Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
				return decodedByte;
			}
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "AppModel::ConvertBase64StringToBitmap");
		}
		return null;
	}

	public static String ConvertBitmapToBase64String(Bitmap bitmap) {
		try {
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
			byte[] imageBytes = stream.toByteArray();
			String encoded = Base64.encodeToString(imageBytes, Base64.DEFAULT);
			return encoded;
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "AppModel::ConvertBitmapToBase64String");
		}
		return "";
	}

	public String GetStringResource(int id) {
		return context.getString(id);
	}

	public Drawable GetDrawableResource(int id) {
		return androidx.core.content.ContextCompat.getDrawable(context, id);
	}

	public Bitmap GetBitmapResource(int id) {
		return BitmapFactory.decodeResource(context.getResources(), id);
	}

	public int GetColorResource(int id) {
		return androidx.core.content.ContextCompat.getColor(context, id);
	}

	public static Bitmap GetResizedBitmapFromPath(String path, int reqWidth, int reqHeight) {
		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(path, options);

		// Calculate inSampleSize
		options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeFile(path, options);
	}

	private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and keeps both
			// height and width larger than the requested height and width.
			while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
				inSampleSize *= 2;
			}
		}

		return inSampleSize;
	}

	public static Calendar GetCalendar(boolean onlyDate) {
		Calendar cal = Calendar.getInstance();
		if (onlyDate) {
			cal.set(Calendar.HOUR_OF_DAY, 0); // HOUR -> Doesn't effect AM/PM
												// While HOUR_OF_DAY effects..
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
		}
		return cal;
	}

	public static Calendar GetCalendar(int DAY_OF_YEAR, int MONTH, int YEAR, int HOUR_OF_DAY, int MINUTE, int SECOND, int MILLISECOND) {
		Calendar cal = GetCalendar(false);
		cal.set(Calendar.DAY_OF_YEAR, DAY_OF_YEAR);
		cal.set(Calendar.MONTH, MONTH);
		cal.set(Calendar.YEAR, YEAR);
		cal.set(Calendar.HOUR_OF_DAY, HOUR_OF_DAY); // HOUR -> Doesn't effect AM/PM
		// While HOUR_OF_DAY effects..
		cal.set(Calendar.MINUTE, MINUTE);
		cal.set(Calendar.SECOND, SECOND);
		cal.set(Calendar.MILLISECOND, MILLISECOND);
		return cal;
	}

	public static Calendar GetCalendar(int HOUR_OF_DAY, int MINUTE, int SECOND, int MILLISECOND) {
		Calendar cal = GetCalendar(false);
		return GetCalendar(cal.get(Calendar.DAY_OF_YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.YEAR), HOUR_OF_DAY, MINUTE, SECOND, MILLISECOND);
	}

	public static Calendar GetCalendar(int DAY_OF_YEAR, int MONTH, int YEAR) {
		Calendar cal = GetCalendar(false);
		return GetCalendar(DAY_OF_YEAR, MONTH, YEAR, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND), cal.get(Calendar.MILLISECOND));
	}

	public static Calendar GetCalendar_DP(int DAY_OF_MONTH, int MONTH, int YEAR) {
		Calendar cal = GetCalendar(false);
		cal.set(Calendar.DAY_OF_MONTH, DAY_OF_MONTH);
		cal.set(Calendar.MONTH, MONTH);
		cal.set(Calendar.YEAR, YEAR);
		cal.set(Calendar.HOUR_OF_DAY, 0); // HOUR -> Doesn't effect AM/PM
		// While HOUR_OF_DAY effects..
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal;
	}

	public static Calendar GetCalendar(Date time, boolean setTodayDate) {
		if (setTodayDate)
			return GetCalendar(GetCalendar(false), time);
		else {
			Calendar cal = GetCalendar(false);
			cal.setTime(time);
			return cal;
		}
	}

	public static Calendar GetCalendar(Calendar date, Date time) {
		Calendar cal = GetCalendar(false);
		cal.setTime(time);
		return GetCalendar(date.get(Calendar.DAY_OF_YEAR), date.get(Calendar.MONTH), date.get(Calendar.YEAR), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND), cal.get(Calendar.MILLISECOND));
	}

	public void SetEnability_EditText(final EditText et, final boolean doEnable) {
		App.Object.runOnUiThread(new Runnable() {
			@Override
			public void run() {

				et.setFocusable(doEnable);
				et.setFocusableInTouchMode(doEnable);
				et.setClickable(doEnable);

				AppModel.Object.SetBackground(et, doEnable ? R.drawable.edittext_whitebg_primaryborder_notop_rounded : R.drawable.edittext_whitebg_primaryborder_notop_rounded_disable);
			}
		});
	}

	public void SetEnability_Spinner(final Spinner spin, final boolean doEnable) {
		App.Object.runOnUiThread(new Runnable() {
			@Override
			public void run() {

				spin.setEnabled(doEnable);

				AppModel.Object.SetBackground(spin, doEnable ? R.drawable.edittext_whitebg_rounded : R.drawable.edittext_whitebg_rounded_disable);
			}
		});
	}

	public void SetEnability(final View btn, final boolean doEnable, final int enableDrawableId) {
		App.Object.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				btn.setEnabled(doEnable);
				AppModel.Object.SetBackground(btn, doEnable ? enableDrawableId : R.drawable.button_rounded_disabled);
				// ANDROID_COMPATIBILITY
				Runnable sdkAction = new Runnable() {
					@SuppressLint("NewApi")
					@Override
					public void run() {
						btn.setAlpha(doEnable ? 1F : 0.5F);
					}
				};
				if (Build.VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB)
					sdkAction.run();
				else
					btn.getBackground().setAlpha(doEnable ? 255 : 127);
			}
		});
	}

	public void SetEnability(final View btn, final boolean doEnable) {
		SetEnability(btn, doEnable, R.drawable.button_style);
	}

	public void SetBackground(View view, int resId) {
		view.setBackgroundResource(resId);
	}

	public static boolean IsNullOrEmpty(String string) {
		return string == null || string.equals("") || string.trim().equals("") || string.length() == 0 || string.trim().equalsIgnoreCase("null");
	}

	public enum AppFolderType {
		Logs, Images, Downloads
	}

	public static File GetAppFolder(AppFolderType type) {
		File reqFile = null;

		File appDir = new File(Environment.getExternalStorageDirectory() + File.separator + APP_FOLDER_NAME);
		if (!appDir.exists())
			appDir.mkdirs();

		switch (type) {
		case Logs:
			reqFile = new File(appDir.getAbsolutePath() + File.separator + "Logs");
			break;
		case Images:
			reqFile = new File(appDir.getAbsolutePath() + File.separator + "Images");
			break;
		default:
			reqFile = appDir;
			break;
		}

		if (!reqFile.exists())
			reqFile.mkdirs();

		return reqFile;
	}

	public static String ConvertToCsv(Object[] items) {
		String csv = "";
		for (int indx = 0; indx < items.length; indx++) {
			csv += indx == 0 ? items[indx].toString() : ("," + items[indx].toString());
		}
		return csv;
	}

	public static String[] Split(String stringToSplit, String splitBy) {
		return IsNullOrEmpty(stringToSplit) ? new String[0] : stringToSplit.trim().split("[" + splitBy + "]");
	}

	public boolean IsNetworkAvailable(boolean showMsg) {

		try {
			if (connectivityManager == null)
				connectivityManager = (ConnectivityManager) App.Object.getSystemService(Context.CONNECTIVITY_SERVICE);

			NetworkInfo info = connectivityManager.getActiveNetworkInfo();
			// boolean isWiFi = info.getType() == ConnectivityManager.TYPE_WIFI;
			boolean hasNetwork = info != null && (info.getState() == NetworkInfo.State.CONNECTED);

			if (!hasNetwork && showMsg) {
				MessageCtrl.Show("Internet connection is not available");
			}

			return hasNetwork;
		} catch (Exception ex) {
			ApplicationError(ex, "App::IsNetworkAvailable");
		}

		return false;
	}

	public String GetTimeSpanString(Date timeSpan) {

		long milliSeconds = timeSpan.getTime();

		long totalSeconds = GetTimeSpan(milliSeconds, true, Calendar.SECOND);
		long totalMinutes = GetTimeSpan(milliSeconds, true, Calendar.MINUTE);
		long totalHours = GetTimeSpan(milliSeconds, true, Calendar.HOUR_OF_DAY);
		long totalDays = GetTimeSpan(milliSeconds, true, Calendar.DAY_OF_YEAR);

		// long seconds = GetTimeSpan(milliSeconds, false, Calendar.SECOND);
		long minutes = GetTimeSpan(milliSeconds, false, Calendar.MINUTE);
		long hours = GetTimeSpan(milliSeconds, false, Calendar.HOUR_OF_DAY);
		long days = GetTimeSpan(milliSeconds, false, Calendar.DAY_OF_YEAR);

		if (totalMinutes < 1)
			return totalSeconds + " secs.";
		else if (totalHours < 1)
			return totalMinutes + " mins.";
		else if (totalDays < 1)
			return hours + " hrs." + " " + minutes + " mins.";
		else
			return days + " hrs." + " " + hours + " mins." + " " + minutes + " secs.";
	}

	public long GetTimeSpan(long milliSeconds, boolean total, int field) {

		long totalSeconds = milliSeconds / 1000;

		if (total) {
			long totalMinutes = totalSeconds / 60;
			long totalHours = totalMinutes > 0 ? totalMinutes / 60 : totalMinutes;
			long totalDays = totalHours > 0 ? totalHours / 24 : totalHours;

			switch (field) {
			case Calendar.DAY_OF_YEAR:
				return totalDays;
			case Calendar.HOUR_OF_DAY:
				return totalHours;
			case Calendar.MINUTE:
				return totalMinutes;
			case Calendar.SECOND:
				return totalSeconds;
			}
		} else {

			Calendar cal = AppModel.GetCalendar(false);
			cal.set(Calendar.DAY_OF_YEAR, 1);
			cal.set(Calendar.MONTH, 1);
			cal.set(Calendar.YEAR, 1900);
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			cal.set(Calendar.SECOND, (int) totalSeconds);

			switch (field) {
			case Calendar.DAY_OF_YEAR:
				return cal.get(Calendar.DAY_OF_YEAR) - 1;
			case Calendar.HOUR_OF_DAY:
				return cal.get(Calendar.HOUR_OF_DAY);
			case Calendar.MINUTE:
				return cal.get(Calendar.MINUTE);
			case Calendar.SECOND:
				return cal.get(Calendar.SECOND);
			}
		}
		return 0;
	}

	public static String GetFormattedDateTime(Date date, boolean forDateOnly) {
		String formatted = "";

		if (date != null) {
			Calendar yesterday = AppModel.GetCalendar(false);
			yesterday.add(Calendar.DATE, -1);
			Calendar calendar = GetCalendar(date, false);

			if (forDateOnly) {
				if (DateUtils.isToday(calendar.getTimeInMillis()))
					formatted = "Today";
				else if (yesterday.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR))
					formatted = "Yesterday";
				else
					formatted = AppModel.DISPLAY_LONG_DAY_DATE_FORMAT.format(date);
			} else {
				if (DateUtils.isToday(calendar.getTimeInMillis()))
					formatted = AppModel.LONG_TIME_FORMAT.format(date);
				else if (yesterday.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR))
					formatted = "Yesterday " + AppModel.TIME_FORMAT.format(date);
				else
					formatted = AppModel.DISPLAY_LONG_DATE_FORMAT.format(date);
			}
		}

		return formatted;
	}

	public static String GetExceptionMessage(Exception ex) {
		String msg = "";
		try {
			if (ex.getMessage() == null) {
				msg = ex.toString();
				msg = msg.contains(":") ? Split(msg, ":")[1].trim() : msg;
			} else
				msg = ex.getMessage();
		} catch (Exception exp) {
			ApplicationError(exp, "AppModel::GetExceptionMessage()");
		}
		return msg;
	}

	public static void ApplicationError(Exception exp, String source) {
		try {

			String message = getStackTrace(exp.fillInStackTrace());
			if (message == null)
				message = GetExceptionMessage(exp);

			AppModel.WriteLog(LOG_FILE_NAME, source, message);
		} catch (Exception ex) {

		}
	}

	public static void WriteLog(String logFileName, String source, String stack) {
		try {
			String logTxt = "\n  Source: " + (IsNullOrEmpty(source) ? "" : source) + "\n  Stack: " + (IsNullOrEmpty(stack) ? "" : stack);

			Calendar day = AppModel.GetCalendar(false);
			String month = new SimpleDateFormat("MMMM", Locale.ENGLISH).format(day.getTime()); // (day.get(Calendar.MONTH) + 1)
			String date = day.get(Calendar.DAY_OF_MONTH) + "-" + month + "-" + day.get(Calendar.YEAR);
			String fileName = "/" + logFileName + "(" + date + ").txt";
			File log = new File(AppModel.GetAppFolder(AppFolderType.Logs) + "/" + fileName);
			OutputStream myOutput = new FileOutputStream(log, log.exists());
			logTxt = "Datetime: " + day.getTime().toString() + "\n" + "Exception:" + logTxt + "\n\n\n";
			myOutput.write(logTxt.getBytes());

			// Close the streams
			myOutput.flush();
			myOutput.close();

		} catch (IOException ex) {

		}
	}

	public static String getStackTrace(Throwable aThrowable) {
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		aThrowable.printStackTrace(printWriter);
		return result.toString();
	}
}
