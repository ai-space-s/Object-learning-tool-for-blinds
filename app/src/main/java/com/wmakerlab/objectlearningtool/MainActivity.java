// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.wmakerlab.objectlearningtool;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.exifinterface.media.ExifInterface;
// ContentResolver dependency
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.formats.proto.LandmarkProto.Landmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutioncore.VideoInput;
import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import okhttp3.OkHttpClient;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/** Main activity of MediaPipe Hands app. */
public class MainActivity extends AppCompatActivity implements SensorEventListener {
  private static final String TAG = "MainActivity";
  public TextToSpeech tts;
  private Hands hands;
  // Run the pipeline and the model inference on GPU or CPU.
  private static final boolean RUN_ON_GPU = true;
  private static final int REQUEST_CODE = 1234; // can be any unique number
  private ImageView overlayImageView;
  private ProgressBar overlayProgressBar;
  public TextView overlayTextView;
  private WindowManager windowManager;
  private ArrayList<Object> touchRegionDiffs;
  private String [] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};
  private static final int PERMISSIONS_REQUEST_CAMERA_AUDIO = 100;
  private static final int REQUEST_MEDIA_PROJECTION = 101;

  private enum InputSource {
    UNKNOWN,
    IMAGE,
    VIDEO,
    CAMERA,
  }
  private InputSource inputSource = InputSource.UNKNOWN;

  // Image demo UI and image loader components.
  private ActivityResultLauncher<Intent> imageGetter;
  private HandsResultImageView imageView;
  // Video demo UI and video loader components.
  private VideoInput videoInput;
  private ActivityResultLauncher<Intent> videoGetter;
  // Live camera demo UI and camera components.
  private CameraInput cameraInput;
  public HandsResultGlRenderer resultRenderer;
  private boolean cameraFacingFront;
  private SolutionGlSurfaceView<HandsResult> glSurfaceView;
  private boolean permissionToRecordAccepted = false;
  @SuppressLint("StaticFieldLeak")
  private static MainActivity instance;
  private Handler handler;
  private PointF startTouchPoint;
  private BoundingBoxView boundingBoxView;
  private int height;
  private int width;
  private int calibrateHeight;
  private int calibrateWidth;
  public int statusbarHeight;
  public int layoutHeight;
  private boolean cameraStarted;
  private int selectedCorner;
  private int touchThreshold;
  public WindowManager.LayoutParams overlayImageParams;
  public WindowManager.LayoutParams lastValidParams;
  //private ScaleGestureDetector scaleGestureDetector;
  private boolean isVerticalPinch;
  private int editMode;
  private float prevWidth;
  private float prevHeight;
  private int prevX;
  private int prevY;
  private final String[] modes = {
          "현재 모드 : Overlay Image 편집 모드",
          "현재 모드 : Bounding Box 편집 모드",
          "현재 모드 : Label 편집 모드"
  };
  private GradientDrawable border;
  private AlertDialog currentDialog;
  private ArrayList<LabeledRectF> targetRegions;
  private ArrayList<PointF> diffs;
  private SeekBar ageSeekBar;
  private TextView ageTextView;
  private RadioGroup genderRadioGroup;
  private SharedPreferences prefs;
  //public int navigationbarHeight;
  public int age;
  public String gender;
  private SensorManager sensorManager;
  private final float[] rotationMatrix = new float[9];
  public final float[] orientationAngles = new float[3];
  private GestureDetector gestureDetector;
  //public boolean drawUIMode;
  private ImageCapture imageCapture;
  private ImageAnalysis imageAnalysis;
  private final String calibrationColor = "FE5E9C";
  private Mat cameraFrame;
  private ArrayList<org.opencv.core.Point> overlayPointsList;
  private ArrayList<org.opencv.core.Point> tempOverlayPointsList;
  private ArrayList<org.opencv.core.Point> cameraPointsList;
  private List<Pair<Integer, Integer>> commonResolutions;
  private MediaProjectionManager mProjectionManager;
  private MediaProjection mMediaProjection;
  private Intent serviceIntent;
  private ImageReader mImageReader;
  private WindowMetrics projectionMetrics;
  private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
  private Runnable clearTextRunnable;
  public boolean recognizing;
  public String chatGPTAPIKey = "sk-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
  public String azureAPIKey = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    setContentView(R.layout.activity_main);
    setupStaticImageDemoUiComponents();
    setupVideoDemoUiComponents();
    setupLiveDemoUiComponents();
    sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
    instance = this;
    handler = new Handler();
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    editMode = 0;
    boundingBoxView = findViewById(R.id.bounding_box_view);
    boundingBoxView.setActivityContext(this);
    DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

    height = displayMetrics.heightPixels;
    width = displayMetrics.widthPixels;

    Context windowContext;
    WindowManager windowManager;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        windowContext = this.createWindowContext(this.getDisplay(), WindowManager.LayoutParams.TYPE_APPLICATION, null);
        windowManager = windowContext.getSystemService(WindowManager.class);
      }
      else{
        windowManager = getSystemService(WindowManager.class);
      }
      projectionMetrics = windowManager.getMaximumWindowMetrics();
    } else {
      windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
      if (windowManager != null) {
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
      }
    }

    initCommonResolution();
    Pair<Integer, Integer> calibrateSize = getClosestCommonResolution(width, height);
    calibrateWidth = calibrateSize.second;
    calibrateHeight = calibrateSize.first;

    selectedCorner = -1;
    touchThreshold = 100;
    //drawUIMode = true;
    border = new GradientDrawable();
    border.setColor(Color.TRANSPARENT); //The color of the ImageView
    border.setStroke(10, Color.BLACK); //border thickness and color
    border.setCornerRadius(5); //The radius for the corners
    currentDialog = null;
    boundingBoxView.drawCircleMode = true;
    ageSeekBar = findViewById(R.id.ageSeekBar);
    ageTextView = findViewById(R.id.ageTextView);
    genderRadioGroup = findViewById(R.id.genderRadioGroup);
    prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
    imageCapture = new ImageCapture.Builder().build();
    overlayPointsList = new ArrayList<>();
    cameraPointsList = new ArrayList<>();
    recognizing = false;
    //isFrameRequested = false;
    OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled */) {
      @Override
      public void handleOnBackPressed() {
        showExitDialog();
      }
    };
    getOnBackPressedDispatcher().addCallback(this, callback);
    clearTextRunnable = new Runnable() {
      @Override
      public void run() {
        overlayTextView.setText("");
      }
    };
    gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
      @Override
      public boolean onDoubleTap(MotionEvent e) {
        //디버깅용 기능 화면 UI 토글
//        resultRenderer.drawUIMode = !resultRenderer.drawUIMode;
//        if(resultRenderer.drawUIMode){
//          overlayImageView.setAlpha(0.6f);
//        }
//        else{
//          overlayImageView.setAlpha(0f);
//        }

        //원래 기능
        overlayImageView.setVisibility(View.INVISIBLE);
        resultRenderer.drawUIMode = false;
        new Handler().postDelayed(() -> {
          //resultRenderer.uploadAudioFile();
          calibrate();
          overlayImageView.setVisibility(View.VISIBLE);
          resultRenderer.drawUIMode = true;
        }, 500);

        //디버깅용 기능 바로 음성인식 모드 진입
//        if(resultRenderer.streamer.isStreaming){
//          resultRenderer.stopRecording(false);
//        }
//        else{
//          resultRenderer.streamer.startStreaming();
//          resultRenderer.playDing();
//          recognizing = true;
//        }

        //디버깅용 기능 바로 지정된 텍스트로 ChatGPT 질문
//        resultRenderer.askGptStreamingTest();
        return true;
      }
    });
    System.loadLibrary("opencv_java4");
    if (!OpenCVLoader.initDebug()) {
      Log.e(TAG, "OpenCV initialization failed.");
    } else {
      Log.d(TAG, "OpenCV initialization succeeded.");
    }

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
              new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
              PERMISSIONS_REQUEST_CAMERA_AUDIO);
    }
    if (!checkDrawOverlayPermission()) {
      // if permission is not granted, request for it
      requestDrawOverlayPermission();
    } else {
      // if permission is granted, show overlay

      showOverlayImage(0f, 0.2f, 0.46f, 0.81f, 0.2f, 0.3f);
      //showOverlayImage(0f, 0f, 0f, 0f, 0f, 0f);
      showOverlayText();
    }
    getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
      @Override
      public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

      @Override
      public void onActivityStarted(@NonNull Activity activity) {
        // Show the overlay when the app comes to foreground
        if (overlayImageView != null) {
          overlayImageView.setVisibility(View.VISIBLE);
        }
        if (overlayTextView != null) {
          overlayTextView.setVisibility(View.VISIBLE);
        }
      }

      @Override
      public void onActivityResumed(@NonNull Activity activity) {}

      @Override
      public void onActivityPaused(@NonNull Activity activity) {}

      @Override
      public void onActivityStopped(@NonNull Activity activity) {
        if (overlayImageView != null) {
          if (currentDialog == null || !currentDialog.isShowing()) {
            overlayImageView.setVisibility(View.GONE);
          }
        }
        if (overlayTextView != null) {
          overlayTextView.setVisibility(View.GONE);
        }
      }

      @Override
      public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

      @Override
      public void onActivityDestroyed(@NonNull Activity activity) {}
    });

    Button loadImageButton = findViewById(R.id.button_change_mode);
    loadImageButton.setOnClickListener(
            v -> {
              switchMode();
            });
    Button saveBBoxButton = findViewById(R.id.button_save_bbox);
    saveBBoxButton.setOnClickListener(
            v -> {
              boundingBoxView.saveBoundingBoxes(overlayImageParams);
            });
    Button loadBBoxButton = findViewById(R.id.button_load_bbox);
    loadBBoxButton.setOnClickListener(
            v -> {
              boundingBoxView.loadBoundingBoxes(() -> {
                overlayImageParams.x = boundingBoxView.xParams;
                overlayImageParams.y = boundingBoxView.yParams;
                overlayImageParams.width = boundingBoxView.widthParams;
                overlayImageParams.height = boundingBoxView.heightParams;
                updateOverlayImagePosition();
              });
            });
    Button clearBBoxButton = findViewById(R.id.button_clear_bbox);
    clearBBoxButton.setOnClickListener(
            v -> {
              boundingBoxView.clearBoundingBoxes();
            });
    Button undoBBoxButton = findViewById(R.id.button_undo_bbox);
    undoBBoxButton.setOnClickListener(
            v -> {
              boundingBoxView.removeLastBoundingBox();
            });
