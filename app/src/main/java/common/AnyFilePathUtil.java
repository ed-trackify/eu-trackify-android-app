package common;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.UUID;

/**
 * @version 2009-07-03
 * @author Peli
 * @version 2013-12-11
 * @author paulburke (ipaulpro)
 */
public class AnyFilePathUtil {
	private AnyFilePathUtil() {
	} 

	static final String TAG = "FileUtils";
	private static final boolean DEBUG = false; // Set to true to enable logging

	public static final String MIME_TYPE_AUDIO = "audio/*";
	public static final String MIME_TYPE_TEXT = "text/*";
	public static final String MIME_TYPE_IMAGE = "image/*";
	public static final String MIME_TYPE_VIDEO = "video/*";
	public static final String MIME_TYPE_APP = "application/*";

	public static final String HIDDEN_PREFIX = ".";

	/**
	 * Gets the extension of a file name, like ".png" or ".jpg".
	 * 
	 * @param uri
	 * @return Extension including the dot("."); "" if there is no extension; null if uri was null.
	 */
	public static String getExtension(String uri) {
		if (uri == null) {
			return null;
		}

		int dot = uri.lastIndexOf(".");
		if (dot >= 0) {
			return uri.substring(dot);
		} else {
			// No extension.
			return "";
		}
	}

	/**
	 * @return Whether the URI is a local one.
	 */
	public static boolean isLocal(String url) {
		if (url != null && !url.startsWith("http://") && !url.startsWith("https://")) {
			return true;
		}
		return false;
	}

	/**
	 * @return True if Uri is a MediaStore Uri.
	 * @author paulburke
	 */
	public static boolean isMediaUri(Uri uri) {
		return "media".equalsIgnoreCase(uri.getAuthority());
	}

	/**
	 * Convert File into Uri.
	 * 
	 * @param file
	 * @return uri
	 */
	public static Uri getUri(File file) {
		if (file != null) {
			return Uri.fromFile(file);
		}
		return null;
	}

	/**
	 * Returns the path only (without file name).
	 * 
	 * @param file
	 * @return
	 */
	public static File getPathWithoutFilename(File file) {
		if (file != null) {
			if (file.isDirectory()) {
				// no file to be split off. Return everything
				return file;
			} else {
				String filename = file.getName();
				String filepath = file.getAbsolutePath();

				// Construct path without file name.
				String pathwithoutname = filepath.substring(0, filepath.length() - filename.length());
				if (pathwithoutname.endsWith("/")) {
					pathwithoutname = pathwithoutname.substring(0, pathwithoutname.length() - 1);
				}
				return new File(pathwithoutname);
			}
		}
		return null;
	}

	/**
	 * @return The MIME type for the given file.
	 */
	public static String getMimeType(File file) {

		String extension = getExtension(file.getName());

		if (extension.length() > 0)
			return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.substring(1));

