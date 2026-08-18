package com.inchanlabs.cordova.customcamera;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.core.content.FileProvider;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Simplified custom Cordova Camera plugin.
 *
 * Current purpose:
 *
 * Android Camera
 *      ↓
 * FileProvider
 *      ↓
 * JPEG file
 *      ↓
 * Base64
 *      ↓
 * callbackContext.success()
 *      ↓
 * navigator.camera.getPicture(success, error, options)
 */
public class CameraLauncher extends CordovaPlugin {

    private static final String LOG_TAG = "ChristianCustomCamera";

    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final int CAMERA_ACTIVITY_REQUEST = 1002;

    private static final String ACTION_TAKE_PICTURE = "takePicture";
    private static final String ACTION_STOP = "stop";

    private CallbackContext callbackContext;
    private Uri imageUri;
    private File imageFile;

    @Override
    public boolean execute(
            String action,
            JSONArray args,
            CallbackContext callbackContext) throws JSONException {

        LOG.d(LOG_TAG, "========================================");
        LOG.d(LOG_TAG, "execute()");
        LOG.d(LOG_TAG, "Action = " + action);
        LOG.d(LOG_TAG, "========================================");

        this.callbackContext = callbackContext;

        if (ACTION_TAKE_PICTURE.equals(action)) {

            LOG.d(LOG_TAG, "Take picture requested");

            checkCameraPermission();

            return true;
        }

        if (ACTION_STOP.equals(action)) {

            LOG.d(LOG_TAG, "Stop requested");

            callbackContext.success();

            return true;
        }

        LOG.d(LOG_TAG, "Unknown action: " + action);

        return false;
    }

    /**
     * Check/request camera permission.
     */
    private void checkCameraPermission() {

        LOG.d(LOG_TAG, "Checking CAMERA permission");

        boolean granted = PermissionHelper.hasPermission(
                this,
                Manifest.permission.CAMERA
        );

        LOG.d(
                LOG_TAG,
                "CAMERA permission granted = " + granted
        );

        if (!granted) {

            LOG.d(
                    LOG_TAG,
                    "Requesting CAMERA permission"
            );

            PermissionHelper.requestPermission(
                    this,
                    CAMERA_PERMISSION_REQUEST,
                    Manifest.permission.CAMERA
            );

            return;
        }

        LOG.d(
                LOG_TAG,
                "CAMERA permission already granted"
        );

        openCamera();
    }