//    Button calibrateButton = findViewById(R.id.button_calibrate);
//    calibrateButton.setOnClickListener(
//            v -> {
//              calibrate();
//            });
    Button chatGPTAPIKeyButton = findViewById(R.id.button_chatgpt_apikey);
    chatGPTAPIKeyButton.setOnClickListener(
            v -> {
              setChatGPTAPIKey();
            });
    Button azureAPIKeyButton = findViewById(R.id.button_azure_apikey);
    azureAPIKeyButton.setOnClickListener(
            v -> {
              setAzureGPTAPIKey();
            });
    //boundingBoxView.loadBoundingBoxes();
    statusbarHeight = (int)(getStatusBarHeight());
    //layoutHeight = (int)((getLayoutHeight("buttons") + getLayoutHeight("buttons2")));
    layoutHeight = 0;
    //navigationbarHeight = getNavigationBarHeight();
    boundingBoxView.layoutHeight = layoutHeight;
    boundingBoxView.statusbarHeight = statusbarHeight;
    cameraStarted = false;
    boundingBoxView.layoutHeight = 0;
    boundingBoxView.touchThreshold = touchThreshold;
    boundingBoxView.updateParams(overlayImageParams);

    ageSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        ageTextView.setText("나이: " + progress);
        age = progress;
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
      }
    });
    age = prefs.getInt("age", 10);
    gender = prefs.getString("gender", "남자");
        ageSeekBar.setProgress(age);
    if ("남자".equals(gender)) {
      ((RadioButton) findViewById(R.id.radioButtonMale)).setChecked(true);
    } else {
      ((RadioButton) findViewById(R.id.radioButtonFemale)).setChecked(true);
    }
    genderRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
      if (checkedId == R.id.radioButtonMale) {
        gender = "남자";
      } else if (checkedId == R.id.radioButtonFemale) {
        gender = "여자";
      }
    });

    chatGPTAPIKey = prefs.getString("chatgptapikey", "NONE").trim();
    azureAPIKey = prefs.getString("azureapikey", "NONE").trim();

    serviceIntent = new Intent(this, MediaProjectionService.class);
    requestScreenCapture();
    tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
      @Override
      public void onInit(int status) {
        if (status != TextToSpeech.ERROR) {
          tts.setLanguage(Locale.KOREAN);
          tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
              //runOnUiThread(() -> updateText(utteranceId));
              updateText(utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
              if ("띵 소리 후 말씀하세요...".equals(utteranceId)) {
                //resultRenderer.startRecording();
                resultRenderer.streamer.startStreaming();
                resultRenderer.playDing();
                recognizing = true;
              }
              else if("인식 중입니다...".equals(utteranceId)){
                resultRenderer.stopRecordingContinue();
              }
              //updateText("");
            }

            @Override
            public void onError(String utteranceId) {
              // 발음 중 오류가 발생했을 때 호출되는 부분
            }
          });
        }
      }
    });

//    overlayImageView.setAlpha(0f);
//    stopCurrentPipeline();
//    setupStreamingModePipeline(InputSource.CAMERA);
  }
  public void onSaveButtonClick(View view) {
    boundingBoxView.saveBoundingBoxes(overlayImageParams);
  }

  public void onLoadButtonClick(View view) {
    boundingBoxView.loadBoundingBoxes();
  }
  public boolean checkDrawOverlayPermission() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
  }
  public static MainActivity getInstance() {
    return instance;
  }
  public void requestDrawOverlayPermission() {
    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
    startActivityForResult(intent, REQUEST_CODE);
  }
//  private void captureFramesFromCamera(int numberOfFrames, Consumer<ArrayList<Mat>> onCaptured) {
//    ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
//    ArrayList<Mat> framesList = new ArrayList<>();
//
//    cameraProviderFuture.addListener(() -> {
//      try {
//        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
//
//        // ImageCapture
//        ImageCapture imageCapture = new ImageCapture.Builder().build();
//
//        // CameraSelector
//        CameraSelector cameraSelector = new CameraSelector.Builder()
//                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
//                .build();
//
//        // Bind use cases to camera
//        cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);
//
//        for (int i = 0; i < numberOfFrames; i++) {
//          ImageCapture.OutputFileOptions outputFileOptions =
//                  new ImageCapture.OutputFileOptions.Builder(new File(getExternalFilesDir(null), "temp" + i + ".jpg")).build();
//
//          imageCapture.takePicture(
//                  outputFileOptions,
//                  ContextCompat.getMainExecutor(this),
//                  new ImageCapture.OnImageSavedCallback() {
//                    @Override
//                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
//                      Uri savedUri = Uri.fromFile(new File(getExternalFilesDir(null), outputFileResults.getSavedUri().getLastPathSegment()));
//                      try {
//                        InputStream imageStream = getContentResolver().openInputStream(savedUri);
//                        Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
//                        Mat mat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC3);
//                        Utils.bitmapToMat(bitmap, mat);
//                        framesList.add(mat);
//
//                        if (framesList.size() == numberOfFrames) {
//                          // Delete all temporary files
//                          for (int j = 0; j < numberOfFrames; j++) {
//                            File tempFile = new File(getExternalFilesDir(null), "temp" + j + ".jpg");
//                            if (tempFile.exists()) {
//                              tempFile.delete();
//                            }
//                          }
//
//                          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                            onCaptured.accept(framesList); // Call the callback with the ArrayList of Mat objects
//                          }
//                          cameraProvider.unbindAll(); // Unbind and close the camera after capturing
//                        }
//                      } catch (Exception e) {
//                        e.printStackTrace();
//                      }
//                    }
//
//                    @Override
//                    public void onError(@NonNull ImageCaptureException exception) {
//                      exception.printStackTrace();
//                      cameraProvider.unbindAll(); // Unbind and close the camera if an error occurs
//                    }
//                  }
//          );
//        }
//
//      } catch (ExecutionException | InterruptedException e) {
//        e.printStackTrace();
//      }
//    }, ContextCompat.getMainExecutor(this));
//  }
  private void toggleDrawUIMode(){
    resultRenderer.drawUIMode = !resultRenderer.drawUIMode;
    if(resultRenderer.drawUIMode){
      overlayImageView.setAlpha(0.6f);
    }
    else{
      overlayImageView.setAlpha(0f);
    }
  }
  private void initCommonResolution(){
    commonResolutions = new ArrayList<>();
    // 16:9 비율
    commonResolutions.add(new Pair<>(3840, 2160)); // UHD
    commonResolutions.add(new Pair<>(2560, 1440)); // QHD
    commonResolutions.add(new Pair<>(1920, 1080)); // Full HD
    commonResolutions.add(new Pair<>(1600, 900));  // HD+
    commonResolutions.add(new Pair<>(1280, 720));  // HD
    // 4:3 비율
    commonResolutions.add(new Pair<>(1600, 1200)); // UXGA
    commonResolutions.add(new Pair<>(1400, 1050)); // SXGA+
    commonResolutions.add(new Pair<>(1280, 960));  // SXGA-
    commonResolutions.add(new Pair<>(1152, 864));  // XGA+
    commonResolutions.add(new Pair<>(1024, 768));  // XGA
    // 기타 비율
    commonResolutions.add(new Pair<>(2048, 1536)); // QXGA, 4:3
    commonResolutions.add(new Pair<>(2560, 1600)); // WQXGA, 16:10
    commonResolutions.add(new Pair<>(1440, 900));  // WXGA+, 16:10
    commonResolutions.add(new Pair<>(1280, 800));  // WXGA, 16:10
    commonResolutions.add(new Pair<>(720, 480));   // 3:2
    // 필요한 경우 다른 해상도를 추가할 수 있습니다.
  }
  private void captureFrameFromCamera(Consumer<Mat> onCaptured) {
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

    cameraProviderFuture.addListener(() -> {
      try {
        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

        // ImageCapture
        ImageCapture imageCapture = new ImageCapture.Builder().build();

        // CameraSelector
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();
        // Bind use cases to camera
        try {
          Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);
        } catch (IllegalArgumentException e) {
          Toast.makeText(this, "카메라가 이미 사용 중입니다.", Toast.LENGTH_SHORT).show();
          return;
        }
        // Take picture and convert to Mat
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(new File(getExternalFilesDir(null), "temp.jpg")).build();

        imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                  @Override
                  public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    Uri savedUri = Uri.fromFile(new File(getExternalFilesDir(null), "temp.jpg"));
                    try {
                      InputStream imageStream = getContentResolver().openInputStream(savedUri);
                      Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
                      Mat mat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC3);
                      Utils.bitmapToMat(bitmap, mat);
                      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        onCaptured.accept(mat); // Call the callback with the Mat object
                      }
                    } catch (Exception e) {
                      e.printStackTrace();
                    }
                    cameraProvider.unbindAll(); // Unbind and close the camera after capturing
                  }

                  @Override
                  public void onError(@NonNull ImageCaptureException exception) {
                    exception.printStackTrace();
                    cameraProvider.unbindAll(); // Unbind and close the camera if an error occurs
                  }
                }
        );

      } catch (ExecutionException | InterruptedException e) {
        e.printStackTrace();
      }
    }, ContextCompat.getMainExecutor(this));
  }
  public void requestScreenCapture() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(serviceIntent);
    } else {
      startService(serviceIntent);
    }
    mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
  }
  @SuppressLint("WrongConstant")
  public Mat captureScreen() {
    int width;
    int height;
    int densityDpi = Resources.getSystem().getDisplayMetrics().densityDpi;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      // API 레벨 30 이상
      width = projectionMetrics.getBounds().width();
      height = projectionMetrics.getBounds().height();
    } else {
      // API 레벨 30 미만
      DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
      width = displayMetrics.widthPixels;
      height = displayMetrics.heightPixels;
    }
