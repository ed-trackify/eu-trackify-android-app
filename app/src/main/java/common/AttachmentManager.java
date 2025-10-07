package common;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import com.svenkapudija.imageresizer.ImageResizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import common.AppModel.AppFolderType;

public class AttachmentManager {

    public static String AttachFileName;
    public static File AttachFile;
    public final static int CAMERA_REQUEST = 2453;
    public final static int FILE_REQUEST = 2285;
    public static Runnable onAttachSuccess = null;

    public static Bitmap GetPicture(String fileName) {
        File f = GetFile(fileName);
        return f != null && f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    public static File GetFile(String fileName) {
        if (!AppModel.IsNullOrEmpty(fileName)) {
            File f = new File(AppModel.GetAppFolder(AppFolderType.Images), fileName);
            return f.exists() ? f : null;
        }
        return null;
    }

    static Uri photoUri;

    public static void AttachImage(Runnable onAttach) {
        onAttachSuccess = onAttach;

        AttachFileName = UUID.randomUUID().toString() + ".jpg";

        File imageDir = new File(App.Object.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "");
        if (!imageDir.exists()) imageDir.mkdirs();

        AttachFile = new File(imageDir, AttachFileName);

        photoUri = FileProvider.getUriForFile(App.Object, App.Object.getPackageName() + ".fileprovider", AttachFile);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        App.Object.cameraLauncher.launch(intent);
    }

    public static void AttachFile(Runnable onAttach) {
        onAttachSuccess = onAttach;

        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.setType("*/*");
        chooseFile = Intent.createChooser(chooseFile, "Choose file to attach");
        App.Object.filePickerLauncher.launch(chooseFile);
    }

    public static void onImageCaptured() {
        try {
            if (AttachFile.exists()) {
                Bitmap resized = ImageResizer.resize(AttachFile, 600, 600);
                ImageResizer.saveToFile(resized, AttachFile);
                onAttachSuccess.run();
            } else
                MessageCtrl.Toast("File not found");
        } catch (Exception e) {
            AppModel.ApplicationError(e, "onImageCaptured");
            MessageCtrl.Toast("File not found");
        }
    }

    public static void OnFileSelect(Context context, Uri uri, Runnable onAttach) {
        try {
            File selectedFile = null;

            // Read file into temp file (works for all content:// or file:// Uris)
            String fileName = getFileName(context, uri);
            if (fileName == null) {
                fileName = "selected_file";
            }

            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                File tempDir = context.getCacheDir();
                selectedFile = new File(tempDir, fileName);

                OutputStream outputStream = new FileOutputStream(selectedFile);
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();
            }

            // Your logic
            if (selectedFile == null || !selectedFile.exists()) {
                MessageCtrl.Toast("File not found");
                return;
            }

            String extension = AnyFilePathUtil.getExtension(selectedFile.getAbsolutePath());
            AttachFileName = UUID.randomUUID().toString() + extension;
            AttachFile = new File(AppModel.GetAppFolder(AppFolderType.Images), AttachFileName);

            CopyFileToAppDirectory(selectedFile, AttachFile);

            onAttach.run();
        } catch (Exception e) {
            AppModel.ApplicationError(e, "onFileSelect");
            MessageCtrl.Toast("File selection failed");
        }
    }

    private static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    public static void OnFileSelect(Intent data) {
        try {
            File selectedFile = new File(data.getData().getPath());
            // if (!selectedFile.exists())
            // selectedFile = new File(getRealPathFromURI(data.getData()));
            if (!selectedFile.exists())
                selectedFile = AnyFilePathUtil.getFile(App.Object, data.getData());

            if (selectedFile == null || !selectedFile.exists())
                MessageCtrl.Toast("File not found");
            else {

                AttachFileName = UUID.randomUUID().toString() + AnyFilePathUtil.getExtension(selectedFile.getAbsolutePath());
                AttachFile = new File(AppModel.GetAppFolder(AppFolderType.Images), AttachFileName);

                CopyFileToAppDirectory(selectedFile, AttachFile);
                onAttachSuccess.run();
            }
        } catch (Exception e) {
            AppModel.ApplicationError(e, "onSelectFromGalleryResult");
            MessageCtrl.Toast("File selection failed");
        }
    }

    public static String GetMimeType(Uri uri) {
        String mimeType = null;
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver cr = App.Object.getContentResolver();
            mimeType = cr.getType(uri);
        } else {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
        }
        return mimeType;
    }

    public static boolean CopyFileToAppDirectory(File source, File dest) {
        try {
            InputStream in = new FileInputStream(source);

            OutputStream out = new FileOutputStream(dest);

            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();

            return true;
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "AttachmentManager::CopyFileToAppDirectory");
        }
        return false;
    }

    // http://stackoverflow.com/questions/15373459/android-activity-with-intent-action-send-gets-uri-which-cannot-be-open
    // NEW::::::
    // http://stackoverflow.com/questions/20067508/get-real-path-from-uri-android-kitkat-new-storage-access-framework?answertab=active#tab-top
    // Note: This happens on Nexus 7 which I'm using to test.
    public static String getRealPathFromURI(Uri contentUri) {
        try {
            Cursor cursor = App.Object.getContentResolver().query(contentUri, null, null, null, null);
            cursor.moveToFirst();
            String document_id = cursor.getString(0);
            document_id = document_id.substring(document_id.lastIndexOf(":") + 1);
            cursor.close();

            // cursor = App.Object.getContentResolver().query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, MediaStore.Images.Media._ID + " = ? ", new String[] { document_id }, null);
            cursor = App.Object.getContentResolver().query(android.provider.MediaStore.Files.getContentUri("external"), null, MediaColumns._ID + " = ? ", new String[]{document_id}, null);
            cursor.moveToFirst();
            // String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            String path = cursor.getString(cursor.getColumnIndex(MediaColumns.DATA));
            cursor.close();

            return path;
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "AttachmentManager::getRealPathFromURI");
            return "";
        }
    }

    private static String getAudioFilePathFromUri(Uri uri) {
        Cursor cursor = App.Object.getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        int index = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DATA);
        return cursor.getString(index);
    }

    private static boolean CopyFile(File source, File dest) {
        try {
            InputStream in = new FileInputStream(source);

            OutputStream out = new FileOutputStream(dest);

            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();

            return true;
        } catch (Exception ex) {
            AppModel.ApplicationError(ex, "AttachmentManager::CopyFile");
        }
        return false;
    }
}
