package common;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class DatabaseManager extends SQLiteOpenHelper {

	private File DB_FILE = null;
	private final static int DB_VERSION = 1;
	public static boolean IsDatabaseReady;

	public enum TableType {
		PendingRequests, PostParams;

		@SuppressLint("DefaultLocale")
		public static TableType GetFromLowerCase(String tablename) {
			TableType rtn = null;
			TableType[] allValues = TableType.values();
			for (TableType tableType : allValues) {
				if (tableType.toString().toLowerCase().equalsIgnoreCase(tablename)) {
					rtn = tableType;
					break;
				}
			}
			return rtn;
		}
	}

	private Context context;

	public DatabaseManager(Context context, File dbFile) {
		super(context, dbFile.getName(), null, DB_VERSION);
		this.context = context;
		DB_FILE = dbFile; // context.getDatabasePath(DB_NAME);
	}

	public void ProcessCreateDatabase() {

		DeleteOldVersionDatabase();

		while (!(isDbExist() && isDbFunctional())) {
			if (isDbExist())
				DB_FILE.delete();

			CreateDatabase();
		}
	}

	private void DeleteOldVersionDatabase() {
		final String CURRENT_VERSION = "1";
		final String VERSION_KEY = "DB_VERSION";
		String existingV = AppModel.Object.GetVariable(VERSION_KEY);
		if (AppModel.IsNullOrEmpty(existingV) || !existingV.equals(CURRENT_VERSION)) {
			if (isDbExist())
				DB_FILE.delete();
			AppModel.Object.SaveVariable(VERSION_KEY, CURRENT_VERSION);
		}
	}

	private void CreateDatabase() {
		try {
			this.getReadableDatabase();
			this.close();
		} catch (Exception ex) {
			MessageCtrl.Show("getReadableDatabase and Close:" + AppModel.GetExceptionMessage(ex));
		}

		try {

			// Open your local db as the input stream
			InputStream myInput = context.getAssets().open(DB_FILE.getName());

			// Open the empty db as the output stream
			OutputStream myOutput = new FileOutputStream(DB_FILE.getAbsolutePath());

			// transfer bytes from the inputfile to the outputfile
			byte[] buffer = new byte[1024];
			int length;
			while ((length = myInput.read(buffer)) > 0) {
				myOutput.write(buffer, 0, length);
			}

			// Close the streams
			myOutput.flush();
			myOutput.close();
			myInput.close();
		} catch (IOException ex) {
			MessageCtrl.Show("createDataBase:" + AppModel.GetExceptionMessage(ex));
		}
	}

	private boolean isDbExist() {

		try {
			return DB_FILE != null && DB_FILE.exists();
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "isDbExist");
		}

		return false;
	}

	private boolean isDbFunctional() {

		try {
			// ArrayList<HashMap<String, String>> records = SelectFormatted(TableType.Employees, new String[] { DbColumns.Configurations_ID }, null, null);
			// return records.size() > 0;
			return true;
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "isDbFunctional");
		}

		return false;
	}

	public SQLiteDatabase openDataBase() throws SQLException {

		// Open the database
		return SQLiteDatabase.openDatabase(DB_FILE.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
	}

	@Override
	public synchronized void close() {
		super.close();
	}

	@Override
	public void onCreate(SQLiteDatabase db) {

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

	}

	public void copyDatabaseTo(String pathToCopy) throws IOException {
		if (isDbExist()) {
			File dbFile = new File(DB_FILE.getAbsolutePath());
			FileInputStream fis = new FileInputStream(dbFile);

			// Open the empty db as the output stream
			OutputStream output = new FileOutputStream(pathToCopy);
			// transfer bytes from the inputfile to the outputfile
			byte[] buffer = new byte[1024];
			int length;
			while ((length = fis.read(buffer)) > 0) {
				output.write(buffer, 0, length);
			}
			// Close the streams
			output.flush();
			output.close();
			fis.close();
		}
	}

	// ///// ---- QUERY CONTROLLER ---- ///// //

	public ArrayList<HashMap<String, String>> SelectFormatted(TableType TableType, String[] columns, String filter, String orderBy) {
		SQLiteDatabase db = null;
		ArrayList<HashMap<String, String>> result = new ArrayList<HashMap<String, String>>();
		Cursor cursor = null;

		try {
			DatabaseManager dbMang = new DatabaseManager(App.Object, DB_FILE);
			db = dbMang.openDataBase();

			if (columns == null)
				columns = new String[] { "*" };
			String csvColumns = AppModel.ConvertToCsv(columns);
			String selectCmd = "SELECT " + csvColumns + " FROM " + TableType.toString();
			if (filter != null)
				selectCmd += " WHERE " + filter;
			if (orderBy != null)
				selectCmd += " ORDER BY " + orderBy;
			cursor = db.rawQuery(selectCmd, null);

			while (cursor.moveToNext()) {
				HashMap<String, String> item = new HashMap<String, String>();
				String[] colNames = cursor.getColumnNames();
				for (String colName : colNames) {
					String value = cursor.getString(cursor.getColumnIndex(colName));
					if (value != null && value.equalsIgnoreCase("NULL"))
						value = null;
					item.put(colName, value);
				}
				result.add(item);
			}

		} catch (Exception ex) {

			AppModel.ApplicationError(ex, "DatabaseManager.SelectFormatted(" + TableType + "..)");
		} finally {
			if (cursor != null)
				cursor.close();

			if (db != null)
				db.close();
		}
		return result;
	}

	public ArrayList<HashMap<String, String>> SelectFormatted(String selectCmd) {
		SQLiteDatabase db = null;
		ArrayList<HashMap<String, String>> result = new ArrayList<HashMap<String, String>>();
		Cursor cursor = null;

		try {
			DatabaseManager dbMang = new DatabaseManager(App.Object, DB_FILE);
			db = dbMang.openDataBase();
			cursor = db.rawQuery(selectCmd, null);

			while (cursor.moveToNext()) {
				LinkedHashMap<String, String> item = new LinkedHashMap<String, String>();
				String[] colNames = cursor.getColumnNames();
				for (String colName : colNames) {
					String value = cursor.getString(cursor.getColumnIndex(colName));
					if (value != null && value.equalsIgnoreCase("NULL"))
						value = null;
					item.put(colName, value);
				}
				result.add(item);
			}

		} catch (Exception ex) {

			AppModel.ApplicationError(ex, "DatabaseManager.SelectFormatted(" + selectCmd + "..)");
		} finally {
			if (cursor != null)
				cursor.close();

			if (db != null)
				db.close();
		}
		return result;
	}

	public List<String> GetTableNames() {
		List<String> rtn = new ArrayList<String>();
		String selectCmd = "SELECT name FROM sqlite_master WHERE type='table' AND name not in ('android_metadata') order by name";
		try {
			ArrayList<HashMap<String, String>> tables = SelectFormatted(selectCmd);
			for (HashMap<String, String> t : tables) {
				rtn.add(t.get("name"));
			}
		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "DatabaseManager.GetTableNames");
		}
		return rtn;
	}

	@SuppressLint("DefaultLocale")
	public List<String> GetTableColumns(TableType table, boolean inLowerCase) {
		List<String> rtn = new ArrayList<String>();
		SQLiteDatabase db = null;
		Cursor cursor = null;

		String selectCmd = MessageFormat.format("Select * from {0} LIMIT 1", table);
		try {
			DatabaseManager dbMang = new DatabaseManager(App.Object, DB_FILE);
			db = dbMang.openDataBase();
			cursor = db.rawQuery(selectCmd, null);

			for (String col : cursor.getColumnNames()) {
				rtn.add(inLowerCase ? col.toLowerCase() : col);
			}

		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "DatabaseManager.GetTableColumns");
		} finally {
			if (cursor != null)
				cursor.close();

			if (db != null)
				db.close();
		}
		return rtn;
	}

	public int GetTableMaxID(TableType TableType, String idColumnName) {
		int maxId = 0;
		try {
			final String Max_Key = "MaxValue";
			if (idColumnName == null)
				idColumnName = "ID";
			ArrayList<HashMap<String, String>> result = SelectFormatted(TableType, new String[] { "Max(" + idColumnName + ") As " + Max_Key }, null, null);

			if (result.size() > 0)
				maxId = Integer.parseInt(result.get(0).get(Max_Key));

		} catch (Exception ex) {
			// AppController.LogException("DatabaseManager.GetTableMaxID()",
			// ex);
		}
		return maxId;
	}

	public int GetTableMaxID(TableType TableType, String idColumnName, String filter) {
		int maxId = 0;
		try {
			final String Max_Key = "MaxValue";
			if (idColumnName == null)
				idColumnName = "ID";
			ArrayList<HashMap<String, String>> result = SelectFormatted(TableType, new String[] { "Max(" + idColumnName + ") As " + Max_Key }, filter, null);

			if (result.size() > 0)
				maxId = Integer.parseInt(result.get(0).get(Max_Key));

		} catch (Exception ex) {
			// AppController.LogException("DatabaseManager.GetTableMaxID()",
			// ex);
		}
		return maxId;
	}

	public int GetTableCount(TableType TableType, String column) {
		int count = 0;
		try {
			final String Count_Key = "CountValue";
			if (column == null)
				column = "*";
			ArrayList<HashMap<String, String>> result = SelectFormatted(TableType, new String[] { "Count(" + column + ") As " + Count_Key }, null, null);

			if (result.size() > 0)
				count = Integer.parseInt(result.get(0).get(Count_Key));

		} catch (Exception ex) {

		}
		return count;
	}

	public int GetTableCount(String countcommand) {
		int count = 0;
		try {

			ArrayList<HashMap<String, String>> result = SelectFormatted(countcommand);

			if (result.size() > 0)
				count = Integer.parseInt(result.get(0).get("Count"));

		} catch (Exception ex) {

		}
		return count;
	}

	public boolean Insert(TableType TableType, ContentValues values) {
		SQLiteDatabase db = null;
		try {
			DatabaseManager dbMang = new DatabaseManager(App.Object, DB_FILE);
			db = dbMang.openDataBase();
			db.insertOrThrow(TableType.toString(), null, values);
			return true;

		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "DatabaseManager.Insert(" + TableType + "..)");
		} finally {
			if (db != null)
				db.close();
		}
		return false; // error occurred.
	}

	public boolean Update(TableType TableType, ContentValues values, String filter) {
		SQLiteDatabase db = null;
		try {
			DatabaseManager dbMang = new DatabaseManager(App.Object, DB_FILE);
			db = dbMang.openDataBase();
			db.update(TableType.toString(), values, filter, null);
			return true;

		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "DatabaseManager.Update(" + TableType + "..)");
		} finally {
			if (db != null)
				db.close();
		}
		return false;
	}

	public boolean Delete(TableType TableType, String filter) {
		SQLiteDatabase db = null;
		try {
			DatabaseManager dbMang = new DatabaseManager(App.Object, DB_FILE);
			db = dbMang.openDataBase();
			db.delete(TableType.toString(), filter, null);
			return true;

		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "DatabaseManager.Delete(" + TableType + "..)");
		} finally {
			if (db != null)
				db.close();
		}
		return false;
	}

	public boolean ExecuteQuery(String query) {
		SQLiteDatabase db = null;
		try {
			DatabaseManager dbMang = new DatabaseManager(App.Object, DB_FILE);
			db = dbMang.openDataBase();
			db.execSQL(query);
			return true;

		} catch (Exception ex) {
			AppModel.ApplicationError(ex, "DatabaseManager.ExecuteQuery()");
		} finally {
			if (db != null)
				db.close();
		}
		return false;
	}

	public static String DatePart(String columnName) {
		return " substr(" + columnName + ",0, 11) ";
	}

	public static String TimePart(String columnName) {
		return " substr(" + columnName + ",12, 8) ";
	}

	public static String CurrentDatePart(boolean isCallingInsideMessageFormat) {
		Calendar c = AppModel.GetCalendar(false);
		String formatted = AppModel.DATE_FORMAT.format(c.getTime()).substring(0, 10);
		return isCallingInsideMessageFormat ? "''" + formatted + "''" : "'" + formatted + "'";
	}
}