//    mImageReader = ImageReader.newInstance(projectionMetrics.getBounds().width(), projectionMetrics.getBounds().height(), PixelFormat.RGBA_8888, 2);
//    mMediaProjection.createVirtualDisplay("ScreenCapture",
//            projectionMetrics.getBounds().width(), projectionMetrics.getBounds().height(), Resources.getSystem().getDisplayMetrics().densityDpi,
//            VIRTUAL_DISPLAY_FLAGS,
//            mImageReader.getSurface(), null, null);
    mImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
    mMediaProjection.createVirtualDisplay("ScreenCapture",
            width, height, densityDpi,
            VIRTUAL_DISPLAY_FLAGS,
            mImageReader.getSurface(), null, null);


    long startTime = System.currentTimeMillis();
    Image image = null;
    while (System.currentTimeMillis() - startTime < 2000) { // 2초 동안 기다림
      image = mImageReader.acquireLatestImage();
      if (image != null) {
        break;
      }
      try {
        Thread.sleep(50); // 50ms마다 이미지가 사용 가능한지 확인
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    if (image != null) {
      final Image.Plane[] planes = image.getPlanes();
      final ByteBuffer buffer = planes[0].getBuffer();
      int pixelStride = planes[0].getPixelStride();
      int rowStride = planes[0].getRowStride();
      int rowPadding = rowStride - pixelStride * width;

      Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
      bitmap.copyPixelsFromBuffer(buffer);
      image.close();

      Mat mat = new Mat();
      Utils.bitmapToMat(bitmap, mat);
      //stopService(serviceIntent);
      return mat;
    }
    //stopService(serviceIntent);
    return null;
  }
  public String matToBase64Png(Mat mat) {
    // 1. Mat 객체를 Bitmap으로 변환
    Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
    Utils.matToBitmap(mat, bitmap);

    // 2. Bitmap을 PNG 형식의 바이트 배열로 변환
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
    byte[] byteArray = byteArrayOutputStream.toByteArray();

    // 3. 바이트 배열을 Base64 문자열로 인코딩
    return Base64.encodeToString(byteArray, Base64.DEFAULT);
  }
  public void calibrate() {
    //captureFrameFromCamera(cameraFrameOrigin -> {
    if (mMediaProjection == null) {
      requestScreenCapture();
      return;
    }
    Mat cameraFrameOrigin = captureScreen();
    cameraFrame = new Mat();
    //stopCurrentPipeline();
    // 가로와 세로 비율에 따른 회전 및 리사이즈
    if (cameraFrameOrigin.cols() > cameraFrameOrigin.rows()) {
      Core.rotate(cameraFrameOrigin, cameraFrameOrigin, Core.ROTATE_90_CLOCKWISE);
    }
    int originCols = cameraFrameOrigin.cols();
    int originRows = cameraFrameOrigin.rows();

    //cameraFrame = resizeWithAspectRatio(cameraFrameOrigin, calibrateWidth, calibrateHeight);
    cameraFrameOrigin.copyTo(cameraFrame);
    //Core.flip(cameraFrame, cameraFrame, 0);
    //runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Calibration size : " + calibrateWidth + ", " + calibrateHeight, Toast.LENGTH_SHORT).show());
    // 이미지를 HSV 색 공간으로 변환
    Mat hsvImage = new Mat();
    Imgproc.cvtColor(cameraFrame, hsvImage, Imgproc.COLOR_RGBA2RGB);
    Imgproc.cvtColor(hsvImage, hsvImage, Imgproc.COLOR_RGB2HSV);
    //Imgproc.cvtColor(cameraFrame, hsvImage, Imgproc.COLOR_RGB2Lab);

    // 빨간색 범위를 정의하고 해당 범위에 있는 픽셀을 이진화
    Scalar lowerRed = new Scalar(0, 100, 100);
    Scalar upperRed = new Scalar(10, 255, 255);
    Mat mask1 = new Mat();
    Core.inRange(hsvImage, lowerRed, upperRed, mask1);

    Scalar lowerRed2 = new Scalar(160, 100, 100);
    Scalar upperRed2 = new Scalar(180, 255, 255);
    Mat mask2 = new Mat();
    Core.inRange(hsvImage, lowerRed2, upperRed2, mask2);

    Mat redMask = new Mat();
    Core.addWeighted(mask1, 1.0, mask2, 1.0, 0.0, redMask);

//      Scalar lowerRed = new Scalar(20, 150, 150);
//      Scalar upperRed = new Scalar(190, 255, 255);
//      Mat redMask = new Mat();
//      Core.inRange(hsvImage, lowerRed, upperRed, redMask);

    // 노이즈 제거
    Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(15, 15));
    Imgproc.morphologyEx(redMask, redMask, Imgproc.MORPH_CLOSE, kernel);

    // Contours 찾기
    List<MatOfPoint> contours = new ArrayList<>();
    Imgproc.findContours(redMask, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
    ArrayList<org.opencv.core.Point> cameraPoints = new ArrayList<>();
    for (MatOfPoint contour : contours) {
      if (contour.total() >= 5) { // fitEllipse는 최소 5개의 포인트가 필요합니다.
        RotatedRect ellipse = Imgproc.fitEllipse(new MatOfPoint2f(contour.toArray()));

        // 타원의 면적
        double ellipseArea = Math.PI * (ellipse.size.width / 2) * (ellipse.size.height / 2);

        // 타원의 주축과 단축의 길이
        double majorAxis = Math.max(ellipse.size.width, ellipse.size.height);
        double minorAxis = Math.min(ellipse.size.width, ellipse.size.height);

        // 면적과 주축/단축의 길이를 기반으로 필터링
        if (ellipseArea > 200 && ellipseArea < 10000 && (majorAxis / minorAxis) < 2) {
          Imgproc.ellipse(cameraFrame, ellipse, new Scalar(0, 255, 0), 3);
          // 각 contour들의 중심점을 구해 ArrayList에 저장
          cameraPoints.add(ellipse.center);
        }
      }
    }

//    // 비트맵으로 변환
//    Bitmap resultBitmap = Bitmap.createBitmap(cameraFrame.cols(), cameraFrame.rows(), Bitmap.Config.ARGB_8888);
//    Utils.matToBitmap(cameraFrame, resultBitmap);
//    //Utils.matToBitmap(redMask, resultBitmap);
//
//      // 이미지 뷰 업데이트
//      runOnUiThread(new Runnable() {
//        @Override
//        public void run() {
//          final ImageView tempCameraView = findViewById(R.id.temp_camera_view);
//
//          // resultBitmap으로 설정
//          tempCameraView.setImageBitmap(resultBitmap);
//
//          // 5초 후에 빈 Bitmap으로 변경하는 작업 예약
//          new Handler().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//              // 빈 Bitmap 생성
//              Bitmap emptyBitmap = Bitmap.createBitmap(tempCameraView.getWidth(), tempCameraView.getHeight(), Bitmap.Config.ARGB_8888);
//              tempCameraView.setImageBitmap(emptyBitmap);
//            }
//          }, 5000);  // 5초
//        }
//      });
    Bitmap cameraFrameBitmap = Bitmap.createBitmap(cameraFrame.cols(), cameraFrame.rows(), Bitmap.Config.ARGB_8888);
    Utils.matToBitmap(cameraFrame, cameraFrameBitmap);
    saveImageToInternalStorage(cameraFrameBitmap, "cameraFrame.jpg");
    // cameraPoints는 4개 이상이어야 함
    if (cameraPoints.size() < 4) {
      runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Not enough points detected in camera frame!", Toast.LENGTH_SHORT).show());
      return;
    }

    // cameraFrame과 같은 크기의 흰색으로 꽉 찬 빈 이미지 생성
    Mat fullSizeOverlay = new Mat(cameraFrame.size(), CvType.CV_8UC4, new Scalar(255, 255, 255));

    // 실제 화면 크기와 fullSizeOverlay의 크기를 비교하여 비율을 계산
//      double ratio;
//      if (height < calibrateWidth) {
//        ratio = 1080.0 / (double)calibrateHeight;
//      } else {
//        ratio = 1080.0 / (double)calibrateWidth;
//      }
//      int adjustedX = (int) (overlayImageParams.x * ratio);
//      int adjustedY = (int) (overlayImageParams.y * ratio);
//      int adjustedWidth = (int) (overlayImageParams.width * ratio);
//      int adjustedHeight = (int) (overlayImageParams.height * ratio);
    int adjustedX = (int) (overlayImageParams.x);
    int adjustedY = (int) (overlayImageParams.y);
    int adjustedWidth = (int) (overlayImageParams.width);
    int adjustedHeight = (int) (overlayImageParams.height);

    // 조정된 위치와 크기에 따라 overlayImageView의 bitmap을 fullSizeOverlay에 붙여 넣기
    Bitmap overlayBitmap = ((BitmapDrawable) overlayImageView.getDrawable()).getBitmap();
    Mat overlayBitmapMat = new Mat();
    Utils.bitmapToMat(overlayBitmap, overlayBitmapMat);
    Imgproc.resize(overlayBitmapMat, overlayBitmapMat, new Size(adjustedWidth, adjustedHeight));

    // ROI 영역 검사
    if (adjustedX + overlayBitmapMat.cols() > fullSizeOverlay.cols() ||
            adjustedY + overlayBitmapMat.rows() > fullSizeOverlay.rows() ||
            adjustedX < 0 || adjustedY < 0) {

      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          Toast.makeText(getApplicationContext(), "Invalid ROI detected!", Toast.LENGTH_SHORT).show();
        }
      });
      overlayImageParams.copyFrom(lastValidParams);
      windowManager.updateViewLayout(overlayImageView, overlayImageParams);
      updateresultRendererImageParams();
      return;
    }
    else{
      lastValidParams.copyFrom(overlayImageParams);
    }

    overlayBitmapMat.copyTo(fullSizeOverlay.submat(new Rect(adjustedX, adjustedY, overlayBitmapMat.cols(), overlayBitmapMat.rows())));


    // 전체 이미지를 overlayImage라는 이름의 Mat으로 저장
    Mat overlayImage = fullSizeOverlay.clone();

    // overlayImage 전처리
    Mat hsvOverlay = new Mat();

    // 첫 번째 색 공간 변환: RGBA -> RGB
    Imgproc.cvtColor(overlayImage, overlayImage, Imgproc.COLOR_RGBA2RGB);

    // Median Blur 적용
    Imgproc.medianBlur(overlayImage, overlayImage, 3); // 5는 커널의 크기입니다. 필요에 따라 조정할 수 있습니다.

    // 두 번째 색 공간 변환: RGB -> HSV
    Imgproc.cvtColor(overlayImage, hsvOverlay, Imgproc.COLOR_RGB2HSV);

    // 빨간색 범위를 정의하고 해당 범위에 있는 픽셀을 이진화
    Mat mask1Overlay = new Mat();
    Core.inRange(hsvOverlay, lowerRed, upperRed, mask1Overlay);

    Mat mask2Overlay = new Mat();
    Core.inRange(hsvOverlay, lowerRed2, upperRed2, mask2Overlay);

    Mat redMaskOverlay = new Mat();
    Core.addWeighted(mask1Overlay, 1.0, mask2Overlay, 1.0, 0.0, redMaskOverlay);

    // Gaussian Blur 적용
    Imgproc.GaussianBlur(redMaskOverlay, redMaskOverlay, new Size(5, 5), 2, 2);

    // 노이즈 제거
    Mat kernel2 = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
    Imgproc.morphologyEx(redMaskOverlay, redMaskOverlay, Imgproc.MORPH_CLOSE, kernel2);


    // overlayImage에서 Contours 찾기
    List<MatOfPoint> overlayContours = new ArrayList<>();
    Imgproc.findContours(redMaskOverlay, overlayContours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
    ArrayList<org.opencv.core.Point> overlayPoints = new ArrayList<>();
    for (MatOfPoint contour : overlayContours) {
      if (contour.total() >= 5) {
        RotatedRect ellipse = Imgproc.fitEllipse(new MatOfPoint2f(contour.toArray()));
        Imgproc.ellipse(overlayImage, ellipse, new Scalar(0, 255, 0), 3);
        overlayPoints.add(ellipse.center);
      }
    }
    if (overlayPoints.size() != 4) {
      runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Overlay image should have exactly 4 points!", Toast.LENGTH_SHORT).show());
      return;
    }

    Bitmap overlayImageBitmap = Bitmap.createBitmap(overlayImage.cols(), overlayImage.rows(), Bitmap.Config.ARGB_8888);
    Utils.matToBitmap(overlayImage, overlayImageBitmap);
    saveImageToInternalStorage(overlayImageBitmap, "overlayImage.jpg");

    // overlayPoints 4개를 iterate하면서 가장 가까운 cameraPoints와 매칭
    List<Pair<org.opencv.core.Point, org.opencv.core.Point>> matchedOverlayPoints = findBestMatchingPairs(overlayPoints, cameraPoints);

    // matchedOverlayPoints에서 overlayPoints와 cameraPoints 추출
    for (Pair<org.opencv.core.Point, org.opencv.core.Point> pair : matchedOverlayPoints) {
      overlayPointsList.add(new org.opencv.core.Point(pair.first.x, pair.first.y));
      cameraPointsList.add(new org.opencv.core.Point(pair.second.x, pair.second.y));
    }
    overlayPointsList = new ArrayList<>();
    cameraPointsList = new ArrayList<>();
    for (Pair<org.opencv.core.Point, org.opencv.core.Point> pair : matchedOverlayPoints) {
      overlayPointsList.add(pair.first);
      cameraPointsList.add(pair.second);
    }
    WindowManager.LayoutParams originalParams = new WindowManager.LayoutParams();
    originalParams.copyFrom(overlayImageParams);

    adjustOverlayImageParams();
    overlayImageParams.y -= statusbarHeight;
    resizeTouchRegions(originalParams, overlayImageParams);
    windowManager.updateViewLayout(overlayImageView, overlayImageParams);
    updateresultRendererImageParams();
    //});
  }
  public Mat resizeWithAspectRatio(Mat src, int targetWidth, int targetHeight) {
    double srcAspectRatio = (double) src.cols() / src.rows();
    double targetAspectRatio = (double) targetWidth / targetHeight;

    int newWidth, newHeight;

    // Decide whether to fit width or height based on target aspect ratio
    if (targetAspectRatio > srcAspectRatio) {
      // Fit to width and crop excess height
      newWidth = targetWidth;
      newHeight = (int) (targetWidth / srcAspectRatio);
    } else {
      // Fit to height and crop excess width
      newHeight = targetHeight;
      newWidth = (int) (targetHeight * srcAspectRatio);
    }

    Mat resizedImage = new Mat();
    Imgproc.resize(src, resizedImage, new Size(newWidth, newHeight));

    // Calculate the starting points for cropping
    int startX = Math.max(0, (newWidth - targetWidth) / 2);
    int startY = Math.max(0, (newHeight - targetHeight) / 2);

    // Ensure the ROI dimensions are within the resized image boundaries
    int roiWidth = Math.min(targetWidth, resizedImage.cols() - startX);
    int roiHeight = Math.min(targetHeight, resizedImage.rows() - startY);

    Rect roi = new Rect(startX, startY, roiWidth, roiHeight);
    return new Mat(resizedImage, roi);
  }

  public ArrayList<org.opencv.core.Point> transformPoints(
          WindowManager.LayoutParams prevParams,
          WindowManager.LayoutParams newParams,
          ArrayList<org.opencv.core.Point> prevPoints) {

    ArrayList<org.opencv.core.Point> newPoints = new ArrayList<>();

    for (org.opencv.core.Point prevPoint : prevPoints) {
      double relativeX = (prevPoint.x - prevParams.x) / prevParams.width;
      double relativeY = (prevPoint.y - prevParams.y) / prevParams.height;

      double newPointX = relativeX * newParams.width + newParams.x;
      double newPointY = relativeY * newParams.height + newParams.y;

      newPoints.add(new org.opencv.core.Point(newPointX, newPointY));
    }

    return newPoints;
  }
  public void resizeTouchRegions(WindowManager.LayoutParams prevParams, WindowManager.LayoutParams newParams){
    if (cameraStarted) {
      targetRegions = resultRenderer.touchRegions;
    } else {
      targetRegions = boundingBoxView.getBoundingBoxes();
    }
//    ArrayList<LabeledRectF> newRegions = transformLabeledRectFs(prevParams, newParams, targetRegions);
//    targetRegions.clear();
//    for (LabeledRectF rect : newRegions) {
//      targetRegions.add(new LabeledRectF(rect.left, rect.top, rect.right, rect.bottom, rect.getLabel()));
//    }
    diffs = new ArrayList<>();
    prevX = prevParams.x;
    prevY = prevParams.y;
    prevWidth = prevParams.width;
    prevHeight = prevParams.height;

    for (LabeledRectF region : targetRegions) {
      float x_diff = region.left - prevX / (float)width;
      float y_diff = region.top - prevY / (float)height;
      diffs.add(new PointF(x_diff, y_diff));
    }

    float widthRatio = overlayImageParams.width / (float)prevWidth;
    float heightRatio = overlayImageParams.height / (float)prevHeight;

    for (int i = 0; i < targetRegions.size(); i++) {
      LabeledRectF region = targetRegions.get(i);
      PointF diff = diffs.get(i);

      float newLeft = overlayImageParams.x / (float)width + diff.x * widthRatio;
      float newTop = overlayImageParams.y / (float)height + diff.y * heightRatio;
      float newRight = newLeft + region.width() * widthRatio;
      float newBottom = newTop + region.height() * heightRatio;

      region.set(newLeft, newTop, newRight, newBottom);
    }
    if(!cameraStarted){
      boundingBoxView.setBoundingBoxes(targetRegions);
      boundingBoxView.updateParams(overlayImageParams);
      boundingBoxView.updateBoundingBox();
    }
  }
  public void adjustOverlayImageParams() {
    final int ACTION_MOVE_AMOUNT = 10; // 한 번의 Action으로 변경되는 좌표값
    double minTotalDistance = calculateTotalDistance(overlayPointsList, cameraPointsList);
    int actionCount = 0;
    while (true) {
      actionCount++;
      boolean isImproved = false;
      WindowManager.LayoutParams bestParams = new WindowManager.LayoutParams();
      boolean bestParamsScale = false;
      // 가능한 모든 Action을 시도
      for (int action = 0; action < 8; action++) {
        WindowManager.LayoutParams newParams = new WindowManager.LayoutParams();
        newParams.copyFrom(overlayImageParams);
        switch (action) {
          case 0: newParams.x -= ACTION_MOVE_AMOUNT; break; // 왼쪽으로 이동
          case 1: newParams.x += ACTION_MOVE_AMOUNT; break; // 오른쪽으로 이동
          case 2: newParams.y -= ACTION_MOVE_AMOUNT; break; // 위로 이동
          case 3: newParams.y += ACTION_MOVE_AMOUNT; break; // 아래로 이동
          case 4: newParams.width -= ACTION_MOVE_AMOUNT; break; // 넓이 줄이기
          case 5: newParams.width += ACTION_MOVE_AMOUNT; break; // 넓이 늘리기
          case 6: newParams.height -= ACTION_MOVE_AMOUNT; break; // 높이 줄이기
          case 7: newParams.height += ACTION_MOVE_AMOUNT; break; // 높이 늘리기
        }
        //ArrayList<org.opencv.core.Point> tempOverlayPointsList = updateTempOverlayPoints(currentActionScaling, overlayPointsList, prevX, prevY, prevWidth, prevHeight, newParams);
        ArrayList<org.opencv.core.Point> tempOverlayPointsList = transformPoints(overlayImageParams, newParams, overlayPointsList);
        double newTotalDistance = calculateTotalDistance(tempOverlayPointsList, cameraPointsList);
        if (newTotalDistance < minTotalDistance) {
          minTotalDistance = newTotalDistance;
          bestParams.copyFrom(newParams);
//          overlayImageParams.copyFrom(newParams);
//          overlayPointsList = transformPoints(overlayImageParams, newParams, overlayPointsList);
//          System.out.println(newParams.x + ", " + newParams.y + ", " + newParams.width + ", " + newParams.height);
//          windowManager.updateViewLayout(overlayImageView, overlayImageParams);
          isImproved = true;
        }
      }

      // 어떠한 Action도 거리를 줄이지 못할 경우 종료
      if (!isImproved) {
        break;
      }
      else{
        overlayPointsList = transformPoints(overlayImageParams, bestParams, overlayPointsList);
        overlayImageParams.copyFrom(bestParams);
        //System.out.println(bestParams.x + ", " + bestParams.y + ", " + bestParams.width + ", " + bestParams.height);
        windowManager.updateViewLayout(overlayImageView, overlayImageParams);

//        Mat copyOfCameraFrame = cameraFrame.clone();
//        Bitmap overlayBitmap = ((BitmapDrawable) overlayImageView.getDrawable()).getBitmap();
//        Mat overlayBitmapMat = new Mat(CvType.CV_8UC4);
//        Utils.bitmapToMat(overlayBitmap, overlayBitmapMat);
//        Imgproc.resize(overlayBitmapMat, overlayBitmapMat, new Size(overlayImageParams.width, overlayImageParams.height));
//        overlayBitmapMat.copyTo(copyOfCameraFrame.submat(new Rect(overlayImageParams.x, overlayImageParams.y, overlayBitmapMat.cols(), overlayBitmapMat.rows())));
//        Core.rotate(copyOfCameraFrame, copyOfCameraFrame, Core.ROTATE_90_COUNTERCLOCKWISE);
//
//        Bitmap bitmap = Bitmap.createBitmap(copyOfCameraFrame.cols(), copyOfCameraFrame.rows(), Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(copyOfCameraFrame, bitmap);
//        saveImageToInternalStorage(bitmap, "Debug_" + actionCount + ".jpg");
      }
    }
  }
  public void saveImageToInternalStorage(Bitmap bitmapImage, String filename){
    ContextWrapper cw = new ContextWrapper(getApplicationContext());
    File directory = cw.getDir("imageDir", Context.MODE_PRIVATE);
    File mypath = new File(directory, filename);

    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(mypath);
      bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, fos);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        fos.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private double calculateTotalDistance(List<org.opencv.core.Point> overlayPointsList, List<org.opencv.core.Point> cameraPointsList) {
    double totalDistance = 0.0;

    for (int i = 0; i < overlayPointsList.size(); i++) {
      org.opencv.core.Point overlayPoint = overlayPointsList.get(i);
      org.opencv.core.Point cameraPoint = cameraPointsList.get(i);

      totalDistance += Math.sqrt(Math.pow(overlayPoint.x - cameraPoint.x, 2) + Math.pow(overlayPoint.y - cameraPoint.y, 2));
      System.out.println(overlayPoint.x + ", " + overlayPoint.y + " -- " + cameraPoint.x + ", " + cameraPoint.y);
    }

    return totalDistance;
  }
  List<Pair<org.opencv.core.Point, org.opencv.core.Point>> findBestMatchingPairs(List<org.opencv.core.Point> overlayPoints, List<org.opencv.core.Point> cameraPoints) {
    List<Pair<org.opencv.core.Point, org.opencv.core.Point>> bestMatchedPairs = new ArrayList<>();
    double minTotalDistance = Double.MAX_VALUE;

    // cameraPoints에서 4개의 점을 선택하는 모든 조합을 생성합니다.
    List<List<org.opencv.core.Point>> allCombinations = generateCombinations(cameraPoints, 4);

    for (List<org.opencv.core.Point> combination : allCombinations) {
      double currentTotalDistance = 0;
      List<Pair<org.opencv.core.Point, org.opencv.core.Point>> currentPairs = new ArrayList<>();

      for (int i = 0; i < overlayPoints.size(); i++) {
        org.opencv.core.Point overlayPoint = overlayPoints.get(i);
        org.opencv.core.Point cameraPoint = combination.get(i);

        double distance = Math.sqrt(Math.pow(overlayPoint.x - cameraPoint.x, 2) + Math.pow(overlayPoint.y - cameraPoint.y, 2));
        currentTotalDistance += distance;
        currentPairs.add(new Pair<>(overlayPoint, cameraPoint));
      }

      if (currentTotalDistance < minTotalDistance) {
        minTotalDistance = currentTotalDistance;
        bestMatchedPairs = currentPairs;
      }
    }
    return bestMatchedPairs;
  }
  List<List<org.opencv.core.Point>> generateCombinations(List<org.opencv.core.Point> cameraPoints, int k) {
    List<List<org.opencv.core.Point>> combinations = new ArrayList<>();
    generateCombinationsHelper(cameraPoints, k, 0, new ArrayList<>(), combinations);
    return combinations;
  }
  void generateCombinationsHelper(List<org.opencv.core.Point> cameraPoints, int k, int start, List<org.opencv.core.Point> current, List<List<org.opencv.core.Point>> combinations) {
    if (k == 0) {
      combinations.add(new ArrayList<>(current));
      return;
    }

    for (int i = start; i <= cameraPoints.size() - k; i++) {
      current.add(cameraPoints.get(i));
      generateCombinationsHelper(cameraPoints, k - 1, i + 1, current, combinations);
      current.remove(current.size() - 1);
    }
  }
  private org.opencv.core.Point calculateCenter(List<org.opencv.core.Point> points) {
    double sumX = 0;
    double sumY = 0;
    for (org.opencv.core.Point point : points) {
      sumX += point.x;
      sumY += point.y;
    }
    return new org.opencv.core.Point(sumX / points.size(), sumY / points.size());
  }

  private double calculateAverageDistance(List<org.opencv.core.Point> points, org.opencv.core.Point center) {
    double sumDistance = 0;
    for (org.opencv.core.Point point : points) {
      double distance = Math.sqrt(Math.pow(point.x - center.x, 2) + Math.pow(point.y - center.y, 2));
      sumDistance += distance;
    }
    return sumDistance / points.size();
  }
  private double calculateAverageDistanceX(List<org.opencv.core.Point> points, org.opencv.core.Point center) {
    double totalDistanceX = 0;
    for (org.opencv.core.Point point : points) {
      totalDistanceX += Math.abs(point.x - center.x);
    }
    return totalDistanceX / points.size();
  }
  private double calculateAverageDistanceY(List<org.opencv.core.Point> points, org.opencv.core.Point center) {
    double totalDistanceY = 0;
    for (org.opencv.core.Point point : points) {
      totalDistanceY += Math.abs(point.y - center.y);
    }
    return totalDistanceY / points.size();
  }
  private void showExitDialog() {
    new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle("앱 종료")
            .setMessage("앱을 종료하시겠습니까?")
            .setPositiveButton("예", (dialog, which) -> {
              finishAffinity();
              System.exit(0);
            })
            .setNegativeButton("아니오", null)
            .show();
  }
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode) {
      case PERMISSIONS_REQUEST_CAMERA_AUDIO: {
        if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
        } else {

        }
        return;
      }
    }
  }
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
    super.onActivityResult(requestCode, resultCode, resultData);
    boundingBoxView.handleActivityResult(requestCode, resultCode, resultData);
    if (requestCode == REQUEST_MEDIA_PROJECTION) {
      if (resultCode != Activity.RESULT_OK) {
        return;
      }
      mMediaProjection = mProjectionManager.getMediaProjection(resultCode, resultData);
    }
  }
  public void onSensorChanged(SensorEvent event) {
    if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
      SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
      SensorManager.getOrientation(rotationMatrix, orientationAngles);
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int i) {

  }
  private void showOverlayImage() {
    // Create an ImageView instance
    overlayImageView = new ImageView(this);
    overlayImageView.setImageResource(R.drawable.dinosaur);
    overlayImageView.setAlpha(0.6f);
    //overlayImageView.setScaleX(1.2f);
    //overlayImageView.setScaleY(1.3f);
    overlayImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
    overlayImageView.setRotation(90);

    //    // Set up layout parameters for the ImageView
    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                    : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, // Add this flag
            PixelFormat.TRANSLUCENT);
    // Get the WindowManager service
    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

    params.y = 100;
    // Add the ImageView to the WindowManager.
    windowManager.addView(overlayImageView, params);
  }
  public void showOverlayProgressBar() {
    overlayProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
    overlayProgressBar.setIndeterminate(true);
    //overlayProgressBar.setScaleX(2.0f);
    //overlayProgressBar.setScaleY(2.0f);

    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                    : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, // Add this flag
            PixelFormat.TRANSLUCENT);

    params.gravity = Gravity.CENTER; // To display ProgressBar in the center
    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    windowManager.addView(overlayProgressBar, params);
  }
  public void hideOverlayProgressBar() {
    if (overlayProgressBar != null) {
      windowManager.removeView(overlayProgressBar);
      overlayProgressBar = null;
    }
  }
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (editMode == 0) {
      if (cameraStarted) {
        gestureDetector.onTouchEvent(event);
      }
      int x = (int) event.getRawX();
      int y = (int) event.getRawY() - statusbarHeight - layoutHeight;

      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          int topLeftDist = calculateDistance(x, y, overlayImageParams.x, overlayImageParams.y);
          int topRightDist = calculateDistance(x, y, overlayImageParams.x + overlayImageParams.width, overlayImageParams.y);
          int bottomLeftDist = calculateDistance(x, y, overlayImageParams.x, overlayImageParams.y + overlayImageParams.height);
          int bottomRightDist = calculateDistance(x, y, overlayImageParams.x + overlayImageParams.width, overlayImageParams.y + overlayImageParams.height);

          int minDist = Math.min(Math.min(topLeftDist, topRightDist), Math.min(bottomLeftDist, bottomRightDist));

          if (minDist <= touchThreshold) {
            if (minDist == topLeftDist) {
              selectedCorner = 0;
            } else if (minDist == topRightDist) {
              selectedCorner = 1;
            } else if (minDist == bottomLeftDist) {
              selectedCorner = 2;
            } else {
              selectedCorner = 3;
            }
          } else {
            selectedCorner = -1;
          }
          prevX = overlayImageParams.x;
          prevY = overlayImageParams.y;
          prevWidth = overlayImageParams.width;
          prevHeight = overlayImageParams.height;
          diffs = new ArrayList<>();
          if (cameraStarted) {
            targetRegions = resultRenderer.touchRegions;
          } else {
            targetRegions = boundingBoxView.getBoundingBoxes();
          }

          for (LabeledRectF region : targetRegions) {
            float x_diff = region.left - prevX / (float)width;
            float y_diff = region.top - prevY / (float)height;
            diffs.add(new PointF(x_diff, y_diff));
          }
          boundingBoxView.updateParams(overlayImageParams);
          break;
        case MotionEvent.ACTION_MOVE:
          if (selectedCorner != -1) {
            //prevWidth = overlayImageParams.width;
            //prevHeight = overlayImageParams.height;

            if (selectedCorner == 0) {
              overlayImageParams.width += overlayImageParams.x - x;
              overlayImageParams.height += overlayImageParams.y - y;
              overlayImageParams.x = x;
              overlayImageParams.y = y;
            } else if (selectedCorner == 1) {
              overlayImageParams.width = x - overlayImageParams.x;
              overlayImageParams.height += overlayImageParams.y - y;
              overlayImageParams.y = y;
            } else if (selectedCorner == 2) {
              overlayImageParams.width += overlayImageParams.x - x;
              overlayImageParams.height = y - overlayImageParams.y;
              overlayImageParams.x = x;
            } else if (selectedCorner == 3) {
              overlayImageParams.width = x - overlayImageParams.x;
              overlayImageParams.height = y - overlayImageParams.y;
            }
            boundingBoxView.updateParams(overlayImageParams);
            boundingBoxView.updateBoundingBox();
            windowManager.updateViewLayout(overlayImageView, overlayImageParams);
          }
          if(cameraStarted){
            updateresultRendererImageParams();
          }
          break;

        case MotionEvent.ACTION_UP:
          selectedCorner = -1;
          //resizeTouchRegions();
          float widthRatio = overlayImageParams.width / (float)prevWidth;
          float heightRatio = overlayImageParams.height / (float)prevHeight;

          for (int i = 0; i < targetRegions.size(); i++) {
            LabeledRectF region = targetRegions.get(i);
            PointF diff = diffs.get(i);

            float newLeft = overlayImageParams.x / (float)width + diff.x * widthRatio;
            float newTop = overlayImageParams.y / (float)height + diff.y * heightRatio;
            float newRight = newLeft + region.width() * widthRatio;
            float newBottom = newTop + region.height() * heightRatio;

            region.set(newLeft, newTop, newRight, newBottom);
          }

          if (!cameraStarted) {
            boundingBoxView.setBoundingBoxes(targetRegions);
            boundingBoxView.updateParams(overlayImageParams);
            boundingBoxView.updateBoundingBox();
          }
          break;
      }
    }
    else if(editMode == 1) {
      float normalizedX = event.getRawX();
      float normalizedY = event.getRawY() - statusbarHeight - layoutHeight;
      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          startTouchPoint = new PointF(normalizedX, normalizedY);
          break;
        case MotionEvent.ACTION_MOVE:
          if (startTouchPoint != null) {
            boundingBoxView.updateCurrentBoundingBox(startTouchPoint, new PointF(normalizedX, normalizedY));
          }
          break;
        case MotionEvent.ACTION_UP:
          if (startTouchPoint != null) {
            boundingBoxView.finalizeCurrentBoundingBox();
          }
          break;
      }
    }
    if (editMode == 2) {
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        float x = event.getRawX();
        float y = event.getRawY() - statusbarHeight - layoutHeight;

        ArrayList<LabeledRectF> boundingBoxes = boundingBoxView.getRawBoundingBoxes();

        for (int i = 0; i < boundingBoxes.size(); i++) {
          LabeledRectF boundingBox = boundingBoxes.get(i);
          if (boundingBox.contains(x, y)) {
            // 해당 bounding box 안에 터치가 발생한 경우 텍스트 입력창 띄우기
            overlayImageView.setVisibility(View.GONE);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(i + " 번째 Bounding Box Label Text 설정");
            builder.setMessage("이 Bounding Box가 활성화됐을때 TTS로 읽을 메시지를 입력하세요.");

            // Set up the input
            final EditText input = new EditText(this);
            input.setText(boundingBox.getLabel()); // Default value is the current label
            builder.setView(input);

            // Set up the buttons
            builder.setPositiveButton("저장하기", new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                boundingBox.setLabel(input.getText().toString()); // Update the label
                boundingBoxView.invalidate(); // Invalidate the view to refresh the screen
              }
            });
            builder.setNegativeButton("취소하기", null);

            currentDialog = builder.create();
            currentDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
              @Override
              public void onDismiss(DialogInterface dialogInterface) {
                overlayImageView.setVisibility(View.VISIBLE);
                currentDialog = null;
              }
            });
            currentDialog.show();
            break;
          }
        }
      }
    }

    return true;
  }
  public void setChatGPTAPIKey(){
    overlayImageView.setVisibility(View.GONE);
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("API키 입력");
    builder.setMessage("ChatGPT 사용을 위한 API 키를 입력하세요.");

    final EditText input = new EditText(this);
    input.setText(chatGPTAPIKey);
    builder.setView(input);
    builder.setPositiveButton("저장하기", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        chatGPTAPIKey = input.getText().toString().trim();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("chatgptapikey", chatGPTAPIKey);
        editor.apply();
      }
    });
    builder.setNegativeButton("취소하기", null);

    currentDialog = builder.create();
    currentDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
      @Override
      public void onDismiss(DialogInterface dialogInterface) {
        overlayImageView.setVisibility(View.VISIBLE);
        currentDialog = null;
      }
    });
    currentDialog.show();
  }
  public void setAzureGPTAPIKey(){
    overlayImageView.setVisibility(View.GONE);
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("API키 입력");
    builder.setMessage("Azure Speech 사용을 위한 API 키를 입력하세요.");

    final EditText input = new EditText(this);
    input.setText(azureAPIKey);
    builder.setView(input);
    builder.setPositiveButton("저장하기", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        azureAPIKey = input.getText().toString().trim();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("azureapikey", azureAPIKey);
        editor.apply();
      }
    });
    builder.setNegativeButton("취소하기", null);

    currentDialog = builder.create();
    currentDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
      @Override
      public void onDismiss(DialogInterface dialogInterface) {
        overlayImageView.setVisibility(View.VISIBLE);
        currentDialog = null;
      }
    });
    currentDialog.show();
  }
  private Pair<Integer, Integer> getClosestCommonResolution(int width, int height) {
    if(width < height){
      int temp = width;
      width = height;
      height = temp;
    }
    double minDifference = Double.MAX_VALUE;
    Pair<Integer, Integer> closestResolution = null;

    for (Pair<Integer, Integer> resolution : commonResolutions) {
      double difference = Math.sqrt(Math.pow(width - resolution.first, 2) + Math.pow(height - resolution.second, 2));

      if (difference < minDifference) {
        minDifference = difference;
        closestResolution = resolution;
      }
    }

    return closestResolution;
  }
  public int getStatusBarHeight() {
    int result = 0;
    int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
    if (resourceId > 0) {
      result = getResources().getDimensionPixelSize(resourceId);
    }
    return result;
  }
  public int getLayoutHeight(String layoutName) {
    int resId = getResources().getIdentifier(layoutName, "id", getPackageName());
    View layoutView = findViewById(resId);
    if(layoutView != null) {
      return layoutView.getHeight();
    }
    return 0;
  }
  private int calculateDistance(int x1, int y1, int x2, int y2) {
    return (int) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
  }
  private void showOverlayImage(float x1, float y1, float x2, float y2, float x3, float y3) {
    overlayImageView = new ImageView(this);
    //overlayImageView.setImageResource(R.drawable.dinosaur);
    overlayImageView.setImageResource(R.drawable.dinosaur3);
    overlayImageView.setAlpha(0.6f);
    overlayImageView.setScaleType(ImageView.ScaleType.FIT_XY);

//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
//      overlayImageView.setBackground(border);
//    } else {
//      overlayImageView.setBackgroundDrawable(border);
//    }

    WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    Display display = windowManager.getDefaultDisplay();
    Point size = new Point();
    display.getSize(size);

    int imageWidth = (int) ((x2 - x1) * size.x);
    int imageHeight = (int) ((y2 - y1) * size.y);

    int imagePositionX = (int) (x3 * size.x);
    int imagePositionY = (int) (y3 * size.y);

    overlayImageParams = new WindowManager.LayoutParams(
            imageWidth,
            imageHeight,
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                    : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT);

    overlayImageParams.x = imagePositionX;
    overlayImageParams.y = imagePositionY;
    overlayImageParams.gravity = Gravity.TOP | Gravity.LEFT;
    overlayImageView.setBackground(border);
    windowManager.addView(overlayImageView, overlayImageParams);

    lastValidParams = new WindowManager.LayoutParams();
    lastValidParams.copyFrom(overlayImageParams);
  }
  public void updateOverlayImagePosition() {
    windowManager.updateViewLayout(overlayImageView, overlayImageParams);
  }
  private void showOverlayText() {
    overlayTextView = new TextView(this);
    overlayTextView.setTextColor(Color.GREEN);
    overlayTextView.setAlpha(0.7f);
    overlayTextView.setGravity(Gravity.CENTER);
    overlayTextView.setRotation(90);
    overlayTextView.setText("");

    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                    : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT);

    params.gravity = Gravity.CENTER;
    // Get the WindowManager service
    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

    windowManager.addView(overlayTextView, params);
  }


  @Override
  protected void onDestroy() {
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
    if (overlayImageView != null) {
      windowManager.removeView(overlayImageView);
      overlayImageView = null;
    }
    //prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
    SharedPreferences.Editor editor = prefs.edit();
    editor.putInt("age", age);
    editor.putString("gender", gender);
    editor.apply();
    stopService(serviceIntent);
    resultRenderer.soundPool.release();
    super.onDestroy();
  }
  @Override
  protected void onResume() {
    super.onResume();
    //sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_NORMAL);
    if (inputSource == InputSource.CAMERA) {
      // Restarts the camera and the opengl surface rendering.
      cameraInput = new CameraInput(this);
      cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
      glSurfaceView.post(this::startCamera);
      glSurfaceView.setVisibility(View.VISIBLE);
    } else if (inputSource == InputSource.VIDEO) {
      videoInput.resume();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    sensorManager.unregisterListener(this);
    if (inputSource == InputSource.CAMERA) {
      glSurfaceView.setVisibility(View.GONE);
      cameraInput.close();
    } else if (inputSource == InputSource.VIDEO) {
      videoInput.pause();
    }
    //prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
    SharedPreferences.Editor editor = prefs.edit();
    editor.putInt("age", age);
    editor.putString("gender", gender);
    editor.apply();
  }

  private Bitmap downscaleBitmap(Bitmap originalBitmap) {
    double aspectRatio = (double) originalBitmap.getWidth() / originalBitmap.getHeight();
    int width = imageView.getWidth();
    int height = imageView.getHeight();
    if (((double) imageView.getWidth() / imageView.getHeight()) > aspectRatio) {
      width = (int) (height * aspectRatio);
    } else {
      height = (int) (width / aspectRatio);
    }
    return Bitmap.createScaledBitmap(originalBitmap, width, height, false);
  }

  private Bitmap rotateBitmap(Bitmap inputBitmap, InputStream imageData) throws IOException {
    int orientation =
            new ExifInterface(imageData)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
    if (orientation == ExifInterface.ORIENTATION_NORMAL) {
      return inputBitmap;
    }
    Matrix matrix = new Matrix();
    switch (orientation) {
      case ExifInterface.ORIENTATION_ROTATE_90:
        matrix.postRotate(90);
        break;
      case ExifInterface.ORIENTATION_ROTATE_180:
        matrix.postRotate(180);
        break;
      case ExifInterface.ORIENTATION_ROTATE_270:
        matrix.postRotate(270);
        break;
      default:
        matrix.postRotate(0);
    }
    return Bitmap.createBitmap(
            inputBitmap, 0, 0, inputBitmap.getWidth(), inputBitmap.getHeight(), matrix, true);
  }

  /** Sets up the UI components for the static image demo. */
  private void setupStaticImageDemoUiComponents() {
    // The Intent to access gallery and read images as bitmap.
    imageGetter =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                      Intent resultIntent = result.getData();
                      if (resultIntent != null) {
                        if (result.getResultCode() == RESULT_OK) {
                          Bitmap bitmap = null;
                          try {
                            bitmap =
                                    downscaleBitmap(
                                            MediaStore.Images.Media.getBitmap(
                                                    this.getContentResolver(), resultIntent.getData()));
                          } catch (IOException e) {
                            Log.e(TAG, "Bitmap reading error:" + e);
                          }
                          try {
                            InputStream imageData =
                                    this.getContentResolver().openInputStream(resultIntent.getData());
                            bitmap = rotateBitmap(bitmap, imageData);
                          } catch (IOException e) {
                            Log.e(TAG, "Bitmap rotation error:" + e);
                          }
                          if (bitmap != null) {
                            hands.send(bitmap);
                          }
                        }
                      }
                    });
    /*Button loadImageButton = findViewById(R.id.button_load_picture);
    loadImageButton.setOnClickListener(
        v -> {
          if (inputSource != InputSource.IMAGE) {
            stopCurrentPipeline();
            setupStaticImageModePipeline();
          }
          // Reads images from gallery.
          Intent pickImageIntent = new Intent(Intent.ACTION_PICK);
          pickImageIntent.setDataAndType(MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*");
          imageGetter.launch(pickImageIntent);
        });
    imageView = new HandsResultImageView(this);*/
    imageView = new HandsResultImageView(this);
  }

  /** Sets up core workflow for static image mode. */
  private void setupStaticImageModePipeline() {
    this.inputSource = InputSource.IMAGE;
    // Initializes a new MediaPipe Hands solution instance in the static image mode.
    hands =
            new Hands(
                    this,
                    HandsOptions.builder()
                            .setStaticImageMode(true)
                            .setMaxNumHands(2)
                            .setRunOnGpu(RUN_ON_GPU)
                            .build());

    // Connects MediaPipe Hands solution to the user-defined HandsResultImageView.
    hands.setResultListener(
            handsResult -> {
              logWristLandmark(handsResult, /*showPixelValues=*/ true);
              imageView.setHandsResult(handsResult);
              runOnUiThread(() -> imageView.update());
            });
    hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

    // Updates the preview layout.
    FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
    frameLayout.removeAllViewsInLayout();
    imageView.setImageDrawable(null);
    frameLayout.addView(imageView);
    imageView.setVisibility(View.VISIBLE);
  }
  public void speakText(String textToSpeak) {
    tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null);
  }
  /** Sets up the UI components for the video demo. */
  private void setupVideoDemoUiComponents() {
    // The Intent to access gallery and read a video file.
    videoGetter =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                      Intent resultIntent = result.getData();
                      if (resultIntent != null) {
                        if (result.getResultCode() == RESULT_OK) {
                          glSurfaceView.post(
                                  () ->
                                          videoInput.start(
                                                  this,
                                                  resultIntent.getData(),
                                                  hands.getGlContext(),
                                                  glSurfaceView.getWidth(),
                                                  glSurfaceView.getHeight()));
                        }
                      }
                    });
