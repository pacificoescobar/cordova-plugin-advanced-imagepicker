package de.einfachhans.AdvancedImagePicker;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.os.Build;
import android.Manifest;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia;
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gun0912.tedimagepicker.builder.TedImagePicker;

public class AdvancedImagePicker extends CordovaPlugin {

    private CallbackContext _callbackContext;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    private boolean asBase64;
    private boolean asJpeg;
    private int max;

    protected static String[] permissions;
    static {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{ Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.CAMERA };
        } else {
            permissions = new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA };
        }
    }

    @Override
    public void pluginInitialize(){
        super.pluginInitialize();
        try{
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pickMedia = cordova.getActivity().registerForActivityResult(new PickMultipleVisualMedia(10), result -> {
                        if (!result.isEmpty()) {
                            handleResult(result, false, "image", false);
                        } else {
                            returnError(AdvancedImagePickerErrorCodes.PickerCanceled, "User cancelled");
                        }
                    });
                }
            });
        } catch(Exception e){
            e.printStackTrace();
            this.returnError(AdvancedImagePickerErrorCodes.UnknownError, e.getMessage());
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this._callbackContext = callbackContext;

        try {
            if (action.equals("present")) {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    this.presentAndroidPicker(args);
                else
                    this.presentFullScreen(args);
                return true;
            } else {
                returnError(AdvancedImagePickerErrorCodes.UnsupportedAction);
                return false;
            }
        } catch (JSONException exception) {
            returnError(AdvancedImagePickerErrorCodes.WrongJsonObject);
        } catch (Exception exception) {
            returnError(AdvancedImagePickerErrorCodes.UnknownError, exception.getMessage());
        }

        return true;
    }

    private void presentFullScreen(JSONArray args) throws JSONException {
        JSONObject options = args.getJSONObject(0);
        String mediaType = options.optString("mediaType", "IMAGE");
        boolean showCameraTile = options.optBoolean("showCameraTile", true);
        String scrollIndicatorDateFormat = options.optString("scrollIndicatorDateFormat");
        boolean showTitle = options.optBoolean("showTitle", true);
        String title = options.optString("title");
        boolean zoomIndicator = options.optBoolean("zoomIndicator", true);
        int min = options.optInt("min");
        String defaultMinCountMessage = "You need to select a minimum of " + (min == 1 ? "one picture" : min + " pictures");
        String minCountMessage = options.optString("minCountMessage", defaultMinCountMessage);
        int max = options.optInt("max");
        String defaultMaxCountMessage = "You can select a maximum of " + max + " pictures";
        String maxCountMessage = options.optString("maxCountMessage", defaultMaxCountMessage);
        String buttonText = options.optString("buttonText");
        boolean asDropdown = options.optBoolean("asDropdown");
        boolean asBase64 = options.optBoolean("asBase64");
        boolean asJpeg = options.optBoolean("asJpeg");

        if (min < 0 || max < 0) {
            this.returnError(AdvancedImagePickerErrorCodes.WrongJsonObject, "Min and Max can not be less then zero.");
            return;
        }

        if (max != 0 && max < min) {
            this.returnError(AdvancedImagePickerErrorCodes.WrongJsonObject, "Max can not be smaller than Min.");
            return;
        }

        TedImagePicker.Builder builder = TedImagePicker.with(this.cordova.getContext())
                .showCameraTile(showCameraTile)
                .showTitle(showTitle)
                .zoomIndicator(zoomIndicator)
                .errorListener(error -> {
                    this.returnError(AdvancedImagePickerErrorCodes.UnknownError, error.getMessage());
                })
                .cancelListener(() -> {
                    this.returnError(AdvancedImagePickerErrorCodes.PickerCanceled, "User cancelled");
                });

        if (!scrollIndicatorDateFormat.equals("")) {
            builder.scrollIndicatorDateFormat(scrollIndicatorDateFormat);
        }
        if (!title.equals("")) {
            builder.title(title);
        }
        if (!buttonText.equals("")) {
            builder.buttonText(buttonText);
        }
        if (asDropdown) {
            builder.dropDownAlbum();
        }
        String type = "image";
        if (mediaType.equals("VIDEO")) {
            builder.video();
            type = "video";
        }

        if (max == 1) {
            String finalType = type;
            builder.start(result -> {
                this.handleResult(result, asBase64, finalType, asJpeg);
            });
        } else {
            if (min > 0) {
                builder.min(min, minCountMessage);
            }
            if (max > 0) {
                builder.max(max, maxCountMessage);
            }

            String finalType1 = type;
            try{
                builder.startMultiImage(result -> {
                    this.handleResult(result, asBase64, finalType1, asJpeg);
                });
            } catch (Exception e) {
                e.printStackTrace();
                this.returnError(AdvancedImagePickerErrorCodes.UnknownError, e.getMessage());
            }
        }
    }

    private void presentAndroidPicker(JSONArray args) throws JSONException {        
        JSONObject options = args.getJSONObject(0);
        String mediaType = options.optString("mediaType", "IMAGE");
        boolean showCameraTile = options.optBoolean("showCameraTile", true);
        String scrollIndicatorDateFormat = options.optString("scrollIndicatorDateFormat");
        boolean showTitle = options.optBoolean("showTitle", true);
        String title = options.optString("title");
        boolean zoomIndicator = options.optBoolean("zoomIndicator", true);
        int min = options.optInt("min");
        String defaultMinCountMessage = "You need to select a minimum of " + (min == 1 ? "one picture" : min + " pictures");
        String minCountMessage = options.optString("minCountMessage", defaultMinCountMessage);
        int max = options.optInt("max");
        String defaultMaxCountMessage = "You can select a maximum of " + max + " pictures";
        String maxCountMessage = options.optString("maxCountMessage", defaultMaxCountMessage);
        String buttonText = options.optString("buttonText");
        boolean asDropdown = options.optBoolean("asDropdown");
        boolean asBase64 = options.optBoolean("asBase64");
        boolean asJpeg = options.optBoolean("asJpeg");
        boolean storagePermission = this.hasPermissions(this.permissions);

        this.asBase64 = asBase64;
        this.asJpeg = asJpeg;
        this.max = max;

        if (min < 0 || max < 0) {
            this.returnError(AdvancedImagePickerErrorCodes.WrongJsonObject, "Min and Max can not be less then zero.");
            return;
        }

        if (max != 0 && max < min) {
            this.returnError(AdvancedImagePickerErrorCodes.WrongJsonObject, "Max can not be smaller than Min.");
            return;
        }

        if(storagePermission){
            try{
                this.pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(mediaType.equals("VIDEO") ? PickVisualMedia.VideoOnly.INSTANCE : PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
            } catch (Exception e) {
                e.printStackTrace();
                this.returnError(AdvancedImagePickerErrorCodes.UnknownError, e.getMessage());
            }
        } else {
            PermissionHelper.requestPermissions(this, 0, permissions);
        }
    }

    private void handleResult(Uri uri, boolean asBase64, String type, boolean asJpeg) {
        List<Uri> list = new ArrayList<>();
        list.add(uri);
        this.handleResult(list, asBase64, type, asJpeg);
    }

    private void handleResult(List<? extends Uri> uris, boolean asBase64, String type, boolean asJpeg) {
        JSONArray result = new JSONArray();
        for (Uri uri : uris) {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("type", type);
            resultMap.put("isBase64", asBase64);
            if (asBase64) {
                try {
                    resultMap.put("src", type.equals("video") ? this.encodeVideo(uri) : this.encodeImage(uri, asJpeg));
                } catch (Exception e) {
                    e.printStackTrace();
                    this.returnError(AdvancedImagePickerErrorCodes.UnknownError, e.getMessage());
                    return;
                }
            } else {
                resultMap.put("src", uri.toString());
            }
            result.put(new JSONObject(resultMap));
        }
        Log.v("AIP", "result: " + result.toString());
        this._callbackContext.success(result);
    }

    private String encodeVideo(Uri uri) throws IOException {
        final InputStream videoStream = this.cordova.getContext().getContentResolver().openInputStream(uri);
        byte[] bytes;
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((bytesRead = videoStream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        bytes = output.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private String encodeImage(Uri uri, boolean asJpeg) throws FileNotFoundException {
        final InputStream imageStream = this.cordova.getContext().getContentResolver().openInputStream(uri);
        final Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
        return encodeImage(selectedImage, asJpeg);
    }

    private String encodeImage(Bitmap bm, boolean asJpeg) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (asJpeg) {
            bm.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        } else {
            bm.compress(Bitmap.CompressFormat.PNG, 80, baos);
        }
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    private void returnError(AdvancedImagePickerErrorCodes errorCode) {
        returnError(errorCode, null);
    }

    private void returnError(AdvancedImagePickerErrorCodes errorCode, String message) {
        if (_callbackContext != null) {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("code", errorCode.value);
            resultMap.put("message", message == null ? "" : message);
            _callbackContext.error(new JSONObject(resultMap));
            _callbackContext = null;
        }
    }

    private boolean hasPermissions(String[] permissions) {
        for (String permission: permissions) {
            if (!PermissionHelper.hasPermission(this, permission)) {
                return false;
            }
        }
        return true;
    }
}
