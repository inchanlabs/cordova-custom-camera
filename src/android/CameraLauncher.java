/*
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.

    Licensed under the Apache License, Version 2.0.
*/
package com.inchanlabs.cordova.customcamera;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class CameraLauncher extends CordovaPlugin
        implements MediaScannerConnectionClient {

    private static final int DATA_URL = 0;
    private static final int FILE_URI = 1;

    private static final int PHOTOLIBRARY = 0;
    private static final int CAMERA = 1;
    private static final int SAVEDPHOTOALBUM = 2;

    private static final int PICTURE = 0;
    private static final int VIDEO = 1;
    private static final int ALLMEDIA = 2;

    private static final int JPEG = 0;
    private static final int PNG = 1;

    private static final String JPEG_TYPE = "jpg";
    private static final String PNG_TYPE = "png";

    private static final String JPEG_EXTENSION = "." + JPEG_TYPE;
    private static final String PNG_EXTENSION = "." + PNG_TYPE;

    private static final String PNG_MIME_TYPE = "image/png";
    private static final String JPEG_MIME_TYPE = "image/jpeg";
    private static final String HEIC_MIME_TYPE = "image/heic";

    private static final String GET_PICTURE = "Get Picture";
    private static final String GET_VIDEO = "Get Video";
    private static final String GET_All = "Get All";

    private static final String CROPPED_URI_KEY = "croppedUri";
    private static final String IMAGE_URI_KEY = "imageUri";

    private static final String TAKE_PICTURE_ACTION = "takePicture";
    private static final String STOP_ACTION = "stop";

    public static final int PERMISSION_DENIED_ERROR = 20;
    public static final int TAKE_PIC_SEC = 0;
    public static final int SAVE_TO_ALBUM_SEC = 1;

    private static final String LOG_TAG = "CustomCamera";

    private static final int CROP_CAMERA = 100;

    private static final String TIME_FORMAT = "yyyyMMdd_HHmmss";

    private int mQuality;
    private int targetWidth;
    private int targetHeight;

    private Uri imageUri;

    private int encodingType;
    private int mediaType;
    private int destType;
    private int srcType;

    private boolean saveToPhotoAlbum;
    private boolean correctOrientation;
    private boolean orientationCorrected;
    private boolean allowEdit;

    public CallbackContext callbackContext;

    private MediaScannerConnection conn;
    private Uri scanMe;

    private Uri croppedUri;
    private String croppedFilePath;

    private ExifHelper exifData;

    private String applicationId;


    // ============================================================
    // EXECUTE
    // ============================================================

    @Override
    public boolean execute(
            String action,
            JSONArray args,
            CallbackContext callbackContext) throws JSONException {

        this.applicationId =
                cordova.getContext().getPackageName();

        this.applicationId =
                preferences.getString(
                        "applicationId",
                        this.applicationId
                );

        LOG.d(
                LOG_TAG,
                "execute() action=" + action +
                        " package=" + this.applicationId
        );

        if (action.equals(TAKE_PICTURE_ACTION)) {

            LOG.d(
                    LOG_TAG,
                    "takePicture action reached"
            );

            this.callbackContext = callbackContext;

            this.srcType = CAMERA;
            this.destType = FILE_URI;

            this.saveToPhotoAlbum = false;

            this.targetHeight = 0;
            this.targetWidth = 0;

            this.encodingType = JPEG;
            this.mediaType = PICTURE;
            this.mQuality = 50;

            if (args != null && args.length() >= 10) {

                this.mQuality = args.getInt(0);
                this.destType = args.getInt(1);
                this.srcType = args.getInt(2);
                this.targetWidth = args.getInt(3);
                this.targetHeight = args.getInt(4);
                this.encodingType = args.getInt(5);
                this.mediaType = args.getInt(6);
                this.allowEdit = args.getBoolean(7);
                this.correctOrientation = args.getBoolean(8);
                this.saveToPhotoAlbum = args.getBoolean(9);

            } else {

                LOG.d(
                        LOG_TAG,
                        "Arguments missing or incomplete. Using defaults."
                );
            }

            LOG.d(
                    LOG_TAG,
                    "Camera parameters: " +
                            "quality=" + mQuality +
                            ", destType=" + destType +
                            ", srcType=" + srcType +
                            ", width=" + targetWidth +
                            ", height=" + targetHeight +
                            ", encoding=" + encodingType +
                            ", mediaType=" + mediaType +
                            ", allowEdit=" + allowEdit +
                            ", correctOrientation=" + correctOrientation +
                            ", saveToAlbum=" + saveToPhotoAlbum
            );

            if (targetWidth < 1) {
                targetWidth = -1;
            }

            if (targetHeight < 1) {
                targetHeight = -1;
            }

            if (targetHeight == -1 &&
                    targetWidth == -1 &&
                    mQuality == 100 &&
                    !correctOrientation &&
                    encodingType == PNG &&
                    srcType == CAMERA) {

                encodingType = JPEG;
            }

            try {

                if (srcType == CAMERA) {

                    LOG.d(
                            LOG_TAG,
                            "Calling callTakePicture()"
                    );

                    callTakePicture(
                            destType,
                            encodingType
                    );

                } else if (
                        srcType == PHOTOLIBRARY ||
                        srcType == SAVEDPHOTOALBUM) {

                    getImage(
                            srcType,
                            destType
                    );
                }

            } catch (IllegalStateException e) {

                LOG.e(
                        LOG_TAG,
                        "IllegalStateException: " +
                                e.getMessage(),
                        e
                );

                callbackContext.error(
                        e.getLocalizedMessage()
                );

                return true;

            } catch (IllegalArgumentException e) {

                LOG.e(
                        LOG_TAG,
                        "IllegalArgumentException",
                        e
                );

                callbackContext.error(
                        "Illegal Argument Exception"
                );

                return true;
            }

            PluginResult result =
                    new PluginResult(
                            PluginResult.Status.NO_RESULT
                    );

            result.setKeepCallback(true);

            callbackContext.sendPluginResult(result);

            LOG.d(
                    LOG_TAG,
                    "NO_RESULT sent. Waiting for camera activity result."
            );

            return true;
        }

        if (action.equals(STOP_ACTION)) {

            stopCamera();

            callbackContext.success();

            return true;
        }

        return false;
    }


    // ============================================================
    // TEMP DIRECTORY
    // ============================================================

    private String getTempDirectoryPath() {

        File cache =
                cordova.getActivity().getCacheDir();

        cache.mkdirs();

        return cache.getAbsolutePath();
    }


    // ============================================================
    // CAMERA PERMISSION
    // ============================================================

    public void callTakePicture(
            int returnType,
            int encodingType) throws IllegalStateException {

        LOG.d(
                LOG_TAG,
                "callTakePicture() returnType=" +
                        returnType +
                        " encodingType=" +
                        encodingType
        );

        boolean manifestContainsCameraPermission = false;

        boolean manifestContainsWriteExternalPermission = false;

        boolean cameraPermissionGranted =
                PermissionHelper.hasPermission(
                        this,
                        Manifest.permission.CAMERA
                );

        boolean writeExternalPermissionGranted;

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {

            writeExternalPermissionGranted =
                    PermissionHelper.hasPermission(
                            this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    );

        } else {

            writeExternalPermissionGranted = true;
        }

        try {

            PackageManager packageManager =
                    cordova.getActivity()
                            .getPackageManager();

            String[] permissions =
                    packageManager
                            .getPackageInfo(
                                    cordova.getActivity()
                                            .getPackageName(),
                                    PackageManager.GET_PERMISSIONS
                            )
                            .requestedPermissions;

            if (permissions != null) {

                for (String permission : permissions) {

                    if (Manifest.permission.CAMERA.equals(permission)) {

                        manifestContainsCameraPermission = true;

                    } else if (
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    .equals(permission)) {

                        manifestContainsWriteExternalPermission = true;
                    }
                }
            }

        } catch (NameNotFoundException e) {

            LOG.e(
                    LOG_TAG,
                    "Unable to inspect package permissions",
                    e
            );
        }

        LOG.d(
                LOG_TAG,
                "CAMERA permission declared=" +
                        manifestContainsCameraPermission +
                        ", granted=" +
                        cameraPermissionGranted
        );

        ArrayList<String> requiredPermissions =
                new ArrayList<>();

        if (manifestContainsCameraPermission &&
                !cameraPermissionGranted) {

            requiredPermissions.add(
                    Manifest.permission.CAMERA
            );
        }

        if (saveToPhotoAlbum &&
                !writeExternalPermissionGranted) {

            if (!manifestContainsWriteExternalPermission) {

                throw new IllegalStateException(
                        "WRITE_EXTERNAL_STORAGE permission not declared"
                );
            }

            requiredPermissions.add(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            );
        }

        if (!requiredPermissions.isEmpty()) {

            LOG.d(
                    LOG_TAG,
                    "Requesting permissions"
            );

            PermissionHelper.requestPermissions(
                    this,
                    TAKE_PIC_SEC,
                    requiredPermissions.toArray(
                            new String[0]
                    )
            );

        } else {

            LOG.d(
                    LOG_TAG,
                    "Permissions already granted. Starting camera."
            );

            takePicture(
                    returnType,
                    encodingType
            );
        }
    }


    // ============================================================
    // STOP CAMERA
    // ============================================================

    public void stopCamera() {

        if (cordova == null ||
                cordova.getActivity() == null) {

            return;
        }

        int[] sourceTypes = {
                CAMERA,
                PHOTOLIBRARY,
                SAVEDPHOTOALBUM
        };

        int[] returnTypes = {
                DATA_URL,
                FILE_URI
        };

        for (int sourceType : sourceTypes) {

            for (int returnType : returnTypes) {

                try {

                    cordova.getActivity()
                            .finishActivity(
                                    (sourceType + 1) * 16
                                            + returnType + 1
                            );

                } catch (Exception ignored) {
                }
            }
        }
    }


    // ============================================================
    // START CAMERA
    // ============================================================

    public void takePicture(
            int returnType,
            int encodingType) {

        LOG.d(
                LOG_TAG,
                "takePicture() START"
        );

        Intent intent =
                new Intent(
                        MediaStore.ACTION_IMAGE_CAPTURE
                );

        File photo =
                createCaptureFile(
                        encodingType
                );

        LOG.d(
                LOG_TAG,
                "Capture file=" +
                        photo.getAbsolutePath()
        );

        try {

            this.imageUri =
                    FileProvider.getUriForFile(
                            cordova.getActivity(),
                            applicationId +
                                    ".cordova.plugin.camera.provider",
                            photo
                    );

        } catch (Exception e) {

            LOG.e(
                    LOG_TAG,
                    "Unable to create FileProvider URI",
                    e
            );

            failPicture(
                    "Unable to create camera output URI: " +
                            e.getMessage()
            );

            return;
        }

        LOG.d(
                LOG_TAG,
                "Camera output URI=" +
                        imageUri.toString()
        );

        intent.putExtra(
                MediaStore.EXTRA_OUTPUT,
                imageUri
        );

        /*
         * Important for Android camera applications.
         * Grant both read and write access.
         */
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );

        /*
         * Some camera applications inspect ClipData
         * instead of relying only on FLAG_GRANT_*.
         */
        intent.setClipData(
                ClipData.newRawUri(
                        "CameraOutput",
                        imageUri
                )
        );

        if (cordova == null ||
                cordova.getActivity() == null) {

            failPicture(
                    "Cordova activity is unavailable."
            );

            return;
        }

        PackageManager packageManager =
                cordova.getActivity()
                        .getPackageManager();

        if (intent.resolveActivity(packageManager) == null) {

            LOG.e(
                    LOG_TAG,
                    "No camera application available."
            );

            failPicture(
                    "No camera application available."
            );

            return;
        }

        int requestCode =
                (CAMERA + 1) * 16 +
                        returnType + 1;

        LOG.d(
                LOG_TAG,
                "Starting camera activity. requestCode=" +
                        requestCode
        );

        try {

            cordova.startActivityForResult(
                    this,
                    intent,
                    requestCode
            );

            LOG.d(
                    LOG_TAG,
                    "startActivityForResult() completed"
            );

        } catch (Exception e) {

            LOG.e(
                    LOG_TAG,
                    "Failed to start camera activity",
                    e
            );

            failPicture(
                    "Unable to start camera: " +
                            e.getMessage()
            );
        }
    }


    // ============================================================
    // CREATE CAPTURE FILE
    // ============================================================

    private File createCaptureFile(
            int encodingType) {

        return createCaptureFile(
                encodingType,
                ""
        );
    }

    private File createCaptureFile(
            int encodingType,
            String fileName) {

        if (fileName.isEmpty()) {

            fileName = ".Pic";
        }

        if (encodingType == JPEG) {

            fileName += JPEG_EXTENSION;

        } else if (encodingType == PNG) {

            fileName += PNG_EXTENSION;

        } else {

            throw new IllegalArgumentException(
                    "Invalid Encoding Type: " +
                            encodingType
            );
        }

        File cacheDir =
                new File(
                        getTempDirectoryPath(),
                        "org.apache.cordova.camera"
                );

        if (!cacheDir.exists()) {

            cacheDir.mkdirs();
        }

        File file =
                new File(
                        cacheDir,
                        fileName
                );

        LOG.d(
                LOG_TAG,
                "Created capture file: " +
                        file.getAbsolutePath()
        );

        return file;
    }


    // ============================================================
    // GET IMAGE
    // ============================================================

    public void getImage(
            int srcType,
            int returnType) {

        Intent intent = new Intent();

        String title = GET_PICTURE;

        croppedUri = null;
        croppedFilePath = null;

        if (mediaType == PICTURE) {

            intent.setType("image/*");

            if (allowEdit) {

                intent.setAction(Intent.ACTION_PICK);

                intent.putExtra("crop", "true");

                if (targetWidth > 0) {
                    intent.putExtra(
                            "outputX",
                            targetWidth
                    );
                }

                if (targetHeight > 0) {
                    intent.putExtra(
                            "outputY",
                            targetHeight
                    );
                }

                if (targetHeight > 0 &&
                        targetWidth > 0 &&
                        targetWidth == targetHeight) {

                    intent.putExtra("aspectX", 1);
                    intent.putExtra("aspectY", 1);
                }

                File croppedFile =
                        createCaptureFile(JPEG);

                croppedFilePath =
                        croppedFile.getAbsolutePath();

                croppedUri =
                        Uri.fromFile(croppedFile);

                intent.putExtra(
                        MediaStore.EXTRA_OUTPUT,
                        croppedUri
                );

            } else {

                intent.setAction(
                        Intent.ACTION_GET_CONTENT
                );

                intent.addCategory(
                        Intent.CATEGORY_OPENABLE
                );
            }

        } else if (mediaType == VIDEO) {

            intent.setType("video/*");

            title = GET_VIDEO;

            intent.setAction(
                    Intent.ACTION_GET_CONTENT
            );

            intent.addCategory(
                    Intent.CATEGORY_OPENABLE
            );

        } else if (mediaType == ALLMEDIA) {

            intent.setType("*/*");

            title = GET_All;

            intent.setAction(
                    Intent.ACTION_GET_CONTENT
            );

            intent.addCategory(
                    Intent.CATEGORY_OPENABLE
            );
        }

        if (cordova != null) {

            cordova.startActivityForResult(
                    this,
                    Intent.createChooser(
                            intent,
                            title
                    ),
                    (srcType + 1) * 16 +
                            returnType + 1
            );
        }
    }


    // ============================================================
    // ACTIVITY RESULT
    // ============================================================

    @Override
    public void onActivityResult(
            int requestCode,
            int resultCode,
            Intent intent) {

        LOG.d(
                LOG_TAG,
                "================================"
        );

        LOG.d(
                LOG_TAG,
                "onActivityResult reached"
        );

        LOG.d(
                LOG_TAG,
                "requestCode=" +
                        requestCode
        );

        LOG.d(
                LOG_TAG,
                "resultCode=" +
                        resultCode
        );

        LOG.d(
                LOG_TAG,
                "RESULT_OK=" +
                        Activity.RESULT_OK
        );

        LOG.d(
                LOG_TAG,
                "imageUri=" +
                        (imageUri == null
                                ? "NULL"
                                : imageUri.toString())
        );

        LOG.d(
                LOG_TAG,
                "intent=" +
                        (intent == null
                                ? "NULL"
                                : intent.toString())
        );

        LOG.d(
                LOG_TAG,
                "================================"
        );

        int calculatedSrcType =
                (requestCode / 16) - 1;

        int calculatedDestType =
                (requestCode % 16) - 1;

        LOG.d(
                LOG_TAG,
                "calculatedSrcType=" +
                        calculatedSrcType
        );

        LOG.d(
                LOG_TAG,
                "calculatedDestType=" +
                        calculatedDestType
        );

        if (requestCode >= CROP_CAMERA) {

            if (resultCode == Activity.RESULT_OK) {

                int cropDestType =
                        requestCode - CROP_CAMERA;

                try {

                    processResultFromCamera(
                            cropDestType,
                            intent
                    );

                } catch (IOException e) {

                    LOG.e(
                            LOG_TAG,
                            "Unable to process cropped image",
                            e
                    );

                    failPicture(
                            "Unable to write image: " +
                                    e.getMessage()
                    );
                }

            } else if (
                    resultCode ==
                            Activity.RESULT_CANCELED) {

                failPicture(
                        "No Image Selected"
                );

            } else {

                failPicture(
                        "Did not complete!"
                );
            }

            return;
        }

        if (calculatedSrcType == CAMERA) {

            LOG.d(
                    LOG_TAG,
                    "Camera result received."
            );

            if (resultCode == Activity.RESULT_OK) {

                try {

                    if (imageUri == null) {

                        LOG.e(
                                LOG_TAG,
                                "imageUri is NULL after camera result"
                        );

                        failPicture(
                                "Camera returned without output URI."
                        );

                        return;
                    }

                    InputStream testStream =
                            cordova.getActivity()
                                    .getContentResolver()
                                    .openInputStream(imageUri);

                    if (testStream == null) {

                        LOG.e(
                                LOG_TAG,
                                "Camera output stream is NULL"
                        );

                        failPicture(
                                "Unable to read captured image."
                        );

                        return;
                    }

                    testStream.close();

                    LOG.d(
                            LOG_TAG,
                            "Camera output URI is readable."
                    );

                    if (allowEdit) {

                        Uri tmpFile =
                                FileProvider.getUriForFile(
                                        cordova.getActivity(),
                                        applicationId +
                                                ".cordova.plugin.camera.provider",
                                        createCaptureFile(
                                                encodingType
                                        )
                                );

                        performCrop(
                                tmpFile,
                                calculatedDestType,
                                intent
                        );

                    } else {

                        LOG.d(
                                LOG_TAG,
                                "Processing camera image directly."
                        );

                        processResultFromCamera(
                                calculatedDestType,
                                intent
                        );
                    }

                } catch (Exception e) {

                    LOG.e(
                            LOG_TAG,
                            "Exception processing camera result",
                            e
                    );

                    failPicture(
                            "Error capturing image: " +
                                    e.getMessage()
                    );
                }

            } else if (
                    resultCode ==
                            Activity.RESULT_CANCELED) {

                LOG.d(
                        LOG_TAG,
                        "Camera was cancelled."
                );

                failPicture(
                        "No Image Selected"
                );

            } else {

                LOG.e(
                        LOG_TAG,
                        "Camera returned unexpected resultCode=" +
                                resultCode
                );

                failPicture(
                        "Did not complete! resultCode=" +
                                resultCode
                );
            }

            return;
        }

        if (calculatedSrcType == PHOTOLIBRARY ||
                calculatedSrcType == SAVEDPHOTOALBUM) {

            if (resultCode == Activity.RESULT_OK &&
                    intent != null) {

                final Intent finalIntent = intent;

                final int finalDestType =
                        calculatedDestType;

                cordova.getThreadPool()
                        .execute(
                                new Runnable() {

                                    @Override
                                    public void run() {

                                        processResultFromGallery(
                                                finalDestType,
                                                finalIntent
                                        );
                                    }
                                }
                        );

            } else if (
                    resultCode ==
                            Activity.RESULT_CANCELED) {

                failPicture(
                        "No Image Selected"
                );

            } else {

                failPicture(
                        "Selection did not complete!"
                );
            }
        }
    }


    // ============================================================
    // PROCESS CAMERA RESULT
    // ============================================================

    private void processResultFromCamera(
            int destType,
            Intent intent) throws IOException {

        LOG.d(
                LOG_TAG,
                "processResultFromCamera()"
        );

        LOG.d(
                LOG_TAG,
                "destType=" + destType
        );

        LOG.d(
                LOG_TAG,
                "imageUri=" +
                        (imageUri == null
                                ? "NULL"
                                : imageUri.toString())
        );

        if (imageUri == null) {

            failPicture(
                    "Captured image URI is null."
            );

            return;
        }

        InputStream input = null;

        String mimeType;

        try {

            input =
                    cordova.getActivity()
                            .getContentResolver()
                            .openInputStream(
                                    imageUri
                            );

            mimeType =
                    FileHelper.getMimeType(
                            imageUri.toString(),
                            cordova
                    );

            LOG.d(
                    LOG_TAG,
                    "Detected MIME type=" +
                            mimeType
            );

        } catch (Exception e) {

            LOG.e(
                    LOG_TAG,
                    "Unable to open camera output URI",
                    e
            );

            failPicture(
                    "Unable to open captured image: " +
                            e.getMessage()
            );

            return;
        }

        if (input == null) {

            failPicture(
                    "Unable to open result source."
            );

            return;
        }

        byte[] sourceData;

        try {

            sourceData =
                    readData(input);

        } finally {

            input.close();
        }

        if (sourceData == null ||
                sourceData.length == 0) {

            LOG.e(
                    LOG_TAG,
                    "Camera returned an empty image."
            );

            failPicture(
                    "Captured image is empty."
            );

            return;
        }

        LOG.d(
                LOG_TAG,
                "Captured image bytes=" +
                        sourceData.length
        );

        Bitmap bitmap = null;

        try {

            if (destType == DATA_URL) {

                LOG.d(
                        LOG_TAG,
                        "Destination is DATA_URL. Creating Base64."
                );

                bitmap =
                        getScaledAndRotatedBitmap(
                                sourceData,
                                mimeType
                        );

                if (bitmap == null &&
                        intent != null &&
                        intent.getExtras() != null) {

                    Object extra =
                            intent.getExtras()
                                    .get("data");

                    if (extra instanceof Bitmap) {

                        bitmap =
                                (Bitmap) extra;
                    }
                }

                if (bitmap == null) {

                    LOG.e(
                            LOG_TAG,
                            "Bitmap creation failed."
                    );

                    failPicture(
                            "Unable to create bitmap!"
                    );

                    return;
                }

                LOG.d(
                        LOG_TAG,
                        "Bitmap created: " +
                                bitmap.getWidth() +
                                "x" +
                                bitmap.getHeight()
                );

                processPicture(
                        bitmap,
                        encodingType
                );

                return;
            }

            if (destType == FILE_URI) {

                LOG.d(
                        LOG_TAG,
                        "Destination is FILE_URI."
                );

                Uri uri =
                        Uri.fromFile(
                                createCaptureFile(
                                        encodingType,
                                        System.currentTimeMillis() + ""
                                )
                        );

                bitmap =
                        getScaledAndRotatedBitmap(
                                sourceData,
                                mimeType
                        );

                if (bitmap == null) {

                    failPicture(
                            "Unable to create bitmap!"
                    );

                    return;
                }

                OutputStream os =
                        new FileOutputStream(
                                uri.getPath()
                        );

                CompressFormat compressFormat =
                        getCompressFormatForEncodingType(
                                encodingType
                        );

                bitmap.compress(
                        compressFormat,
                        mQuality,
                        os
                );

                os.flush();
                os.close();

                LOG.d(
                        LOG_TAG,
                        "Returning FILE_URI=" +
                                uri.toString()
                );

                callbackContext.success(
                        uri.toString()
                );

                bitmap.recycle();
                bitmap = null;

                cleanup(
                        imageUri,
                        null,
                        null
                );

                return;
            }

            failPicture(
                    "Unsupported destination type: " +
                            destType
            );

        } catch (Exception e) {

            LOG.e(
                    LOG_TAG,
                    "Error processing camera image",
                    e
            );

            failPicture(
                    "Error processing image: " +
                            e.getMessage()
            );

        } finally {

            if (bitmap != null &&
                    !bitmap.isRecycled()) {

                bitmap.recycle();
            }
        }
    }


    // ============================================================
    // BASE64
    // ============================================================

    public void processPicture(
            Bitmap bitmap,
            int encodingType) {

        LOG.d(
                LOG_TAG,
                "processPicture() START"
        );

        if (bitmap == null) {

            failPicture(
                    "Bitmap is null."
            );

            return;
        }

        ByteArrayOutputStream dataStream =
                new ByteArrayOutputStream();

        CompressFormat compressFormat =
                getCompressFormatForEncodingType(
                        encodingType
                );

        try {

            boolean compressed =
                    bitmap.compress(
                            compressFormat,
                            mQuality,
                            dataStream
                    );

            if (!compressed) {

                failPicture(
                        "Bitmap compression failed."
                );

                return;
            }

            byte[] code =
                    dataStream.toByteArray();

            LOG.d(
                    LOG_TAG,
                    "Compressed image bytes=" +
                            code.length
            );

            byte[] output =
                    Base64.encode(
                            code,
                            Base64.NO_WRAP
                    );

            String mimeType =
                    encodingType == PNG
                            ? PNG_MIME_TYPE
                            : JPEG_MIME_TYPE;

            String result =
                    "data:" +
                            mimeType +
                            ";base64," +
                            new String(output);

            LOG.d(
                    LOG_TAG,
                    "Base64 generated. Length=" +
                            result.length()
            );

            callbackContext.success(
                    result
            );

            LOG.d(
                    LOG_TAG,
                    "callbackContext.success() called"
            );

            dataStream.close();

        } catch (Exception e) {

            LOG.e(
                    LOG_TAG,
                    "Error compressing image",
                    e
            );

            failPicture(
                    "Error compressing image: " +
                            e.getMessage()
            );
        }
    }


    // ============================================================
    // ERROR
    // ============================================================

    public void failPicture(
            String error) {

        LOG.e(
                LOG_TAG,
                "Camera ERROR: " +
                        error
        );

        if (callbackContext != null) {

            callbackContext.error(
                    error
            );
        }
    }


    // ============================================================
    // COMPRESS FORMAT
    // ============================================================

    private CompressFormat getCompressFormatForEncodingType(
            int encodingType) {

        return encodingType == JPEG
                ? CompressFormat.JPEG
                : CompressFormat.PNG;
    }


    // ============================================================
    // GALLERY SUPPORT
    // ============================================================

    private void writeTakenPictureToGalleryStartingFromAndroidQ(
            GalleryPathVO galleryPathVO)
            throws IOException {

        ContentResolver resolver =
                cordova.getActivity()
                        .getContentResolver();

        ContentValues contentValues =
                new ContentValues();

        contentValues.put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                galleryPathVO.getGalleryFileName()
        );

        contentValues.put(
                MediaStore.MediaColumns.MIME_TYPE,
                getMimetypeForEncodingType()
        );

        Uri galleryOutputUri =
                resolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                );

        if (galleryOutputUri == null) {

            throw new IOException(
                    "Unable to create gallery URI"
            );
        }

        InputStream fileStream =
                FileHelper.getInputStreamFromUriString(
                        imageUri.toString(),
                        cordova
                );

        writeUncompressedImage(
                fileStream,
                galleryOutputUri
        );
    }


    private String getMimetypeForEncodingType() {

        if (encodingType == PNG) {
            return PNG_MIME_TYPE;
        }

        if (encodingType == JPEG) {
            return JPEG_MIME_TYPE;
        }

        return "";
    }


    // ============================================================
    // FILE HELPERS
    // ============================================================

    private void writeUncompressedImage(
            InputStream input,
            Uri destination)
            throws IOException {

        OutputStream output = null;

        try {

            output =
                    cordova.getActivity()
                            .getContentResolver()
                            .openOutputStream(
                                    destination
                            );

            if (output == null) {

                throw new IOException(
                        "Unable to open output stream"
                );
            }

            byte[] buffer =
                    new byte[8192];

            int length;

            while (
                    (length =
                            input.read(buffer)) != -1) {

                output.write(
                        buffer,
                        0,
                        length
                );
            }

            output.flush();

        } finally {

            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }

            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
        }
    }


    private byte[] readData(
            InputStream input)
            throws IOException {

        if (input == null) {
            return null;
        }

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        byte[] dataChunk =
                new byte[8192];

        int bytesRead;

        while (
                (bytesRead =
                        input.read(dataChunk)) != -1) {

            buffer.write(
                    dataChunk,
                    0,
                    bytesRead
            );
        }

        return buffer.toByteArray();
    }


    // ============================================================
    // IMAGE PROCESSING
    // ============================================================

    private Bitmap getScaledAndRotatedBitmap(
            byte[] data,
            String mimeType)
            throws IOException {

        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inJustDecodeBounds = true;

        BitmapFactory.decodeStream(
                new ByteArrayInputStream(data),
                null,
                options
        );

        if (options.outWidth <= 0 ||
                options.outHeight <= 0) {

            return null;
        }

        if (targetWidth <= 0 &&
                targetHeight <= 0 &&
                !correctOrientation) {

            options.inJustDecodeBounds = false;

            return BitmapFactory.decodeStream(
                    new ByteArrayInputStream(data),
                    null,
                    options
            );
        }

        int rotate = 0;

        if (correctOrientation &&
                JPEG_MIME_TYPE.equalsIgnoreCase(mimeType)) {

            try {

                ExifInterface exif =
                        new ExifInterface(
                                new ByteArrayInputStream(data)
                        );

                int orientation =
                        exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                        );

                if (orientation ==
                        ExifInterface.ORIENTATION_ROTATE_90) {

                    rotate = 90;

                } else if (
                        orientation ==
                                ExifInterface.ORIENTATION_ROTATE_180) {

                    rotate = 180;

                } else if (
                        orientation ==
                                ExifInterface.ORIENTATION_ROTATE_270) {

                    rotate = 270;
                }

            } catch (Exception e) {

                LOG.w(
                        LOG_TAG,
                        "Unable to read EXIF orientation: " +
                                e.getMessage()
                );
            }
        }

        int originalWidth =
                options.outWidth;

        int originalHeight =
                options.outHeight;

        int rotatedWidth =
                (rotate == 90 || rotate == 270)
                        ? originalHeight
                        : originalWidth;

        int rotatedHeight =
                (rotate == 90 || rotate == 270)
                        ? originalWidth
                        : originalHeight;

        int[] dimensions =
                calculateAspectRatio(
                        rotatedWidth,
                        rotatedHeight
                );

        int desiredWidth =
                dimensions[0];

        int desiredHeight =
                dimensions[1];

        if (desiredWidth <= 0) {
            desiredWidth = rotatedWidth;
        }

        if (desiredHeight <= 0) {
            desiredHeight = rotatedHeight;
        }

        int sampleSize =
                calculateSampleSize(
                        rotatedWidth,
                        rotatedHeight,
                        desiredWidth,
                        desiredHeight
                );

        if (sampleSize < 1) {
            sampleSize = 1;
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;

        Bitmap bitmap =
                BitmapFactory.decodeStream(
                        new ByteArrayInputStream(data),
                        null,
                        options
                );

        if (bitmap == null) {
            return null;
        }

        if (desiredWidth > 0 &&
                desiredHeight > 0 &&
                (bitmap.getWidth() != desiredWidth ||
                        bitmap.getHeight() != desiredHeight)) {

            Bitmap scaled =
                    Bitmap.createScaledBitmap(
                            bitmap,
                            desiredWidth,
                            desiredHeight,
                            true
                    );

            if (scaled != bitmap) {

                bitmap.recycle();
                bitmap = scaled;
            }
        }

        if (correctOrientation &&
                rotate != 0) {

            Matrix matrix =
                    new Matrix();

            matrix.setRotate(rotate);

            Bitmap rotatedBitmap =
                    Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.getWidth(),
                            bitmap.getHeight(),
                            matrix,
                            true
                    );

            if (rotatedBitmap != bitmap) {

                bitmap.recycle();
                bitmap = rotatedBitmap;
            }

            orientationCorrected = true;
        }

        return bitmap;
    }


    public int[] calculateAspectRatio(
            int origWidth,
            int origHeight) {

        int newWidth =
                targetWidth;

        int newHeight =
                targetHeight;

        if (newWidth <= 0 &&
                newHeight <= 0) {

            newWidth = origWidth;
            newHeight = origHeight;

        } else if (
                newWidth > 0 &&
                        newHeight <= 0) {

            newHeight =
                    (int) (
                            (double) newWidth /
                                    (double) origWidth *
                                    origHeight
                    );

        } else if (
                newWidth <= 0 &&
                        newHeight > 0) {

            newWidth =
                    (int) (
                            (double) newHeight /
                                    (double) origHeight *
                                    origWidth
                    );

        } else {

            double newRatio =
                    newWidth /
                            (double) newHeight;

            double originalRatio =
                    origWidth /
                            (double) origHeight;

            if (originalRatio > newRatio) {

                newHeight =
                        (newWidth * origHeight) /
                                origWidth;

            } else if (
                    originalRatio < newRatio) {

                newWidth =
                        (newHeight * origWidth) /
                                origHeight;
            }
        }

        return new int[]{
                newWidth,
                newHeight
        };
    }


    public static int calculateSampleSize(
            int srcWidth,
            int srcHeight,
            int dstWidth,
            int dstHeight) {

        if (dstWidth <= 0 ||
                dstHeight <= 0) {

            return 1;
        }

        final float srcAspect =
                (float) srcWidth /
                        (float) srcHeight;

        final float dstAspect =
                (float) dstWidth /
                        (float) dstHeight;

        int sample;

        if (srcAspect > dstAspect) {

            sample =
                    srcWidth /
                            dstWidth;

        } else {

            sample =
                    srcHeight /
                            dstHeight;
        }

        return Math.max(
                1,
                sample
        );
    }


    // ============================================================
    // CLEANUP
    // ============================================================

    private void cleanup(
            Uri oldImage,
            Uri newImage,
            Bitmap bitmap) {

        if (bitmap != null &&
                !bitmap.isRecycled()) {

            bitmap.recycle();
        }

        if (oldImage != null) {

            try {

                String path =
                        oldImage.toString();

                if (path.startsWith("file://")) {

                    File file =
                            new File(
                                    FileHelper.stripFileProtocol(
                                            path
                                    )
                            );

                    file.delete();
                }

            } catch (Exception e) {

                LOG.w(
                        LOG_TAG,
                        "Unable to cleanup temporary image: " +
                                e.getMessage()
                );
            }
        }

        System.gc();
    }


    // ============================================================
    // PERMISSION RESULT
    // ============================================================

    @Override
    public void onRequestPermissionResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        LOG.d(
                LOG_TAG,
                "onRequestPermissionResult requestCode=" +
                        requestCode
        );

        for (int result : grantResults) {

            if (result ==
                    PackageManager.PERMISSION_DENIED) {

                failPicture(
                        "Camera permission denied."
                );

                return;
            }
        }

        if (requestCode == TAKE_PIC_SEC) {

            takePicture(
                    destType,
                    encodingType
            );

        } else if (
                requestCode ==
                        SAVE_TO_ALBUM_SEC) {

            getImage(
                    srcType,
                    destType
            );
        }
    }


    // ============================================================
    // MEDIA SCANNER
    // ============================================================

    private void scanForGallery(
            Uri newImage) {

        this.scanMe = newImage;

        if (this.conn != null) {

            this.conn.disconnect();
        }

        this.conn =
                new MediaScannerConnection(
                        cordova.getActivity()
                                .getApplicationContext(),
                        this
                );

        conn.connect();
    }


    @Override
    public void onMediaScannerConnected() {

        try {

            conn.scanFile(
                    scanMe.toString(),
                    "image/*"
            );

        } catch (IllegalStateException e) {

            LOG.e(
                    LOG_TAG,
                    "Unable to scan image",
                    e
            );
        }
    }


    @Override
    public void onScanCompleted(
            String path,
            Uri uri) {

        if (conn != null) {

            conn.disconnect();
        }
    }


    // ============================================================
    // STATE RESTORATION
    // ============================================================

    @Override
    public Bundle onSaveInstanceState() {

        Bundle state =
                new Bundle();

        state.putInt(
                "destType",
                destType
        );

        state.putInt(
                "srcType",
                srcType
        );

        state.putInt(
                "mQuality",
                mQuality
        );

        state.putInt(
                "targetWidth",
                targetWidth
        );

        state.putInt(
                "targetHeight",
                targetHeight
        );

        state.putInt(
                "encodingType",
                encodingType
        );

        state.putInt(
                "mediaType",
                mediaType
        );

        state.putBoolean(
                "allowEdit",
                allowEdit
        );

        state.putBoolean(
                "correctOrientation",
                correctOrientation
        );

        state.putBoolean(
                "saveToPhotoAlbum",
                saveToPhotoAlbum
        );

        if (croppedUri != null) {

            state.putString(
                    CROPPED_URI_KEY,
                    croppedFilePath
            );
        }

        if (imageUri != null) {

            state.putString(
                    IMAGE_URI_KEY,
                    imageUri.toString()
            );
        }

        return state;
    }


    @Override
    public void onRestoreStateForActivityResult(
            Bundle state,
            CallbackContext callbackContext) {

        destType =
                state.getInt("destType");

        srcType =
                state.getInt("srcType");

        mQuality =
                state.getInt("mQuality");

        targetWidth =
                state.getInt("targetWidth");

        targetHeight =
                state.getInt("targetHeight");

        encodingType =
                state.getInt("encodingType");

        mediaType =
                state.getInt("mediaType");

        allowEdit =
                state.getBoolean("allowEdit");

        correctOrientation =
                state.getBoolean(
                        "correctOrientation"
                );

        saveToPhotoAlbum =
                state.getBoolean(
                        "saveToPhotoAlbum"
                );

        if (state.containsKey(
                CROPPED_URI_KEY)) {

            croppedFilePath =
                    state.getString(
                            CROPPED_URI_KEY
                    );

            if (croppedFilePath != null) {

                croppedUri =
                        Uri.parse(
                                croppedFilePath
                        );
            }
        }

        if (state.containsKey(
                IMAGE_URI_KEY)) {

            imageUri =
                    Uri.parse(
                            state.getString(
                                    IMAGE_URI_KEY
                            )
                    );
        }

        this.callbackContext =
                callbackContext;
    }


    // ============================================================
    // CROP
    // ============================================================

    private void performCrop(
            Uri picUri,
            int destType,
            Intent cameraIntent) {

        try {

            Intent cropIntent =
                    new Intent(
                            "com.android.camera.action.CROP"
                    );

            cropIntent.setDataAndType(
                    picUri,
                    "image/*"
            );

            cropIntent.putExtra(
                    "crop",
                    "true"
            );

            if (targetWidth > 0) {

                cropIntent.putExtra(
                        "outputX",
                        targetWidth
                );
            }

            if (targetHeight > 0) {

                cropIntent.putExtra(
                        "outputY",
                        targetHeight
                );
            }

            croppedFilePath =
                    createCaptureFile(
                            encodingType,
                            System.currentTimeMillis() + ""
                    ).getAbsolutePath();

            croppedUri =
                    FileProvider.getUriForFile(
                            cordova.getActivity(),
                            applicationId +
                                    ".cordova.plugin.camera.provider",
                            new File(croppedFilePath)
                    );

            cropIntent.putExtra(
                    MediaStore.EXTRA_OUTPUT,
                    croppedUri
            );

            cropIntent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );

            cropIntent.setClipData(
                    ClipData.newRawUri(
                            "CropOutput",
                            croppedUri
                    )
            );

            cordova.startActivityForResult(
                    this,
                    cropIntent,
                    CROP_CAMERA + destType
            );

        } catch (ActivityNotFoundException e) {

            LOG.e(
                    LOG_TAG,
                    "Crop operation not supported",
                    e
            );

            try {

                processResultFromCamera(
                        destType,
                        cameraIntent
                );

            } catch (IOException ioException) {

                failPicture(
                        "Unable to process image."
                );
            }

        } catch (Exception e) {

            LOG.e(
                    LOG_TAG,
                    "Unable to start crop",
                    e
            );

            failPicture(
                    "Unable to start crop: " +
                            e.getMessage()
            );
        }
    }


    // ============================================================
    // MISC
    // ============================================================

    private String getExtensionForEncodingType() {

        return encodingType == JPEG
                ? JPEG_EXTENSION
                : PNG_EXTENSION;
    }

    private String getMimetypeForEncodingType(
            boolean unused) {

        return getMimetypeForEncodingType();
    }
}
```