//    Button loadVideoButton = findViewById(R.id.button_load_video);
//    loadVideoButton.setOnClickListener(
//            v -> {
//              stopCurrentPipeline();
//              setupStreamingModePipeline(InputSource.VIDEO);
//              // Reads video from gallery.
//              Intent pickVideoIntent = new Intent(Intent.ACTION_PICK);
//              pickVideoIntent.setDataAndType(MediaStore.Video.Media.INTERNAL_CONTENT_URI, "video/*");
//              videoGetter.launch(pickVideoIntent);
//            });
  }
//  public void updateText(String text, boolean delete) {
//    runOnUiThread(new Runnable() {
//      @Override
//      public void run() {
//        overlayTextView.setText(text);
//        adjustFontSize(text.length());
//        if(delete) {
//          handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//              overlayTextView.setText("");
//            }
//          }, 8000);
//        }
//      }
//    });
//  }
  public void updateText(String text) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        handler.removeCallbacks(clearTextRunnable);
        overlayTextView.setText(text);
        adjustFontSize(text.length());
        handler.postDelayed(clearTextRunnable, 10000);
      }
    });
  }
//  public void updateText(String text) {
//    overlayTextView.setText(text);
//    adjustFontSize(text.length());
//  }
  private void adjustFontSize(int length) {
    float textSize;
    if (length > 200) {
      textSize = 18; // text size for very long text
    } else if (length > 100) {
      textSize = 24; // text size for long text
    } else if (length > 50) {
      textSize = 30; // text size for medium text
    } else if (length > 20) {
      textSize = 36; // text size for short text
    } else {
      textSize = 42; // text size for very short text
    }
    overlayTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
  }
  /** Sets up the UI components for the live demo with camera input. */
  private void setupLiveDemoUiComponents() {
    Button startCameraButton = findViewById(R.id.button_start_camera);
    startCameraButton.setOnClickListener(
            v -> {
              if (inputSource == InputSource.CAMERA) {
                return;
              }
              stopCurrentPipeline();
              setupStreamingModePipeline(InputSource.CAMERA);
              //goFullscreen();
            });
  }
  public void goFullscreen() {
    // Find the layout
    View buttonsLayout = findViewById(R.id.buttons);
    View buttons2Layout = findViewById(R.id.buttons2);
    View statusbarLayout = findViewById(R.id.status_bar);

    // Hide the layout
    buttonsLayout.setVisibility(View.GONE);
    buttons2Layout.setVisibility(View.GONE);
    statusbarLayout.setVisibility(View.GONE);

    // Set the app to fullscreen
    View decorView = getWindow().getDecorView();
    decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
  }

  /** Sets up core workflow for streaming mode. */
  private void setupStreamingModePipeline(InputSource inputSource) {
    this.inputSource = inputSource;
    // Initializes a new MediaPipe Hands solution instance in the streaming mode.
    hands =
            new Hands(
                    this,
                    HandsOptions.builder()
                            .setStaticImageMode(false)
                            .setMaxNumHands(4)
                            .setRunOnGpu(RUN_ON_GPU)
                            .setMinDetectionConfidence(0.66f)
                            .setMinTrackingConfidence(0.66f)
                            .setModelComplexity(0)
                            .build());
    hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

    if (inputSource == InputSource.CAMERA) {
      cameraInput = new CameraInput(this);
      cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
    } else if (inputSource == InputSource.VIDEO) {
      videoInput = new VideoInput(this);
      videoInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
    }

    // Initializes a new Gl surface view with a user-defined HandsResultGlRenderer.
    glSurfaceView =
            new SolutionGlSurfaceView<>(this, hands.getGlContext(), hands.getGlMajorVersion());
    resultRenderer = new HandsResultGlRenderer();
    resultRenderer.tts = tts;
    resultRenderer.oneHandGestureFrameCount = 0;
    resultRenderer.twoHandGestureFrameCount = 0;
    resultRenderer.specialGestureFrameCount = 0;
    resultRenderer.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    resultRenderer.fileName = getExternalCacheDir().getAbsolutePath();
    resultRenderer.fileName += "/audiorecordtest.m4a";
    resultRenderer.speakMode = false;
    resultRenderer.uploadMode = false;
    resultRenderer.mediaPlayer = MediaPlayer.create(this, R.raw.loop_music);
    resultRenderer.mediaPlayer.setLooping(true);
    resultRenderer.soundPool = new SoundPool.Builder().setMaxStreams(1).build();
    resultRenderer.dingSoundId = resultRenderer.soundPool.load(this, R.raw.ding, 1);
    resultRenderer.ding2SoundId = resultRenderer.soundPool.load(this, R.raw.ding2, 1);
    resultRenderer.musicMode = false;
    //resultRenderer.touchRegions = new ArrayList<>();
    resultRenderer.touchRegions = boundingBoxView.getBoundingBoxes();
    //resultRenderer.touchThreshold = (float)(touchThreshold / width);
    resultRenderer.touchThreshold = 0.06f;
    resultRenderer.screenWidth = (float)height;
    resultRenderer.screenHeight = (float)width;
    resultRenderer.isSoundDetected = false;
    resultRenderer.drawUIMode = true;
    resultRenderer.recognizer = new AzureSpeechHelper(azureAPIKey, "koreacentral");
    resultRenderer.streamer = new AudioStreamer(resultRenderer.recognizer);
    resultRenderer.streamer.initializeAudioRecord();
    resultRenderer.executor = Executors.newSingleThreadExecutor();
    updateresultRendererImageParams();
    glSurfaceView.setSolutionResultRenderer(resultRenderer);
    glSurfaceView.setRenderInputImage(true);
    hands.setResultListener(
            handsResult -> {
              logWristLandmark(handsResult, /*showPixelValues=*/ false);
              glSurfaceView.setRenderData(handsResult);
              glSurfaceView.requestRender();
            });

    // The runnable to start camera after the gl surface view is attached.
    // For video input source, videoInput.start() will be called when the video uri is available.
    if (inputSource == InputSource.CAMERA) {
      glSurfaceView.post(this::startCamera);
    }

    // Updates the preview layout.
    FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
    imageView.setVisibility(View.GONE);
    frameLayout.removeAllViewsInLayout();
    frameLayout.addView(glSurfaceView);
    glSurfaceView.setVisibility(View.VISIBLE);
    frameLayout.requestLayout();
  }

  private void startCamera() {
//    cameraInput.start(
//            this,
//            hands.getGlContext(),
//            CameraInput.CameraFacing.FRONT,
//            glSurfaceView.getWidth(),
//            glSurfaceView.getHeight());
//    cameraFacingFront = true;
    cameraInput.start(
            this,
            hands.getGlContext(),
            CameraInput.CameraFacing.FRONT,
            height,
            width);
    cameraFacingFront = true;
    //overlayImageParams.y -= (statusbarHeight + layoutHeight);
    cameraStarted = true;
    goFullscreen();
    editMode = 0;
    overlayImageView.setBackground(null);
    updateOverlayImagePosition();
    updateresultRendererImageParams();
  }