		return "application/octet-stream";
	}

	/**
	 * @return The MIME type for the give Uri.
	 * @throws Exception
	 */
	public static String getMimeType(Context context, Uri uri) throws Exception {
		File file = new File(getPath(context, uri));
		return getMimeType(file);
	}

	/**
	 * @param uri
	 *            The Uri to check.
	 * @return Whether the Uri authority is {@link LocalStorageProvider}.
	 * @author paulburke
	 */
	public static boolean isLocalStorageDocument(Uri uri) {
		return LocalStorageProvider.AUTHORITY.equals(uri.getAuthority());
	}

	/**
	 * @param uri
	 *            The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 * @author paulburke
	 */
	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri
	 *            The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 * @author paulburke
	 */
	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri
	 *            The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 * @author paulburke
	 */
	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri
	 *            The Uri to check.
	 * @return Whether the Uri authority is Google Photos.
	 */
	public static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}

	/**
	 * Get the value of the data column for this Uri. This is useful for MediaStore Uris, and other file-based ContentProviders.
	 * 
	 * @param context
	 *            The context.
	 * @param uri
	 *            The Uri to query.
	 * @param selection
	 *            (Optional) Filter used in the query.
	 * @param selectionArgs
	 *            (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 * @author paulburke
	 */
	public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {

		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = { column };

		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
			if (cursor != null && cursor.moveToFirst()) {
				if (DEBUG)
					DatabaseUtils.dumpCursor(cursor);

				final int column_index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(column_index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}

	/**
	 * Get a file path from a Uri. This will get the the path for Storage Access Framework Documents, as well as the _data field for the MediaStore and other file-based ContentProviders.<br>
	 * <br>
	 * Callers should check whether the path is local before assuming it represents a local file.
	 * 
	 * @param context
	 *            The context.
	 * @param uri
	 *            The Uri to query.
	 * @see #isLocal(String)
	 * @see #getFile(Context, Uri)
	 * @author paulburke
	 * @throws Exception
	 */
	public static String getPath(final Context context, final Uri uri) throws Exception {

		String log = "Uri: " + uri.toString();

		try {

			log += "\n FileInfo::: Authority: " + uri.getAuthority() + ", Fragment: " + uri.getFragment() + ", Port: " + uri.getPort() + ", Query: " + uri.getQuery() + ", Scheme: " + uri.getScheme() + ", Host: " + uri.getHost() + ", Segments: " + uri.getPathSegments().toString();

			final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

			log += "\n isKitKat: " + isKitKat;

			// DocumentProvider
			if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
				// LocalStorageProvider
				if (isLocalStorageDocument(uri)) {
					// The path is the id
					log += "\n isLocalStorageDocument(Returning-DocumentId: " + DocumentsContract.getDocumentId(uri) + ")";
					return DocumentsContract.getDocumentId(uri);
				}
				// ExternalStorageProvider
				else if (isExternalStorageDocument(uri)) {

					final String docId = DocumentsContract.getDocumentId(uri);
					final String[] split = docId.split(":");
					final String type = split[0];

					log += "\n isExternalStorageDocument (docId: " + docId + ", type: " + type + ")";

					if ("primary".equalsIgnoreCase(type)) {
						log += "\n Returning-Path: " + Environment.getExternalStorageDirectory() + "/" + split[1];
						return Environment.getExternalStorageDirectory() + "/" + split[1];
					}

					// TODO handle non-primary volumes
				}
				// DownloadsProvider
				else if (isDownloadsDocument(uri)) {

					final String id = DocumentsContract.getDocumentId(uri);
					final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

					log += "\n isDownloadsDocument(id: " + id + ", contentUri: " + contentUri.toString() + ")";
					log += "\n Returning-DataColumn: " + getDataColumn(context, contentUri, null, null);
					return getDataColumn(context, contentUri, null, null);
				}
				// MediaProvider
				else if (isMediaDocument(uri)) {
					final String docId = DocumentsContract.getDocumentId(uri);
					final String[] split = docId.split(":");
					final String type = split[0];

					Uri contentUri = null;
					if ("image".equals(type)) {
						contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
					} else if ("video".equals(type)) {
						contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
					} else if ("audio".equals(type)) {
						contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
					}

					final String selection = "_id=?";
					final String[] selectionArgs = new String[] { split[1] };

					log += "\n isMediaDocument(docId: " + docId + ", type: " + type + ", contentUri: " + contentUri.toString() + ")";
					log += "\n Returning-DataColumn: " + getDataColumn(context, contentUri, selection, selectionArgs);

					return getDataColumn(context, contentUri, selection, selectionArgs);
				}
			}
			// MediaStore (and general)
			else if ("content".equalsIgnoreCase(uri.getScheme())) {

				// Return the remote address
				if (isGooglePhotosUri(uri)) {
					log += "\n isContent + isGooglePhotosUri";
					log += "\n Returning-LastPathSegment: " + uri.getLastPathSegment();

					return uri.getLastPathSegment();
				}

				log += "\n isContent";
				log += "\n Returning-DataColumn: " + getDataColumn(context, uri, null, null);
				return getDataColumn(context, uri, null, null);
			}
			// File
			else if ("file".equalsIgnoreCase(uri.getScheme())) {
				log += "\n isFile";
				log += "\n Returning-Path: " + uri.getPath();
				return uri.getPath();
			}

			log += "\n Doing lastTryToGetFile()";
			File file = lastTryToGetFile(context, uri);
			if (file != null && file.exists()) {
				log += "\n File found at copied ";
				return file.getAbsolutePath();
			}

			log += "\n NOTHING MATCHED, RETURNING NULL";
		} catch (Exception ex) {

			log += "\n EXCEPTION OCCURED: " + ex.toString();
			throw ex;
		} finally {
			AppModel.WriteLog("Attachment-OnFileSelect-Path", "getPath", log);
		}

		return null;
	}

	// Works for Dropbox, GoogleDrive and other external file hostings
	public static File lastTryToGetFile(Context context, Uri uri) throws Exception {
		if (uri != null) {
			String fileName = DocumentFile.fromSingleUri(context, uri).getName();
			String extension = getExtension(fileName);
			File tempFile = File.createTempFile(UUID.randomUUID().toString(), extension, context.getCacheDir());
			copyStreamToFile(context.getContentResolver().openInputStream(uri), tempFile);

			return tempFile.getAbsoluteFile();
		}
		return null;
	}

	public static void copyStreamToFile(InputStream in, File dest) throws Exception {
		OutputStream out = new FileOutputStream(dest);

		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
	}

	/**
	 * Convert Uri into File, if possible.
	 * 
	 * @return file A local file that the Uri was pointing to, or null if the Uri is unsupported or pointed to a remote resource.
	 * @see #getPath(Context, Uri)
	 * @author paulburke
	 * @throws Exception
	 */
	public static File getFile(Context context, Uri uri) throws Exception {
		if (uri != null) {
			String path = getPath(context, uri);
			if (path != null && isLocal(path)) {
				return new File(path);
			}
		}
		return null;
	}

	/**
	 * Get the file size in a human-readable string.
	 * 
	 * @param size
	 * @return
	 * @author paulburke
	 */
	public static String getReadableFileSize(int size) {
		final int BYTES_IN_KILOBYTES = 1024;
		final DecimalFormat dec = new DecimalFormat("###.#");
		final String KILOBYTES = " KB";
		final String MEGABYTES = " MB";
		final String GIGABYTES = " GB";
		float fileSize = 0;
		String suffix = KILOBYTES;

		if (size > BYTES_IN_KILOBYTES) {
			fileSize = size / BYTES_IN_KILOBYTES;
			if (fileSize > BYTES_IN_KILOBYTES) {
				fileSize = fileSize / BYTES_IN_KILOBYTES;
				if (fileSize > BYTES_IN_KILOBYTES) {
					fileSize = fileSize / BYTES_IN_KILOBYTES;
					suffix = GIGABYTES;
				} else {
					suffix = MEGABYTES;
				}
			}
		}
		return String.valueOf(dec.format(fileSize) + suffix);
	}

	/**
	 * Attempt to retrieve the thumbnail of given File from the MediaStore. This should not be called on the UI thread.
	 * 
	 * @param context
	 * @param file
	 * @return
	 * @author paulburke
	 */
	public static Bitmap getThumbnail(Context context, File file) {
		return getThumbnail(context, getUri(file), getMimeType(file));
	}

	/**
	 * Attempt to retrieve the thumbnail of given Uri from the MediaStore. This should not be called on the UI thread.
	 * 
	 * @param context
	 * @param uri
	 * @return
	 * @author paulburke
	 * @throws Exception
	 */
	public static Bitmap getThumbnail(Context context, Uri uri) throws Exception {
		return getThumbnail(context, uri, getMimeType(context, uri));
	}

	/**
	 * Attempt to retrieve the thumbnail of given Uri from the MediaStore. This should not be called on the UI thread.
	 * 
	 * @param context
	 * @param uri
	 * @param mimeType
	 * @return
	 * @author paulburke
	 */
	public static Bitmap getThumbnail(Context context, Uri uri, String mimeType) {
		if (DEBUG)
			Log.d(TAG, "Attempting to get thumbnail");

		if (!isMediaUri(uri)) {
			Log.e(TAG, "You can only retrieve thumbnails for images and videos.");
			return null;
		}

		Bitmap bm = null;
		if (uri != null) {
			final ContentResolver resolver = context.getContentResolver();
			Cursor cursor = null;
			try {
				cursor = resolver.query(uri, null, null, null, null);
				if (cursor.moveToFirst()) {
					final int id = cursor.getInt(0);
					if (DEBUG)
						Log.d(TAG, "Got thumb ID: " + id);

					if (mimeType.contains("video")) {
						bm = MediaStore.Video.Thumbnails.getThumbnail(resolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null);
					} else if (mimeType.contains(AnyFilePathUtil.MIME_TYPE_IMAGE)) {
						bm = MediaStore.Images.Thumbnails.getThumbnail(resolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null);
					}
				}
			} catch (Exception e) {
				if (DEBUG)
					Log.e(TAG, "getThumbnail", e);
			} finally {
				if (cursor != null)
					cursor.close();
			}
		}
		return bm;
	}

	/**
	 * File and folder comparator. TODO Expose sorting option method
	 * 
	 * @author paulburke
	 */
	public static Comparator<File> sComparator = new Comparator<File>() {
		@Override
		public int compare(File f1, File f2) {
			// Sort alphabetically by lower case, which is much cleaner
			return f1.getName().toLowerCase().compareTo(f2.getName().toLowerCase());
		}
	};

	/**
	 * File (not directories) filter.
	 * 
	 * @author paulburke
	 */
	public static FileFilter sFileFilter = new FileFilter() {
		@Override
		public boolean accept(File file) {
			final String fileName = file.getName();
			// Return files only (not directories) and skip hidden files
			return file.isFile() && !fileName.startsWith(HIDDEN_PREFIX);
		}
	};

	/**
	 * Folder (directories) filter.
	 * 
	 * @author paulburke
	 */
	public static FileFilter sDirFilter = new FileFilter() {
		@Override
		public boolean accept(File file) {
			final String fileName = file.getName();
			// Return directories only and skip hidden directories
			return file.isDirectory() && !fileName.startsWith(HIDDEN_PREFIX);
		}
	};

	/**
	 * Get the Intent for selecting content to be used in an Intent Chooser.
	 * 
	 * @return The intent for opening a file with Intent.createChooser()
	 * @author paulburke
	 */
	public static Intent createGetContentIntent() {
		// Implicitly allow the user to select a particular kind of data
		final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		// The MIME data type filter
		intent.setType("*/*");
		// Only return URIs that can be opened with ContentResolver
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		return intent;
	}
	
	public class LocalStorageProvider extends DocumentsProvider {

		public static final String AUTHORITY = "com.ianhanniballake.localstorage.documents";

		/**
		 * Default root projection: everything but Root.COLUMN_MIME_TYPES
		 */
		private final String[] DEFAULT_ROOT_PROJECTION = new String[] { Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_TITLE, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_ICON, Root.COLUMN_AVAILABLE_BYTES };
		/**
		 * Default document projection: everything but Document.COLUMN_ICON and Document.COLUMN_SUMMARY
		 */
		private final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] { Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_FLAGS, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED };

		// @Override
		// public Cursor queryRoots(final String[] projection) throws FileNotFoundException {
		// // Create a cursor with either the requested fields, or the default
		// // projection if "projection" is null.
		// final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_ROOT_PROJECTION);
		// // Add Home directory
		// File homeDir = Environment.getExternalStorageDirectory();
		// final MatrixCursor.RowBuilder row = result.newRow();
		// // These columns are required
		// row.add(Root.COLUMN_ROOT_ID, homeDir.getAbsolutePath());
		// row.add(Root.COLUMN_DOCUMENT_ID, homeDir.getAbsolutePath());
		// row.add(Root.COLUMN_TITLE, getContext().getString(R.string.internal_storage));
		// row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_CREATE);
		// row.add(Root.COLUMN_ICON, R.drawable.ic_provider);
		// // These columns are optional
		// row.add(Root.COLUMN_AVAILABLE_BYTES, homeDir.getFreeSpace());
		// // Root.COLUMN_MIME_TYPE is another optional column and useful if you
		// // have multiple roots with different
		// // types of mime types (roots that don't match the requested mime type
		// // are automatically hidden)
		// return result;
		// }

		@Override
		public String createDocument(final String parentDocumentId, final String mimeType, final String displayName) throws FileNotFoundException {
			File newFile = new File(parentDocumentId, displayName);
			try {
				newFile.createNewFile();
				return newFile.getAbsolutePath();
			} catch (IOException e) {
				Log.e(LocalStorageProvider.class.getSimpleName(), "Error creating new file " + newFile);
			}
			return null;
		}

		@Override
		public AssetFileDescriptor openDocumentThumbnail(final String documentId, final Point sizeHint, final CancellationSignal signal) throws FileNotFoundException {
			// Assume documentId points to an image file. Build a thumbnail no
			// larger than twice the sizeHint
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(documentId, options);
			final int targetHeight = 2 * sizeHint.y;
			final int targetWidth = 2 * sizeHint.x;
			final int height = options.outHeight;
			final int width = options.outWidth;
			options.inSampleSize = 1;
			if (height > targetHeight || width > targetWidth) {
				final int halfHeight = height / 2;
				final int halfWidth = width / 2;
				// Calculate the largest inSampleSize value that is a power of 2 and
				// keeps both
				// height and width larger than the requested height and width.
				while ((halfHeight / options.inSampleSize) > targetHeight || (halfWidth / options.inSampleSize) > targetWidth) {
					options.inSampleSize *= 2;
				}
			}
			options.inJustDecodeBounds = false;
			Bitmap bitmap = BitmapFactory.decodeFile(documentId, options);
			// Write out the thumbnail to a temporary file
			File tempFile = null;
			FileOutputStream out = null;
			try {
				tempFile = File.createTempFile("thumbnail", null, getContext().getCacheDir());
				out = new FileOutputStream(tempFile);
				bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
			} catch (IOException e) {
				Log.e(LocalStorageProvider.class.getSimpleName(), "Error writing thumbnail", e);
				return null;
			} finally {
				if (out != null)
					try {
						out.close();
					} catch (IOException e) {
						Log.e(LocalStorageProvider.class.getSimpleName(), "Error closing thumbnail", e);
					}
			}
			// It appears the Storage Framework UI caches these results quite
			// aggressively so there is little reason to
			// write your own caching layer beyond what you need to return a single
			// AssetFileDescriptor
			return new AssetFileDescriptor(ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
		}

		@Override
		public Cursor queryChildDocuments(final String parentDocumentId, final String[] projection, final String sortOrder) throws FileNotFoundException {
			// Create a cursor with either the requested fields, or the default
			// projection if "projection" is null.
			final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
			final File parent = new File(parentDocumentId);
			for (File file : parent.listFiles()) {
				// Don't show hidden files/folders
				if (!file.getName().startsWith(".")) {
					// Adds the file's display name, MIME type, size, and so on.
					includeFile(result, file);
				}
			}
			return result;
		}

		@Override
		public Cursor queryDocument(final String documentId, final String[] projection) throws FileNotFoundException {
			// Create a cursor with either the requested fields, or the default
			// projection if "projection" is null.
			final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
			includeFile(result, new File(documentId));
			return result;
		}

		private void includeFile(final MatrixCursor result, final File file) throws FileNotFoundException {
			final MatrixCursor.RowBuilder row = result.newRow();
			// These columns are required
			row.add(Document.COLUMN_DOCUMENT_ID, file.getAbsolutePath());
			row.add(Document.COLUMN_DISPLAY_NAME, file.getName());
			String mimeType = getDocumentType(file.getAbsolutePath());
			row.add(Document.COLUMN_MIME_TYPE, mimeType);
			int flags = file.canWrite() ? Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_WRITE : 0;
			// We only show thumbnails for image files - expect a call to
			// openDocumentThumbnail for each file that has
			// this flag set
			if (mimeType.startsWith("image/"))
				flags |= Document.FLAG_SUPPORTS_THUMBNAIL;
			row.add(Document.COLUMN_FLAGS, flags);
			// COLUMN_SIZE is required, but can be null
			row.add(Document.COLUMN_SIZE, file.length());
			// These columns are optional
			row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
			// Document.COLUMN_ICON can be a resource id identifying a custom icon.
			// The system provides default icons
			// based on mime type
			// Document.COLUMN_SUMMARY is optional additional information about the
			// file
		}

		@Override
		public String getDocumentType(final String documentId) throws FileNotFoundException {
			File file = new File(documentId);
			if (file.isDirectory())
				return Document.MIME_TYPE_DIR;
			// From FileProvider.getType(Uri)
			final int lastDot = file.getName().lastIndexOf('.');
			if (lastDot >= 0) {
				final String extension = file.getName().substring(lastDot + 1);
				final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
				if (mime != null) {
					return mime;
				}
			}
			return "application/octet-stream";
		}

		@Override
		public void deleteDocument(final String documentId) throws FileNotFoundException {
			new File(documentId).delete();
		}

		@Override
		public ParcelFileDescriptor openDocument(final String documentId, final String mode, final CancellationSignal signal) throws FileNotFoundException {
			File file = new File(documentId);
			final boolean isWrite = (mode.indexOf('w') != -1);
			if (isWrite) {
				return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
			} else {
				return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
			}
		}

		@Override
		public boolean onCreate() {
			return true;
		}

		@Override
		public Cursor queryRoots(String[] arg0) throws FileNotFoundException {
			return null;
		}
	}
}
