/*
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
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
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Custom Cordova Camera Launcher.
 */
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

    private static final String LOG_TAG = "CameraLauncher";

    private static final int CROP_CAMERA = 100;

    private static final String TIME_FORMAT = "yyyyMMdd_HHmmss";

    /*
     * Camera direction.
     *
     * 0 = BACK
     * 1 = FRONT
     */
    public static final int CAMERA_DIRECTION_BACK = 0;
    public static final int CAMERA_DIRECTION_FRONT = 1;

    /*
     * Intent extra used by a number of Android camera applications
     * to request the front/back camera.
     */
    private static final String CAMERA_FACING_EXTRA =
            "android.intent.extras.CAMERA_FACING";

    private int mQuality;
    private int targetWidth;
    private int targetHeight;

    private Uri imageUri;

    private int encodingType;
    private int mediaType;
    private int destType;
    private int srcType;

    /*
     * Camera direction.
     *
     * 0 = BACK
     * 1 = FRONT
     */
    private int cameraDirection =
            CAMERA_DIRECTION_BACK;

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

    // -------------------------------------------------------------------------
    // EXECUTE
    // -------------------------------------------------------------------------

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

        if (TAKE_PICTURE_ACTION.equals(action)) {

            this.callbackContext = callbackContext;

            this.srcType = CAMERA;
            this.destType = FILE_URI;

            this.saveToPhotoAlbum = false;

            this.targetHeight = 0;
            this.targetWidth = 0;

            this.encodingType = JPEG;
            this.mediaType = PICTURE;
            this.mQuality = 50;

            /*
             * Default camera direction:
             * BACK
             */
            this.cameraDirection =
                    CAMERA_DIRECTION_BACK;

            try {

                if (args != null && args.length() > 0) {
                    this.mQuality = args.getInt(0);
                }

                if (args != null && args.length() > 1) {
                    this.destType = args.getInt(1);
                }

                if (args != null && args.length() > 2) {
                    this.srcType = args.getInt(2);
                }

                if (args != null && args.length() > 3) {
                    this.targetWidth = args.getInt(3);
                }

                if (args != null && args.length() > 4) {
                    this.targetHeight = args.getInt(4);
                }

                if (args != null && args.length() > 5) {
                    this.encodingType = args.getInt(5);
                }

                if (args != null && args.length() > 6) {
                    this.mediaType = args.getInt(6);
                }

                if (args != null && args.length() > 7) {
                    this.allowEdit = args.getBoolean(7);
                }

                if (args != null && args.length() > 8) {
                    this.correctOrientation =
                            args.getBoolean(8);
                }

                if (args != null && args.length() > 9) {
                    this.saveToPhotoAlbum =
                            args.getBoolean(9);
                }

                /*
                 * NEW:
                 *
                 * args[10] = cameraDirection
                 *
                 * 0 = BACK
                 * 1 = FRONT
                 */
                if (args != null && args.length() > 10) {
                    this.cameraDirection =
                            args.getInt(10);
                }

            } catch (Exception e) {

                callbackContext.error(
                        "Invalid camera arguments: "
                                + e.getLocalizedMessage()
                );

                return true;
            }

            /*
             * Validate camera direction.
             *
             * Anything other than 0 or 1
             * falls back to BACK.
             */
            if (this.cameraDirection
                    != CAMERA_DIRECTION_BACK
                    && this.cameraDirection
                    != CAMERA_DIRECTION_FRONT) {

                LOG.d(
                        LOG_TAG,
                        "Invalid camera direction: "
                                + this.cameraDirection
                                + ". Defaulting to BACK."
                );

                this.cameraDirection =
                        CAMERA_DIRECTION_BACK;
            }

            LOG.d(
                    LOG_TAG,
                    "Camera direction requested: "
                            + (
                                this.cameraDirection
                                        == CAMERA_DIRECTION_FRONT
                                        ? "FRONT"
                                        : "BACK"
                            )
            );

            if (this.targetWidth < 1) {
                this.targetWidth = -1;
            }

            if (this.targetHeight < 1) {
                this.targetHeight = -1;
            }

            if (this.targetHeight == -1
                    && this.targetWidth == -1
                    && this.mQuality == 100
                    && !this.correctOrientation
                    && this.encodingType == PNG
                    && this.srcType == CAMERA) {

                this.encodingType = JPEG;
            }

            try {

                if (this.srcType == CAMERA) {

                    callTakePicture(
                            this.destType,
                            this.encodingType
                    );

                } else if (
                        this.srcType == PHOTOLIBRARY
                                || this.srcType == SAVEDPHOTOALBUM) {

                    getImage(
                            this.srcType,
                            this.destType
                    );
                }

            } catch (Exception e) {

                LOG.e(
                        LOG_TAG,
                        "Camera execute error",
                        e
                );

                callbackContext.error(
                        "Camera error: "
                                + e.getLocalizedMessage()
                );

                return true;
            }

            PluginResult result =
                    new PluginResult(
                            PluginResult.Status.NO_RESULT
                    );

            result.setKeepCallback(true);

            callbackContext.sendPluginResult(result);

            return true;
        }

        if (STOP_ACTION.equals(action)) {

            stopCamera();

            callbackContext.success();

            return true;
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // TEMP DIRECTORY
    // -------------------------------------------------------------------------

    private String getTempDirectoryPath() {

        File cache =
                cordova.getActivity().getCacheDir();

        if (!cache.exists()) {
            cache.mkdirs();
        }

        return cache.getAbsolutePath();
    }

    // -------------------------------------------------------------------------
    // CAMERA PERMISSION
    // -------------------------------------------------------------------------

    public void callTakePicture(
            int returnType,
            int encodingType) {

        boolean manifestContainsCameraPermission = false;

        boolean manifestContainsWriteExternalPermission = false;

        boolean cameraPermissionGranted =
                PermissionHelper.hasPermission(
                        this,
                        Manifest.permission.CAMERA
                );

        boolean writeExternalPermissionGranted;

        if (Build.VERSION.SDK_INT
                <= Build.VERSION_CODES.P) {

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

            String[] permissionsInPackage =
                    packageManager
                            .getPackageInfo(
                                    cordova.getActivity()
                                            .getPackageName(),
                                    PackageManager.GET_PERMISSIONS
                            )
                            .requestedPermissions;

            if (permissionsInPackage != null) {

                for (String permission :
                        permissionsInPackage) {

                    if (Manifest.permission.CAMERA.equals(
                            permission)) {

                        manifestContainsCameraPermission = true;

                    } else if (
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    .equals(permission)) {

                        manifestContainsWriteExternalPermission =
                                true;
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

        ArrayList<String> requiredPermissions =
                new ArrayList<>();

        if (manifestContainsCameraPermission
                && !cameraPermissionGranted) {

            requiredPermissions.add(
                    Manifest.permission.CAMERA
            );
        }

        if (saveToPhotoAlbum
                && !writeExternalPermissionGranted) {

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

            PermissionHelper.requestPermissions(
                    this,
                    TAKE_PIC_SEC,
                    requiredPermissions.toArray(
                            new String[0]
                    )
            );

        } else {

            takePicture(
                    returnType,
                    encodingType
            );
        }
    }

    // -------------------------------------------------------------------------
    // TAKE PICTURE
    // -------------------------------------------------------------------------

    public void takePicture(
            int returnType,
            int encodingType) {

        try {

            Intent intent =
                    new Intent(
                            MediaStore.ACTION_IMAGE_CAPTURE
                    );

            /*
             * -------------------------------------------------------------
             * CAMERA DIRECTION
             * -------------------------------------------------------------
             *
             * 0 = BACK
             * 1 = FRONT
             *
             * This extra is recognized by many Android camera
             * applications, although it is not guaranteed by the
             * ACTION_IMAGE_CAPTURE contract on every OEM device.
             */
            intent.putExtra(
                    CAMERA_FACING_EXTRA,
                    this.cameraDirection
            );

            LOG.d(
                    LOG_TAG,
                    "Requested camera direction: "
                            + (
                                this.cameraDirection
                                        == CAMERA_DIRECTION_FRONT
                                        ? "FRONT"
                                        : "BACK"
                            )
            );

            PackageManager packageManager =
                    cordova.getActivity()
                            .getPackageManager();

            if (intent.resolveActivity(packageManager)
                    == null) {

                failPicture(
                        "No camera application available."
                );

                return;
            }

            File photo =
                    createCaptureFile(
                            encodingType
                    );

            File parent =
                    photo.getParentFile();

            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            this.imageUri =
                    FileProvider.getUriForFile(
                            cordova.getActivity(),
                            applicationId
                                    + ".cordova.plugin.camera.provider",
                            photo
                    );

            intent.putExtra(
                    MediaStore.EXTRA_OUTPUT,
                    this.imageUri
            );

            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );

            /*
             * Some camera applications require ClipData
             * in order to receive the FileProvider URI.
             */
            intent.setClipData(
                    ClipData.newRawUri(
                            "CameraOutput",
                            this.imageUri
                    )
            );

            List<android.content.pm.ResolveInfo>
                    cameraApps =
                    packageManager.queryIntentActivities(
                            intent,
                            PackageManager.MATCH_DEFAULT_ONLY
                    );

            for (
                    android.content.pm.ResolveInfo resolveInfo
                    : cameraApps) {

                String packageName =
                        resolveInfo.activityInfo.packageName;

                cordova.getActivity()
                        .grantUriPermission(
                                packageName,
                                this.imageUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        );
            }

            LOG.d(
                    LOG_TAG,
                    "Starting camera"
            );

            LOG.d(
                    LOG_TAG,
                    "Camera output URI: "
                            + this.imageUri
            );

            LOG.d(
                    LOG_TAG,
                    "Camera direction: "
                            + (
                                this.cameraDirection
                                        == CAMERA_DIRECTION_FRONT
                                        ? "FRONT"
                                        : "BACK"
                            )
            );

            this.cordova.startActivityForResult(
                    this,
                    intent,
                    (CAMERA + 1) * 16
                            + returnType
                            + 1
            );

        } catch (Exception e) {

            LOG.e(
                    LOG_TAG,
                    "Unable to start camera",
                    e
            );

            failPicture(
                    "Unable to start camera: "
                            + e.getLocalizedMessage()
            );
        }
    }

    // -------------------------------------------------------------------------
    // CREATE CAPTURE FILE
    // -------------------------------------------------------------------------

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

        if (fileName == null
                || fileName.isEmpty()) {

            fileName = ".Pic";
        }

        if (encodingType == JPEG) {

            fileName += JPEG_EXTENSION;

        } else if (encodingType == PNG) {

            fileName += PNG_EXTENSION;

        } else {

            throw new IllegalArgumentException(
                    "Invalid Encoding Type: "
                            + encodingType
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

        return new File(
                cacheDir,
                fileName
        );
    }

    // -------------------------------------------------------------------------
    // STOP CAMERA
    // -------------------------------------------------------------------------

    public void stopCamera() {

        if (cordova == null
                || cordova.getActivity() == null) {

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
                                            + returnType
                                            + 1
                            );

                } catch (Exception ignored) {
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // GET IMAGE
    // -------------------------------------------------------------------------

    public void getImage(
            int srcType,
            int returnType) {

        Intent intent =
                new Intent();

        String title =
                GET_PICTURE;

        croppedUri = null;
        croppedFilePath = null;

        if (mediaType == PICTURE) {

            intent.setType("image/*");

            if (allowEdit) {

                intent.setAction(
                        Intent.ACTION_PICK
                );

                intent.putExtra(
                        "crop",
                        "true"
                );

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

                if (targetHeight > 0
                        && targetWidth > 0
                        && targetWidth == targetHeight) {

                    intent.putExtra(
                            "aspectX",
                            1
                    );

                    intent.putExtra(
                            "aspectY",
                            1
                    );
                }

                File croppedFile =
                        createCaptureFile(JPEG);

                croppedFilePath =
                        croppedFile.getAbsolutePath();

                croppedUri =
                        FileProvider.getUriForFile(
                                cordova.getActivity(),
                                applicationId
                                        + ".cordova.plugin.camera.provider",
                                croppedFile
                        );

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
                    (srcType + 1) * 16
                            + returnType
                            + 1
            );
        }
    }

    // -------------------------------------------------------------------------
    // CROP
    // -------------------------------------------------------------------------

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

            if (targetHeight > 0
                    && targetWidth > 0
                    && targetWidth == targetHeight) {

                cropIntent.putExtra(
                        "aspectX",
                        1
                );

                cropIntent.putExtra(
                        "aspectY",
                        1
                );
            }

            File croppedFile =
                    createCaptureFile(
                            this.encodingType,
                            System.currentTimeMillis()
                                    + ""
                    );

            croppedFilePath =
                    croppedFile.getAbsolutePath();

            croppedUri =
                    FileProvider.getUriForFile(
                            cordova.getActivity(),
                            applicationId
                                    + ".cordova.plugin.camera.provider",
                            croppedFile
                    );

            cropIntent.putExtra(
                    "output",
                    croppedUri
            );

            cropIntent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );

            cropIntent.setClipData(
                    ClipData.newRawUri(
                            "CropOutput",
                            croppedUri
                    )
            );

            PackageManager pm =
                    cordova.getActivity()
                            .getPackageManager();

            List<android.content.pm.ResolveInfo>
                    cropApps =
                    pm.queryIntentActivities(
                            cropIntent,
                            PackageManager.MATCH_DEFAULT_ONLY
                    );

            for (
                    android.content.pm.ResolveInfo resolveInfo
                    : cropApps) {

                cordova.getActivity()
                        .grantUriPermission(
                                resolveInfo.activityInfo.packageName,
                                croppedUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        );
            }

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

            } catch (Exception ex) {

                failPicture(
                        "Unable to process image: "
                                + ex.getLocalizedMessage()
                );
            }

        } catch (Exception e) {

            failPicture(
                    "Unable to start crop: "
                            + e.getLocalizedMessage()
            );
        }
    }

    // -------------------------------------------------------------------------
    // CAMERA RESULT
    // -------------------------------------------------------------------------

    private void processResultFromCamera(
            int destType,
            Intent intent) throws IOException {

        LOG.d(
                LOG_TAG,
                "=== processResultFromCamera ==="
        );

        LOG.d(
                LOG_TAG,
                "destType=" + destType
        );

        LOG.d(
                LOG_TAG,
                "imageUri=" + imageUri
        );

        if (imageUri == null) {

            failPicture(
                    "Captured image URI is null"
            );

            return;
        }

        InputStream input = null;

        String mimeType;

        try {

            if (allowEdit
                    && croppedUri != null
                    && croppedFilePath != null) {

                input =
                        new FileInputStream(
                                croppedFilePath
                        );

                mimeType =
                        FileHelper.getMimeTypeForExtension(
                                croppedFilePath
                        );

            } else {

                ContentResolver resolver =
                        cordova.getActivity()
                                .getContentResolver();

                input =
                        resolver.openInputStream(
                                imageUri
                        );

                mimeType =
                        FileHelper.getMimeType(
                                imageUri.toString(),
                                cordova
                        );
            }

            if (input == null) {

                failPicture(
                        "Unable to open captured image"
                );

                return;
            }

            LOG.d(
                    LOG_TAG,
                    "Captured image stream opened"
            );

            byte[] sourceData =
                    readData(input);

            if (sourceData == null
                    || sourceData.length == 0) {

                failPicture(
                        "Captured image is empty"
                );

                return;
            }

            LOG.d(
                    LOG_TAG,
                    "Captured image bytes="
                            + sourceData.length
            );

            int rotate = 0;

            ExifHelper exif =
                    new ExifHelper();

            if (encodingType == JPEG) {

                try {

                    exif.createInFile(
                            new ByteArrayInputStream(
                                    sourceData
                            )
                    );

                    exif.readExifData();

                    rotate =
                            exif.getOrientation();

                } catch (Exception e) {

                    LOG.d(
                            LOG_TAG,
                            "Unable to read EXIF: "
                                    + e.getLocalizedMessage()
                    );
                }
            }

            Bitmap bitmap = null;

            Uri galleryUri = null;

            if (saveToPhotoAlbum) {

                GalleryPathVO galleryPathVO =
                        getPicturesPath();

                if (Build.VERSION.SDK_INT
                        <= Build.VERSION_CODES.P) {

                    File galleryFile =
                            new File(
                                    galleryPathVO
                                            .getGalleryPath()
                            );

                    galleryUri =
                            Uri.fromFile(
                                    galleryFile
                            );

                    writeTakenPictureToGalleryLowerThanAndroidQ(
                            galleryUri
                    );

                } else {

                    galleryUri =
                            writeTakenPictureToGalleryStartingFromAndroidQ(
                                    galleryPathVO
                            );
                }
            }

            // -------------------------------------------------------------
            // BASE64 / DATA_URL
            // -------------------------------------------------------------

            if (destType == DATA_URL) {

                LOG.d(
                        LOG_TAG,
                        "Processing DATA_URL"
                );

                bitmap =
                        getScaledAndRotatedBitmap(
                                sourceData,
                                mimeType
                        );

                if (bitmap == null
                        && intent != null
                        && intent.getExtras() != null) {

                    Object data =
                            intent.getExtras()
                                    .get("data");

                    if (data instanceof Bitmap) {

                        bitmap =
                                (Bitmap) data;
                    }
                }

                if (bitmap == null) {

                    failPicture(
                            "Unable to create bitmap"
                    );

                    return;
                }

                LOG.d(
                        LOG_TAG,
                        "Bitmap created: "
                                + bitmap.getWidth()
                                + "x"
                                + bitmap.getHeight()
                );

                LOG.d(
                        LOG_TAG,
                        "Calling processPicture()"
                );

                processPicture(
                        bitmap,
                        encodingType
                );

                cleanup(
                        imageUri,
                        galleryUri,
                        bitmap
                );

                bitmap = null;

                return;
            }

            // -------------------------------------------------------------
            // FILE URI
            // -------------------------------------------------------------

            if (destType == FILE_URI) {

                File outputFile =
                        createCaptureFile(
                                encodingType,
                                System.currentTimeMillis()
                                        + ""
                        );

                Uri outputUri =
                        FileProvider.getUriForFile(
                                cordova.getActivity(),
                                applicationId
                                        + ".cordova.plugin.camera.provider",
                                outputFile
                        );

                if (targetHeight == -1
                        && targetWidth == -1
                        && mQuality == 100
                        && !correctOrientation) {

                    if (saveToPhotoAlbum
                            && galleryUri != null) {

                        callbackContext.success(
                                galleryUri.toString()
                        );

                    } else {

                        writeUncompressedImage(
                                imageUri,
                                outputUri
                        );

                        callbackContext.success(
                                outputUri.toString()
                        );
                    }

                    cleanup(
                            imageUri,
                            galleryUri,
                            null
                    );

                    return;
                }

                bitmap =
                        getScaledAndRotatedBitmap(
                                sourceData,
                                mimeType
                        );

                if (bitmap == null) {

                    failPicture(
                            "Unable to create bitmap"
                    );

                    return;
                }

                OutputStream outputStream =
                        cordova.getActivity()
                                .getContentResolver()
                                .openOutputStream(
                                        outputUri
                                );

                if (outputStream == null) {

                    failPicture(
                            "Unable to open output stream"
                    );

                    return;
                }

                CompressFormat format =
                        getCompressFormatForEncodingType(
                                encodingType
                        );

                boolean compressed =
                        bitmap.compress(
                                format,
                                mQuality,
                                outputStream
                        );

                outputStream.close();

                if (!compressed) {

                    failPicture(
                            "Unable to compress image"
                    );

                    return;
                }

                callbackContext.success(
                        outputUri.toString()
                );

                cleanup(
                        imageUri,
                        galleryUri,
                        bitmap
                );

                return;
            }

            failPicture(
                    "Unsupported destination type: "
                            + destType
            );

        } catch (Exception e) {

            LOG.e(
                    LOG_TAG,
                    "processResultFromCamera failed",
                    e
            );

            failPicture(
                    "Error processing camera image: "
                            + e.getLocalizedMessage()
            );

        } finally {

            if (input != null) {

                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // GALLERY
    // -------------------------------------------------------------------------

    private Uri writeTakenPictureToGalleryStartingFromAndroidQ(
            GalleryPathVO galleryPathVO) throws IOException {

        ContentResolver resolver =
                cordova.getActivity()
                        .getContentResolver();

        ContentValues values =
                new ContentValues();

        values.put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                galleryPathVO.getGalleryFileName()
        );

        values.put(
                MediaStore.MediaColumns.MIME_TYPE,
                getMimetypeForEncodingType()
        );

        values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES
        );

        Uri galleryOutputUri =
                resolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                );

        if (galleryOutputUri == null) {

            throw new IOException(
                    "Unable to create MediaStore URI"
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

        return galleryOutputUri;
    }

    private void writeTakenPictureToGalleryLowerThanAndroidQ(
            Uri galleryUri) throws IOException {

        writeUncompressedImage(
                imageUri,
                galleryUri
        );

        refreshGallery(
                galleryUri
        );
    }

    // -------------------------------------------------------------------------
    // GALLERY PATH
    // -------------------------------------------------------------------------

    private GalleryPathVO getPicturesPath() {

        String timeStamp =
                new SimpleDateFormat(
                        TIME_FORMAT
                ).format(
                        new Date()
                );

        String imageFileName =
                "IMG_"
                        + timeStamp
                        + getExtensionForEncodingType();

        File storageDir =
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES
                );

        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        return new GalleryPathVO(
                storageDir.getAbsolutePath(),
                imageFileName
        );
    }

    // -------------------------------------------------------------------------
    // MEDIA SCANNER
    // -------------------------------------------------------------------------

    private void refreshGallery(
            Uri contentUri) {

        Intent mediaScanIntent =
                new Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE
                );

        mediaScanIntent.setData(
                contentUri
        );

        cordova.getActivity()
                .sendBroadcast(
                        mediaScanIntent
                );
    }

    // -------------------------------------------------------------------------
    // MIME
    // -------------------------------------------------------------------------

    private String getMimetypeForEncodingType() {

        if (encodingType == PNG) {
            return PNG_MIME_TYPE;
        }

        if (encodingType == JPEG) {
            return JPEG_MIME_TYPE;
        }

        return "";
    }

    private String getExtensionForEncodingType() {

        if (encodingType == JPEG) {
            return JPEG_EXTENSION;
        }

        return PNG_EXTENSION;
    }

    private CompressFormat getCompressFormatForEncodingType(
            int encodingType) {

        if (encodingType == JPEG) {
            return CompressFormat.JPEG;
        }

        return CompressFormat.PNG;
    }

    // -------------------------------------------------------------------------
    // GALLERY RESULT
    // -------------------------------------------------------------------------

    private void processResultFromGallery(
            int destType,
            Intent intent) {

        if (intent == null) {

            failPicture(
                    "Gallery returned null intent"
            );

            return;
        }

        Uri uri =
                intent.getData();

        if (uri == null) {

            if (croppedUri != null) {

                uri = croppedUri;

            } else {

                failPicture(
                        "Null data from photo library"
                );

                return;
            }
        }

        String uriString =
                uri.toString();

        String mimeType =
                FileHelper.getMimeType(
                        uriString,
                        cordova
                );

        if (mediaType == VIDEO
                || !isImageMimeTypeProcessable(
                        mimeType
                )) {

            callbackContext.success(
                    uriString
            );

            return;
        }

        if (targetHeight == -1
                && targetWidth == -1
                && destType == FILE_URI
                && !correctOrientation
                && getMimetypeForEncodingType()
                .equalsIgnoreCase(mimeType)) {

            callbackContext.success(
                    uriString
            );

            return;
        }

        InputStream input;

        try {

            input =
                    cordova.getActivity()
                            .getContentResolver()
                            .openInputStream(
                                    uri
                            );

        } catch (FileNotFoundException e) {

            failPicture(
                    "Unable to open gallery input stream"
            );

            return;
        }

        if (input == null) {

            failPicture(
                    "Unable to open gallery input stream"
            );

            return;
        }

        try {

            byte[] data =
                    readData(input);

            Bitmap bitmap =
                    getScaledAndRotatedBitmap(
                            data,
                            mimeType
                    );

            if (bitmap == null) {

                failPicture(
                        "Unable to create bitmap"
                );

                input.close();

                return;
            }

            if (destType == DATA_URL) {

                processPicture(
                        bitmap,
                        encodingType
                );

            } else if (destType == FILE_URI) {

                callbackContext.success(
                        uriString
                );

            } else {

                failPicture(
                        "Invalid destination type"
                );
            }

            bitmap.recycle();

            input.close();

        } catch (Exception e) {

            try {
                input.close();
            } catch (Exception ignored) {
            }

            failPicture(
                    "Gallery processing failed: "
                            + e.getLocalizedMessage()
            );
        }
    }

    // -------------------------------------------------------------------------
    // BITMAP
    // -------------------------------------------------------------------------

    private Bitmap getScaledAndRotatedBitmap(
            byte[] data,
            String mimeType) throws IOException {

        if (data == null
                || data.length == 0) {

            return null;
        }

        /*
         * Fast path.
         */
        if (targetWidth <= 0
                && targetHeight <= 0
                && !correctOrientation) {

            try {

                return BitmapFactory.decodeStream(
                        new ByteArrayInputStream(data)
                );

            } catch (OutOfMemoryError e) {

                callbackContext.error(
                        "Out of memory decoding image"
                );

                return null;
            }
        }

        int rotate = 0;

        try {

            if (JPEG_MIME_TYPE.equalsIgnoreCase(
                    mimeType)) {

                exifData =
                        new ExifHelper();

                exifData.createInFile(
                        new ByteArrayInputStream(
                                data
                        )
                );

                exifData.readExifData();

                if (correctOrientation) {

                    ExifInterface exif =
                            new ExifInterface(
                                    new ByteArrayInputStream(
                                            data
                                    )
                            );

                    rotate =
                            exifToDegrees(
                                    exif.getAttributeInt(
                                            ExifInterface.TAG_ORIENTATION,
                                            ExifInterface.ORIENTATION_UNDEFINED
                                    )
                            );
                }
            }

        } catch (Exception e) {

            LOG.d(
                    LOG_TAG,
                    "Unable to read EXIF: "
                            + e.getLocalizedMessage()
            );

            rotate = 0;
        }

        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inJustDecodeBounds = true;

        BitmapFactory.decodeStream(
                new ByteArrayInputStream(data),
                null,
                options
        );

        if (options.outWidth <= 0
                || options.outHeight <= 0) {

            return null;
        }

        if (targetWidth <= 0
                && targetHeight <= 0) {

            targetWidth =
                    options.outWidth;

            targetHeight =
                    options.outHeight;
        }

        int rotatedWidth;
        int rotatedHeight;

        boolean rotated = false;

        if (rotate == 90
                || rotate == 270) {

            rotatedWidth =
                    options.outHeight;

            rotatedHeight =
                    options.outWidth;

            rotated = true;

        } else {

            rotatedWidth =
                    options.outWidth;

            rotatedHeight =
                    options.outHeight;
        }

        int[] widthHeight =
                calculateAspectRatio(
                        rotatedWidth,
                        rotatedHeight
                );

        options.inJustDecodeBounds =
                false;

        options.inSampleSize =
                calculateSampleSize(
                        rotatedWidth,
                        rotatedHeight,
                        widthHeight[0],
                        widthHeight[1]
                );

        if (options.inSampleSize < 1) {
            options.inSampleSize = 1;
        }

        Bitmap unscaledBitmap =
                BitmapFactory.decodeStream(
                        new ByteArrayInputStream(data),
                        null,
                        options
                );

        if (unscaledBitmap == null) {
            return null;
        }

        int scaledWidth =
                (!rotated)
                        ? widthHeight[0]
                        : widthHeight[1];

        int scaledHeight =
                (!rotated)
                        ? widthHeight[1]
                        : widthHeight[0];

        if (scaledWidth <= 0) {
            scaledWidth =
                    unscaledBitmap.getWidth();
        }

        if (scaledHeight <= 0) {
            scaledHeight =
                    unscaledBitmap.getHeight();
        }

        Bitmap scaledBitmap;

        if (unscaledBitmap.getWidth()
                == scaledWidth
                && unscaledBitmap.getHeight()
                == scaledHeight) {

            scaledBitmap =
                    unscaledBitmap;

        } else {

            scaledBitmap =
                    Bitmap.createScaledBitmap(
                            unscaledBitmap,
                            scaledWidth,
                            scaledHeight,
                            true
                    );

            if (scaledBitmap != unscaledBitmap) {
                unscaledBitmap.recycle();
            }
        }

        if (correctOrientation
                && rotate != 0) {

            Matrix matrix =
                    new Matrix();

            matrix.setRotate(
                    rotate
            );

            try {

                Bitmap rotatedBitmap =
                        Bitmap.createBitmap(
                                scaledBitmap,
                                0,
                                0,
                                scaledBitmap.getWidth(),
                                scaledBitmap.getHeight(),
                                matrix,
                                true
                        );

                if (rotatedBitmap
                        != scaledBitmap) {

                    scaledBitmap.recycle();
                }

                scaledBitmap =
                        rotatedBitmap;

                orientationCorrected =
                        true;

            } catch (OutOfMemoryError e) {

                orientationCorrected =
                        false;
            }
        }

        return scaledBitmap;
    }

    // -------------------------------------------------------------------------
    // ASPECT RATIO
    // -------------------------------------------------------------------------

    public int[] calculateAspectRatio(
            int origWidth,
            int origHeight) {

        int newWidth =
                targetWidth;

        int newHeight =
                targetHeight;

        if (newWidth <= 0
                && newHeight <= 0) {

            newWidth =
                    origWidth;

            newHeight =
                    origHeight;

        } else if (newWidth > 0
                && newHeight <= 0) {

            newHeight =
                    (int) (
                            (double) newWidth
                                    / (double) origWidth
                                    * origHeight
                    );

        } else if (newWidth <= 0
                && newHeight > 0) {

            newWidth =
                    (int) (
                            (double) newHeight
                                    / (double) origHeight
                                    * origWidth
                    );

        } else {

            double newRatio =
                    newWidth
                            / (double) newHeight;

            double originalRatio =
                    origWidth
                            / (double) origHeight;

            if (originalRatio > newRatio) {

                newHeight =
                        (newWidth * origHeight)
                                / origWidth;

            } else if (originalRatio < newRatio) {

                newWidth =
                        (newHeight * origWidth)
                                / origHeight;
            }
        }

        return new int[]{
                newWidth,
                newHeight
        };
    }

    // -------------------------------------------------------------------------
    // SAMPLE SIZE
    // -------------------------------------------------------------------------

    public static int calculateSampleSize(
            int srcWidth,
            int srcHeight,
            int dstWidth,
            int dstHeight) {

        if (dstWidth <= 0
                || dstHeight <= 0) {

            return 1;
        }

        final float srcAspect =
                (float) srcWidth
                        / (float) srcHeight;

        final float dstAspect =
                (float) dstWidth
                        / (float) dstHeight;

        int sample;

        if (srcAspect > dstAspect) {

            sample =
                    srcWidth / dstWidth;

        } else {

            sample =
                    srcHeight / dstHeight;
        }

        return Math.max(
                1,
                sample
        );
    }

    // -------------------------------------------------------------------------
    // EXIF
    // -------------------------------------------------------------------------

    private int exifToDegrees(
            int exifOrientation) {

        if (exifOrientation
                == ExifInterface.ORIENTATION_ROTATE_90) {

            return 90;

        } else if (
                exifOrientation
                        == ExifInterface.ORIENTATION_ROTATE_180) {

            return 180;

        } else if (
                exifOrientation
                        == ExifInterface.ORIENTATION_ROTATE_270) {

            return 270;
        }

        return 0;
    }

    // -------------------------------------------------------------------------
    // WRITE IMAGE
    // -------------------------------------------------------------------------

    private void writeUncompressedImage(
            InputStream input,
            Uri destination)
            throws IOException {

        if (input == null) {

            throw new IOException(
                    "Input stream is null"
            );
        }

        if (destination == null) {

            throw new IOException(
                    "Destination URI is null"
            );
        }

        OutputStream output =
                null;

        try {

            output =
                    cordova.getActivity()
                            .getContentResolver()
                            .openOutputStream(
                                    destination
                            );

            if (output == null) {

                throw new IOException(
                        "Unable to open destination stream"
                );
            }

            byte[] buffer =
                    new byte[8192];

            int length;

            while (
                    (length =
                            input.read(buffer))
                            != -1) {

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

            try {
                input.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void writeUncompressedImage(
            Uri source,
            Uri destination)
            throws IOException {

        InputStream input =
                FileHelper.getInputStreamFromUriString(
                        source.toString(),
                        cordova
                );

        writeUncompressedImage(
                input,
                destination
        );
    }

    // -------------------------------------------------------------------------
    // BASE64
    // -------------------------------------------------------------------------

    public void processPicture(
            Bitmap bitmap,
            int encodingType) {

        LOG.d(
                LOG_TAG,
                "=== processPicture ==="
        );

        if (bitmap == null) {

            failPicture(
                    "Bitmap is null"
            );

            return;
        }

        ByteArrayOutputStream dataStream =
                new ByteArrayOutputStream();

        try {

            CompressFormat format =
                    getCompressFormatForEncodingType(
                            encodingType
                    );

            boolean compressed =
                    bitmap.compress(
                            format,
                            mQuality,
                            dataStream
                    );

            if (!compressed) {

                failPicture(
                        "Bitmap compression failed"
                );

                return;
            }

            byte[] imageBytes =
                    dataStream.toByteArray();

            LOG.d(
                    LOG_TAG,
                    "Compressed bytes="
                            + imageBytes.length
            );

            String mimeType =
                    encodingType == PNG
                            ? PNG_MIME_TYPE
                            : JPEG_MIME_TYPE;

            String base64 =
                    Base64.encodeToString(
                            imageBytes,
                            Base64.NO_WRAP
                    );

            LOG.d(
                    LOG_TAG,
                    "Base64 length="
                            + base64.length()
            );

            String result =
                    "data:"
                            + mimeType
                            + ";base64,"
                            + base64;

            LOG.d(
                    LOG_TAG,
                    "Sending Base64 result to JavaScript"
            );

            LOG.d(
                    LOG_TAG,
                    "Result length="
                            + result.length()
            );

            callbackContext.success(
                    result
            );

        } catch (Exception e) {

            LOG.e(
                    LOG_TAG,
                    "Base64 processing failed",
                    e
            );

            failPicture(
                    "Error compressing image: "
                            + e.getLocalizedMessage()
            );

        } finally {

            try {
                dataStream.close();
            } catch (Exception ignored) {
            }
        }
    }

    // -------------------------------------------------------------------------
    // CLEANUP
    // -------------------------------------------------------------------------

    private void cleanup(
            Uri oldImage,
            Uri newImage,
            Bitmap bitmap) {

        if (bitmap != null
                && !bitmap.isRecycled()) {

            bitmap.recycle();
        }

        /*
         * Delete temporary camera file.
         */
        if (oldImage != null) {

            try {

                String path =
                        FileHelper.stripFileProtocol(
                                oldImage.toString()
                        );

                if (path != null) {

                    File file =
                            new File(path);

                    if (file.exists()) {
                        file.delete();
                    }
                }

            } catch (Exception e) {

                LOG.d(
                        LOG_TAG,
                        "Unable to delete temporary image: "
                                + e.getLocalizedMessage()
                );
            }
        }

        if (saveToPhotoAlbum
                && newImage != null) {

            scanForGallery(
                    newImage
            );
        }

        System.gc();
    }

    // -------------------------------------------------------------------------
    // FAILURE
    // -------------------------------------------------------------------------

    public void failPicture(
            String error) {

        LOG.e(
                LOG_TAG,
                "Camera failure: " + error
        );

        if (callbackContext != null) {

            callbackContext.error(
                    error == null
                            ? "Unknown camera error"
                            : error
            );
        }
    }

    // -------------------------------------------------------------------------
    // GALLERY SCAN
    // -------------------------------------------------------------------------

    private void scanForGallery(
            Uri newImage) {

        this.scanMe =
                newImage;

        if (this.conn != null) {

            try {
                this.conn.disconnect();
            } catch (Exception ignored) {
            }
        }

        this.conn =
                new MediaScannerConnection(
                        cordova.getActivity()
                                .getApplicationContext(),
                        this
                );

        this.conn.connect();
    }

    @Override
    public void onMediaScannerConnected() {

        try {

            if (conn != null
                    && scanMe != null) {

                conn.scanFile(
                        scanMe.toString(),
                        "image/*"
                );
            }

        } catch (Exception e) {

            LOG.e(
                    LOG_TAG,
                    "Unable to scan gallery file",
                    e
            );
        }
    }

    @Override
    public void onScanCompleted(
            String path,
            Uri uri) {

        if (conn != null) {

            try {
                conn.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    // -------------------------------------------------------------------------
    // PERMISSION RESULT
    // -------------------------------------------------------------------------

    @Override
    public void onRequestPermissionResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        for (int result : grantResults) {

            if (result
                    == PackageManager.PERMISSION_DENIED) {

                if (callbackContext != null) {

                    callbackContext.error(
                            PERMISSION_DENIED_ERROR
                    );
                }

                return;
            }
        }

        switch (requestCode) {

            case TAKE_PIC_SEC:

                takePicture(
                        this.destType,
                        this.encodingType
                );

                break;

            case SAVE_TO_ALBUM_SEC:

                getImage(
                        this.srcType,
                        this.destType
                );

                break;

            default:

                break;
        }
    }

    // -------------------------------------------------------------------------
    // ACTIVITY RESULT
    // -------------------------------------------------------------------------

    @Override
    public void onActivityResult(
            int requestCode,
            int resultCode,
            Intent intent) {

        LOG.d(
                LOG_TAG,
                "=== onActivityResult ==="
        );

        LOG.d(
                LOG_TAG,
                "requestCode="
                        + requestCode
        );

        LOG.d(
                LOG_TAG,
                "resultCode="
                        + resultCode
        );

        LOG.d(
                LOG_TAG,
                "intent="
                        + (intent != null)
        );

        /*
         * Crop result.
         */
        if (requestCode >= CROP_CAMERA) {

            if (resultCode
                    == Activity.RESULT_OK) {

                try {

                    int cropDestType =
                            requestCode
                                    - CROP_CAMERA;

                    processResultFromCamera(
                            cropDestType,
                            intent
                    );

                } catch (Exception e) {

                    LOG.e(
                            LOG_TAG,
                            "Crop result failed",
                            e
                    );

                    failPicture(
                            "Crop result failed: "
                                    + e.getLocalizedMessage()
                    );
                }

            } else if (
                    resultCode
                            == Activity.RESULT_CANCELED) {

                failPicture(
                        "No Image Selected"
                );

            } else {

                failPicture(
                        "Crop did not complete"
                );
            }

            return;
        }

        int resultSrcType =
                (requestCode / 16) - 1;

        int resultDestType =
                (requestCode % 16) - 1;

        LOG.d(
                LOG_TAG,
                "srcType="
                        + resultSrcType
        );

        LOG.d(
                LOG_TAG,
                "destType="
                        + resultDestType
        );

        /*
         * CAMERA
         */
        if (resultSrcType == CAMERA) {

            if (resultCode
                    == Activity.RESULT_OK) {

                LOG.d(
                        LOG_TAG,
                        "=== CAMERA RESULT_OK ==="
                );

                LOG.d(
                        LOG_TAG,
                        "imageUri="
                                + imageUri
                );

                try {

                    if (imageUri == null) {

                        failPicture(
                                "Camera returned successfully but imageUri is null"
                        );

                        return;
                    }

                    /*
                     * Verify that the camera actually
                     * wrote the image.
                     */
                    File imageFile =
                            new File(
                                    imageUri.getPath()
                            );

                    LOG.d(
                            LOG_TAG,
                            "imageUri scheme="
                                    + imageUri.getScheme()
                    );

                    if (allowEdit) {

                        performCrop(
                                imageUri,
                                resultDestType,
                                intent
                        );

                    } else {

                        processResultFromCamera(
                                resultDestType,
                                intent
                        );
                    }

                } catch (Exception e) {

                    LOG.e(
                            LOG_TAG,
                            "Camera result processing failed",
                            e
                    );

                    failPicture(
                            "Camera result processing failed: "
                                    + e.getLocalizedMessage()
                    );
                }

            } else if (
                    resultCode
                            == Activity.RESULT_CANCELED) {

                LOG.d(
                        LOG_TAG,
                        "Camera cancelled"
                );

                failPicture(
                        "No Image Selected"
                );

            } else {

                failPicture(
                        "Camera did not complete. Result code: "
                                + resultCode
                );
            }

            return;
        }

        /*
         * PHOTO LIBRARY
         */
        if (resultSrcType == PHOTOLIBRARY
                || resultSrcType == SAVEDPHOTOALBUM) {

            if (resultCode
                    == Activity.RESULT_OK
                    && intent != null) {

                final Intent finalIntent =
                        intent;

                final int finalDestType =
                        resultDestType;

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
                    resultCode
                            == Activity.RESULT_CANCELED) {

                failPicture(
                        "No Image Selected"
                );

            } else {

                failPicture(
                        "Selection did not complete"
                );
            }
        }
    }

    // -------------------------------------------------------------------------
    // MIME CHECK
    // -------------------------------------------------------------------------

    private boolean isImageMimeTypeProcessable(
            String mimeType) {

        if (mimeType == null) {
            return false;
        }

        return JPEG_MIME_TYPE.equalsIgnoreCase(
                mimeType
        )
                || PNG_MIME_TYPE.equalsIgnoreCase(
                mimeType
        )
                || HEIC_MIME_TYPE.equalsIgnoreCase(
                mimeType
        );
    }

    // -------------------------------------------------------------------------
    // STATE SAVE
    // -------------------------------------------------------------------------

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

        /*
         * Save camera direction.
         */
        state.putInt(
                "cameraDirection",
                cameraDirection
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

    // -------------------------------------------------------------------------
    // STATE RESTORE
    // -------------------------------------------------------------------------

    @Override
    public void onRestoreStateForActivityResult(
            Bundle state,
            CallbackContext callbackContext) {

        if (state == null) {
            this.callbackContext =
                    callbackContext;
            return;
        }

        this.destType =
                state.getInt(
                        "destType"
                );

        this.srcType =
                state.getInt(
                        "srcType"
                );

        this.mQuality =
                state.getInt(
                        "mQuality"
                );

        this.targetWidth =
                state.getInt(
                        "targetWidth"
                );

        this.targetHeight =
                state.getInt(
                        "targetHeight"
                );

        this.encodingType =
                state.getInt(
                        "encodingType"
                );

        this.mediaType =
                state.getInt(
                        "mediaType"
                );

        /*
         * Restore camera direction.
         *
         * Defaults to BACK for older saved states.
         */
        this.cameraDirection =
                state.getInt(
                        "cameraDirection",
                        CAMERA_DIRECTION_BACK
                );

        if (this.cameraDirection
                != CAMERA_DIRECTION_BACK
                && this.cameraDirection
                != CAMERA_DIRECTION_FRONT) {

            this.cameraDirection =
                    CAMERA_DIRECTION_BACK;
        }

        this.allowEdit =
                state.getBoolean(
                        "allowEdit"
                );

        this.correctOrientation =
                state.getBoolean(
                        "correctOrientation"
                );

        this.saveToPhotoAlbum =
                state.getBoolean(
                        "saveToPhotoAlbum"
                );

        if (state.containsKey(
                CROPPED_URI_KEY)) {

            this.croppedFilePath =
                    state.getString(
                            CROPPED_URI_KEY
                    );

            if (croppedFilePath != null) {

                this.croppedUri =
                        Uri.parse(
                                croppedFilePath
                        );
            }
        }

        if (state.containsKey(
                IMAGE_URI_KEY)) {

            String uri =
                    state.getString(
                            IMAGE_URI_KEY
                    );

            if (uri != null) {

                this.imageUri =
                        Uri.parse(
                                uri
                        );
            }
        }

        this.callbackContext =
                callbackContext;
    }

    // -------------------------------------------------------------------------
    // READ DATA
    // -------------------------------------------------------------------------

    private byte[] readData(
            InputStream input)
            throws IOException {

        if (input == null) {
            return null;
        }

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        byte[] data =
                new byte[8192];

        int bytesRead;

        while (
                (bytesRead =
                        input.read(data))
                        != -1) {

            buffer.write(
                    data,
                    0,
                    bytesRead
            );
        }

        return buffer.toByteArray();
    }
}