//  private void getSingleFrame() {
//    cameraInput.setNewFrameListener(textureFrame -> {
//      cameraFrame = textureFrameToMat(textureFrame);
//      cameraInput.setNewFrameListener(textureFrame2 -> hands.send(textureFrame2));
//    });
//  }
//  private Bitmap textureFrameToBitmap(TextureFrame textureFrame) {
//    ByteBuffer buffer = ByteBuffer.allocateDirect(textureFrame.getWidth() * textureFrame.getHeight() * 4);
//    GLES20.glReadPixels(0, 0, textureFrame.getWidth(), textureFrame.getHeight(), GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
//    Bitmap bitmap = Bitmap.createBitmap(textureFrame.getWidth(), textureFrame.getHeight(), Bitmap.Config.ARGB_8888);
//    bitmap.copyPixelsFromBuffer(buffer);
//    return bitmap;
//  }
public String copyRawResourceToFile(int resourceId) {
    try {
      File tempFile = File.createTempFile("temp_audio", ".m4a", getCacheDir());
      tempFile.deleteOnExit();
      try (InputStream in = getResources().openRawResource(resourceId);
           OutputStream out = new FileOutputStream(tempFile)) {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
        }
      }
      return tempFile.getAbsolutePath();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }
  private void updateresultRendererImageParams(){
    resultRenderer.xParams = (float)(overlayImageParams.x / (float)width);
    resultRenderer.yParams = (float)(overlayImageParams.y / (float)height);
    resultRenderer.widthParams = (float)(overlayImageParams.width / (float)width);
    resultRenderer.heightParams = (float)(overlayImageParams.height / (float)height);
  }
  private void switchMode() {
//    cameraInput.start(
//            this,
//            hands.getGlContext(),
//            cameraFacingFront ? CameraInput.CameraFacing.BACK : CameraInput.CameraFacing.FRONT,
//            height,
//            width);
//    cameraFacingFront = !cameraFacingFront;
    editMode = (editMode + 1) % 3;

    if (editMode == 0) {
      // Apply the border when the edit mode is 0
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        overlayImageView.setBackground(border);
      } else {
        overlayImageView.setBackgroundDrawable(border);
      }
      boundingBoxView.drawCircleMode = true;
      boundingBoxView.updateBoundingBox();

    } else {
      // Remove the border otherwise
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        overlayImageView.setBackground(null);
      } else {
        overlayImageView.setBackgroundDrawable(null);
      }
      boundingBoxView.drawCircleMode = false;
      boundingBoxView.updateBoundingBox();
    }

    // Invalidate to immediately reflect the change on screen
    overlayImageView.invalidate();

    Toast.makeText(this, modes[editMode], Toast.LENGTH_SHORT).show();
  }
  public void playDing2() {
    resultRenderer.playDing2();
  }
  private void stopCurrentPipeline() {
    if (cameraInput != null) {
      cameraInput.setNewFrameListener(null);
      cameraInput.close();
    }
    if (videoInput != null) {
      videoInput.setNewFrameListener(null);
      videoInput.close();
    }
    if (glSurfaceView != null) {
      glSurfaceView.setVisibility(View.GONE);
    }
    if (hands != null) {
      hands.close();
    }
  }

  private void logWristLandmark(HandsResult result, boolean showPixelValues) {
    if (result.multiHandLandmarks().isEmpty()) {
      return;
    }
    NormalizedLandmark wristLandmark =
            result.multiHandLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
    // For Bitmaps, show the pixel values. For texture inputs, show the normalized coordinates.
    if (showPixelValues) {
      int width = result.inputBitmap().getWidth();
      int height = result.inputBitmap().getHeight();
      Log.i(
              TAG,
              String.format(
                      "MediaPipe Hand wrist coordinates (pixel values): x=%f, y=%f",
                      wristLandmark.getX() * width, wristLandmark.getY() * height));
    } else {
      Log.i(
              TAG,
              String.format(
                      "MediaPipe Hand wrist normalized coordinates (value range: [0, 1]): x=%f, y=%f",
                      wristLandmark.getX(), wristLandmark.getY()));
    }
    if (result.multiHandWorldLandmarks().isEmpty()) {
      return;
    }
    Landmark wristWorldLandmark =
            result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
    Log.i(
            TAG,
            String.format(
                    "MediaPipe Hand wrist world coordinates (in meters with the origin at the hand's"
                            + " approximate geometric center): x=%f m, y=%f m, z=%f m",
                    wristWorldLandmark.getX(), wristWorldLandmark.getY(), wristWorldLandmark.getZ()));
  }
}