    /**
     * Open Android camera.
     */
    private void openCamera() {

        LOG.d(LOG_TAG, "========================================");
        LOG.d(LOG_TAG, "openCamera()");
        LOG.d(LOG_TAG, "========================================");

        if (cordova == null || cordova.getActivity() == null) {

            LOG.e(
                    LOG_TAG,
                    "Cordova activity is NULL"
            );

            fail("Cordova activity is unavailable");

            return;
        }

        Activity activity = cordova.getActivity();

        PackageManager packageManager =
                activity.getPackageManager();

        Intent cameraIntent =
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (cameraIntent.resolveActivity(packageManager) == null) {

            LOG.e(
                    LOG_TAG,
                    "No camera application found"
            );

            fail("No camera application available");

            return;
        }

        try {

            /*
             * Create temporary image file.
             */
            imageFile = createImageFile();

            LOG.d(
                    LOG_TAG,
                    "Image file = " +
                            imageFile.getAbsolutePath()
            );

            /*
             * FileProvider authority must exactly match
             * plugin.xml.
             */
            String authority =
                    activity.getPackageName()
                            + ".cordova.plugin.camera.provider";

            LOG.d(
                    LOG_TAG,
                    "FileProvider authority = " +
                            authority
            );

            imageUri =
                    FileProvider.getUriForFile(
                            activity,
                            authority,
                            imageFile
                    );

            LOG.d(
                    LOG_TAG,
                    "Image URI = " +
                            imageUri.toString()
            );

            /*
             * Tell camera where to write the image.
             */
            cameraIntent.putExtra(
                    MediaStore.EXTRA_OUTPUT,
                    imageUri
            );

            /*
             * Give camera permission to write to our URI.
             */
            cameraIntent.addFlags(
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );

            cameraIntent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            /*
             * Some camera applications need explicit
             * URI permissions.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                cameraIntent.setClipData(
                        android.content.ClipData.newRawUri(
                                "output",
                                imageUri
                        )
                );
            }

            LOG.d(
                    LOG_TAG,
                    "Starting camera activity..."
            );

            cordova.startActivityForResult(
                    this,
                    cameraIntent,
                    CAMERA_ACTIVITY_REQUEST
            );

            LOG.d(
                    LOG_TAG,
                    "Camera activity started"
            );

        } catch (Exception e) {

            LOG.e(
                    LOG_TAG,
                    "Failed to start camera: " +
                            e.getMessage()
            );

            e.printStackTrace();

            fail(
                    "Unable to start camera: " +
                            e.getMessage()
            );
        }
    }

    /**
     * Create temporary JPEG file.
     */
    private File createImageFile() throws IOException {

        File directory =
                new File(
                        cordova.getActivity().getCacheDir(),
                        "custom-camera"
                );

        if (!directory.exists()) {

            boolean created =
                    directory.mkdirs();

            LOG.d(
                    LOG_TAG,
                    "Created camera directory = " +
                            created
            );
        }

        String fileName =
                "IMG_" +
                        System.currentTimeMillis() +
                        ".jpg";

        File file =
                new File(
                        directory,
                        fileName
                );

        LOG.d(
                LOG_TAG,
                "Created image file path = " +
                        file.getAbsolutePath()
        );

        return file;
    }

    /**
     * Called after camera activity finishes.
     */
    @Override
    public void onActivityResult(
            int requestCode,
            int resultCode,
            Intent intent) {

        LOG.d(LOG_TAG, "========================================");
        LOG.d(LOG_TAG, "onActivityResult()");
        LOG.d(LOG_TAG, "requestCode = " + requestCode);
        LOG.d(LOG_TAG, "resultCode = " + resultCode);
        LOG.d(
                LOG_TAG,
                "intent = " +
                        (intent == null ? "NULL" : "NOT NULL")
        );
        LOG.d(
                LOG_TAG,
                "imageUri = " +
                        (imageUri == null
                                ? "NULL"
                                : imageUri.toString())
        );
        LOG.d(
                LOG_TAG,
                "imageFile = " +
                        (imageFile == null
                                ? "NULL"
                                : imageFile.getAbsolutePath())
        );
        LOG.d(LOG_TAG, "========================================");

        if (requestCode != CAMERA_ACTIVITY_REQUEST) {

            LOG.d(
                    LOG_TAG,
                    "Ignoring unrelated requestCode"
            );

            return;
        }

        /*
         * User cancelled camera.
         */
        if (resultCode == Activity.RESULT_CANCELED) {

            LOG.d(
                    LOG_TAG,
                    "Camera was cancelled"
            );

            fail("No Image Selected");

            return;
        }

        /*
         * Camera did not return OK.
         */
        if (resultCode != Activity.RESULT_OK) {

            LOG.e(
                    LOG_TAG,
                    "Camera returned unexpected resultCode = " +
                            resultCode
            );

            fail(
                    "Camera did not complete. resultCode=" +
                            resultCode
            );

            return;
        }

        LOG.d(
                LOG_TAG,
                "Camera returned RESULT_OK"
        );

        /*
         * IMPORTANT:
         *
         * Because we used EXTRA_OUTPUT, the camera normally
         * writes directly to imageFile and does NOT put the
         * image in intent.getData().
         *
         * Therefore intent can legitimately be NULL.
         */
        if (imageFile == null) {

            LOG.e(
                    LOG_TAG,
                    "imageFile is NULL after camera result"
            );

            fail("Captured image file is NULL");

            return;
        }

        if (!imageFile.exists()) {

            LOG.e(
                    LOG_TAG,
                    "Captured image file does not exist"
            );

            fail(
                    "Captured image file does not exist: " +
                            imageFile.getAbsolutePath()
            );

            return;
        }

        long fileSize =
                imageFile.length();

        LOG.d(
                LOG_TAG,
                "Captured image file size = " +
                        fileSize +
                        " bytes"
        );

        if (fileSize <= 0) {

            LOG.e(
                    LOG_TAG,
                    "Captured image file is EMPTY"
            );

            fail("Captured image file is empty");

            return;
        }

        /*
         * Process image on Cordova thread pool.
         */
        cordova.getThreadPool().execute(
                new Runnable() {

                    @Override
                    public void run() {

                        processCapturedImage();
                    }
                }
        );
    }

    /**
     * Read captured JPEG and return Base64.
     */
    private void processCapturedImage() {

        LOG.d(LOG_TAG, "========================================");
        LOG.d(LOG_TAG, "processCapturedImage()");
        LOG.d(LOG_TAG, "========================================");

        if (callbackContext == null) {

            LOG.e(
                    LOG_TAG,
                    "callbackContext is NULL"
            );

            return;
        }

        if (imageFile == null) {

            LOG.e(
                    LOG_TAG,
                    "imageFile is NULL"
            );

            fail("Image file is NULL");

            return;
        }

        if (!imageFile.exists()) {

            LOG.e(
                    LOG_TAG,
                    "Image file no longer exists"
            );

            fail("Image file no longer exists");

            return;
        }

        try {

            byte[] imageBytes =
                    readFile(imageFile);

            LOG.d(
                    LOG_TAG,
                    "Image bytes read = " +
                            imageBytes.length
            );

            if (imageBytes.length == 0) {

                LOG.e(
                        LOG_TAG,
                        "Image bytes are empty"
                );

                fail("Image data is empty");

                return;
            }

            /*
             * Convert JPEG bytes to Base64.
             */
            String base64 =
                    Base64.encodeToString(
                            imageBytes,
                            Base64.NO_WRAP
                    );

            LOG.d(
                    LOG_TAG,
                    "Base64 generated"
            );

            LOG.d(
                    LOG_TAG,
                    "Base64 length = " +
                            base64.length()
            );

            /*
             * IMPORTANT:
             *
             * We return the complete data URI.
             *
             * Example:
             *
             * data:image/jpeg;base64,/9j/4AAQ...
             */
            String result =
                    "data:image/jpeg;base64," +
                            base64;

            LOG.d(
                    LOG_TAG,
                    "Result length = " +
                            result.length()
            );

            LOG.d(
                    LOG_TAG,
                    "Sending callbackContext.success()"
            );

            callbackContext.success(result);

            LOG.d(
                    LOG_TAG,
                    "callbackContext.success() completed"
            );

            /*
             * Delete temporary image after successful
             * Base64 conversion.
             */
            try {

                boolean deleted =
                        imageFile.delete();

                LOG.d(
                        LOG_TAG,
                        "Temporary image deleted = " +
                                deleted
                );

            } catch (Exception cleanupException) {

                LOG.e(
                        LOG_TAG,
                        "Unable to delete temporary image: " +
                                cleanupException.getMessage()
                );
            }

        } catch (Exception e) {

            LOG.e(
                    LOG_TAG,
                    "Error processing captured image: " +
                            e.getMessage()
            );

            e.printStackTrace();

            fail(
                    "Error processing captured image: " +
                            e.getMessage()
            );
        }
    }

    /**
     * Read complete file into byte array.
     */
    private byte[] readFile(File file)
            throws IOException {

        LOG.d(
                LOG_TAG,
                "Reading file: " +
                        file.getAbsolutePath()
        );

        InputStream inputStream =
                null;

        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        try {

            inputStream =
                    new FileInputStream(file);

            byte[] buffer =
                    new byte[8192];

            int bytesRead;

            while (
                    (bytesRead =
                            inputStream.read(buffer)) != -1
            ) {

                outputStream.write(
                        buffer,
                        0,
                        bytesRead
                );
            }

            return outputStream.toByteArray();

        } finally {

            if (inputStream != null) {

                try {

                    inputStream.close();

                } catch (IOException ignored) {
                }
            }

            try {

                outputStream.close();

            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Camera permission result.
     */
    @Override
    public void onRequestPermissionResult(
            int requestCode,
            String[] permissions,
            int[] grantResults)
            throws JSONException {

        LOG.d(
                LOG_TAG,
                "========================================"
        );

        LOG.d(
                LOG_TAG,
                "onRequestPermissionResult()"
        );

        LOG.d(
                LOG_TAG,
                "requestCode = " +
                        requestCode
        );

        if (
                requestCode ==
                        CAMERA_PERMISSION_REQUEST
        ) {

            if (
                    grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED
            ) {

                LOG.d(
                        LOG_TAG,
                        "CAMERA permission GRANTED"
                );

                openCamera();

            } else {

                LOG.e(
                        LOG_TAG,
                        "CAMERA permission DENIED"
                );

                fail(
                        "Camera permission denied"
                );
            }

            return;
        }

        LOG.d(
                LOG_TAG,
                "Unknown permission request"
        );
    }

    /**
     * Stop camera.
     */
    private void stopCamera() {

        LOG.d(
                LOG_TAG,
                "stopCamera()"
        );

        /*
         * Nothing special is required here because the
         * Android camera Activity owns its own lifecycle.
         */
    }

    /**
     * Send error to JavaScript.
     */
    private void fail(String message) {

        LOG.e(
                LOG_TAG,
                "FAIL: " +
                        message
        );

        if (callbackContext != null) {

            callbackContext.error(message);

        } else {

            LOG.e(
                    LOG_TAG,
                    "Cannot send error because callbackContext is NULL"
            );
        }
    }

    /**
     * Save state if Android kills/recreates the Activity.
     */
    @Override
    public Bundle onSaveInstanceState() {

        LOG.d(
                LOG_TAG,
                "onSaveInstanceState()"
        );

        Bundle state =
                new Bundle();

        if (imageUri != null) {

            state.putString(
                    "imageUri",
                    imageUri.toString()
            );
        }

        if (imageFile != null) {

            state.putString(
                    "imageFile",
                    imageFile.getAbsolutePath()
            );
        }

        return state;
    }

    /**
     * Restore state before activity result.
     */
    @Override
    public void onRestoreStateForActivityResult(
            Bundle state,
            CallbackContext callbackContext) {

        LOG.d(
                LOG_TAG,
                "onRestoreStateForActivityResult()"
        );

        this.callbackContext =
                callbackContext;

        if (state == null) {

            LOG.d(
                    LOG_TAG,
                    "State is NULL"
            );

            return;
        }

        if (state.containsKey("imageUri")) {

            String uriString =
                    state.getString("imageUri");

            if (uriString != null) {

                imageUri =
                        Uri.parse(uriString);
            }
        }

        if (state.containsKey("imageFile")) {

            String filePath =
                    state.getString("imageFile");

            if (filePath != null) {

                imageFile =
                        new File(filePath);
            }
        }

        LOG.d(
                LOG_TAG,
                "Restored imageUri = " +
                        (
                                imageUri == null
                                        ? "NULL"
                                        : imageUri.toString()
                        )
        );

        LOG.d(
                LOG_TAG,
                "Restored imageFile = " +
                        (
                                imageFile == null
                                        ? "NULL"
                                        : imageFile.getAbsolutePath()
                        )
        );
    }
}
