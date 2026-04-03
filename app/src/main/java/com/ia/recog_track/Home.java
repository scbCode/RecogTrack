package com.ia.recog_track;

import static androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST;
import static androidx.camera.core.ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY;
import static androidx.camera.core.impl.utils.TransformUtils.getRectToRect;
import static androidx.camera.core.impl.utils.TransformUtils.rotateRect;
import static androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED;

import static com.google.android.gms.common.util.CollectionUtils.listOf;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ExposureState;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.impl.CaptureConfig;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.mlkit.vision.MlKitAnalyzer;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.interfaces.Detector;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.ia.recog_track.databinding.ActivityHomeBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import jp.co.cyberagent.android.gpuimage.GPUImage;
import jp.co.cyberagent.android.gpuimage.filter.GPUImage3x3ConvolutionFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLightenBlendFilter;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
@ExperimentalGetImage public class Home extends AppCompatActivity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]
            {"android.permission.CAMERA"};

    PreviewView mPreviewView;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler(Looper.myLooper());
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar
            if (Build.VERSION.SDK_INT >= 30) {
                mContentView.getWindowInsetsController().hide(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            } else {
                // Note that some of these constants are new as of API 16 (Jelly Bean)
                // and API 19 (KitKat). It is safe to use them, as they are inlined
                // at compile-time and do nothing on earlier devices.
                mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (AUTO_HIDE) {
                        delayedHide(AUTO_HIDE_DELAY_MILLIS);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    break;
                default:
                    break;
            }
            return false;
        }
    };
    private ActivityHomeBinding binding;

    ObjectDetectorOptions options;
    ObjectDetector objectDetector;
    private ImageLabeler imageLabeler;
    TextRecognizer recognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mPreviewView = findViewById(R.id.previewView);
        LocalModel localModel =
                new LocalModel.Builder()
                        .setAssetFilePath("8.tflite")//8 bus
                        .build();

        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        height = displayMetrics.heightPixels;

        width = displayMetrics.widthPixels;

        CustomObjectDetectorOptions customObjectDetectorOptions =
                new CustomObjectDetectorOptions.Builder(localModel)
                        .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
//                        .enableClassification()
//                        .enableMultipleObjects()
                        .build();

        recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        CustomImageLabelerOptions customImageLabelerOptions =
                new CustomImageLabelerOptions.Builder(localModel)
//                        .setConfidenceThreshold(0.135f)
                        .setConfidenceThreshold(0.135f)
                        .build();
        imageLabeler = ImageLabeling.getClient(customImageLabelerOptions);

        objectDetector = ObjectDetection.getClient(customObjectDetectorOptions);
        if(allPermissionsGranted()){
            startCamera(); //start camera if permission has been granted by user
        } else{
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

    }

    public void process(InputImage image){
        objectDetector.process(image);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private void show() {
        // Show the system bar
        if (Build.VERSION.SDK_INT >= 30) {
            mContentView.getWindowInsetsController().show(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else {
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
//        mHideHandler.removeCallbacks(mHideRunnable);
//        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    private boolean allPermissionsGranted(){

        for(String permission : REQUIRED_PERMISSIONS){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    private Executor executor;
    private void startCamera() {

        executor=ContextCompat.getMainExecutor(this);

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {

                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindPreview(cameraProvider);

                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, executor);
    }

    YourAnalyzer yourAnalyzer = new YourAnalyzer();
    ImageAnalysis imageAnalysis;

    @SuppressLint("UnsafeOptInUsageError")
    Camera2Interop.Extender buildImageAnalysis( ImageAnalysis.Builder builder)  {
        @ExperimentalCamera2Interop
        Camera2Interop.Extender camera2InterOp = new Camera2Interop.Extender(builder);
        camera2InterOp.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
        camera2InterOp.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
        camera2InterOp.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, 2);
        return camera2InterOp;
    }
    @SuppressLint("RestrictedApi")
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

        imageAnalysis = new ImageAnalysis.Builder().build();

        LifecycleCameraController lifecycleCameraController = new LifecycleCameraController(getApplicationContext());
        Range<Integer> fpsRange = Range.create(240, 240);
        lifecycleCameraController.setVideoCaptureTargetFrameRate(fpsRange);
        List<Detector<?>> detectors = new ArrayList<>();
        detectors.add(objectDetector);

        MlKitAnalyzer mlKitAnalyzer =  new MlKitAnalyzer(detectors, COORDINATE_SYSTEM_VIEW_REFERENCED,
                executor, result -> {

            for (DetectedObject detectedObject : result.getValue(objectDetector)) {

                ImageView imgv = (ImageView) findViewById(R.id.imageView);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        DisplayMetrics displayMetrics = new DisplayMetrics();
                        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                        int height = displayMetrics.heightPixels;
                        int width = displayMetrics.widthPixels;
                        Bitmap tempBitmap = Bitmap.createBitmap(width,
                                height, Bitmap.Config.ARGB_8888);
                        Canvas tempCanvas = new Canvas(tempBitmap);
                        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(Color.GREEN);
                        paint.setStrokeWidth(5);
                        Rect rect = detectedObject.getBoundingBox();
                        float leftx = rect.left;
                        float topy = rect.top;
                        float rightx = rect.right;
                        float bottomy = rect.bottom;
                        tempCanvas.drawRect(leftx, topy, rightx, bottomy, paint);

                        Log.i("getlabel","getlabel "+ detectedObject.getLabels());
                        paint.setTextSize(30);
                        tempCanvas.drawText("x",  leftx, bottomy+50, paint);
                        imgv.setImageBitmap(tempBitmap);


                    }
                });

            }
        });

        lifecycleCameraController.bindToLifecycle(this);

        lifecycleCameraController.setImageAnalysisAnalyzer(executor, yourAnalyzer);
        mPreviewView.setController(lifecycleCameraController);

//        Calendar currentTime = Calendar.getInstance();
//        if (currentTime.get(Calendar.HOUR) >18){
//        Handler handler = new Handler();
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                ExposureState exposureState = lifecycleCameraController.getCameraInfo().getExposureState();
//                Range<Integer> range=   Range.create(mPreviewView.getController().getVideoCaptureTargetFrameRate().getUpper(),
//                        mPreviewView.getController().getVideoCaptureTargetFrameRate().getUpper());
//                mPreviewView.getController().setVideoCaptureTargetFrameRate( range);
//                MediaStoreOutputOptions mediaStoreOutputOptions = new MediaStoreOutputOptions
//                        .Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
//                        .setDurationLimitMillis(5000)
//                        .build();
//
//                if (exposureState.isExposureCompensationSupported()) {
////                    Log.i("getExposureCompensationRDange", "getExposureCompensationRange " + range);
////                    lifecycleCameraController.getCameraControl().setExposureCompensationIndex(range.getUpper());
////                    lifecycleCameraController.getCameraControl().cancelFocusAndMetering();\
//
//                }
//            }
//        },3000);}
    }
    DisplayMetrics displayMetrics = new DisplayMetrics();
    int height = 0;
    int width = 0;
    boolean check=false;
    public void mlkitapi(InputImage inputImage,ImageProxy imageProxy){


        Log.i("analizer","analizer ");

        analizer(inputImage,imageProxy);
    }

   public void analizer(InputImage inputImage,ImageProxy imageProxy){
       int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
       Image image = imageProxy.getImage();
       Task<?> mlKitTask = null;
       try {
           if (mlKitTask == null) {
               mlKitTask =
                       objectDetector.process(image, rotationDegrees, analysisToTarget);

               mlKitTask.addOnCompleteListener(
                       executor,
                       task -> {
                           // Record the return value / exception.
                           if (task.isCanceled()) {
                           } else if (task.isSuccessful()) {

                               if (task.getResult() != null) {
                                   List<DetectedObject> l = ((List<DetectedObject>) task.getResult());

                                   DisplayMetrics displayMetrics = new DisplayMetrics();
                                   getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                                   int height = displayMetrics.heightPixels;
                                   int width = displayMetrics.widthPixels;
                                   Bitmap tempBitmap = Bitmap.createBitmap(width,
                                           height, Bitmap.Config.ARGB_8888);
                                   Canvas tempCanvas = new Canvas(tempBitmap);
                                   Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                   paint.setStyle(Paint.Style.STROKE);
                                   paint.setColor(Color.GREEN);
                                   paint.setStrokeWidth(5);

                                   for (DetectedObject detectedObject : l) {
                                       ImageView imgv = (ImageView) findViewById(R.id.imageView);

//                                    ///CLASSIFICACAO IMAGEM
                                       runOnUiThread(new Runnable() {
                                           @Override
                                           public void run() {
                                               Rect rect = detectedObject.getBoundingBox();
                                               float leftx = rect.left;
                                               float topy = rect.top;
                                               float rightx = rect.right;
                                               float bottomy = rect.bottom;
                                               tempCanvas.drawRect(leftx, topy, rightx, bottomy, paint);
                                               imgv.setImageBitmap(tempBitmap);
                                           }
                                       });
                                       InputImage inputImageClassification = InputImage.fromBitmap(changeBitmapContrastBrightness(imageProxy.toBitmap(),2,4),rotationDegrees);
                                       label(detectedObject.getBoundingBox(), image.getCropRect(), inputImageClassification);
                                   }
                               } else {
                                   ImageView imgv = (ImageView) findViewById(R.id.imageView);
                                   DisplayMetrics displayMetrics = new DisplayMetrics();
                                   getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                                   int height = displayMetrics.heightPixels;
                                   int width = displayMetrics.widthPixels;
                                   Bitmap tempBitmap = Bitmap.createBitmap(width,
                                           height, Bitmap.Config.ARGB_8888);
                                   imgv.setImageBitmap(tempBitmap);

                               }
                           } else {
                               Log.i("onSuccess", "onSuccess x");
                           }
                           imageProxy.close();
                       });
           }
       }catch (Exception e){

       }
    }

    FirebaseFirestore db = FirebaseFirestore.getInstance();

    String oldLine = "";
    public void saveText(String line){
        Map<String, Object> map = new HashMap<>();
        String numero = line.split(":")[0];
        String nome = line.split(":")[1];
        map.put("numero", numero);
        map.put("nome", nome);
        db.collection("linhas")
                .document(numero)
                .set(map).addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {

                        Map<String, Object> obj = new HashMap<>();
                        Map<String, Object> position = new HashMap<>();
                        position.put("time", new Timestamp(new Date()));
                        if (!oldLine.equals(line))
                            db.collection("linhas")
                                    .document(numero).collection("point")
                                    .document("1").collection("register").add(position).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                        @Override
                                        public void onSuccess(DocumentReference documentReference) {
                                            Log.i("SETDATA", "onSuccess writing document");
                                            oldLine = line;
                                        }
                                    }).addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Log.w("SETDATA", "Error writing document", e);
                                        }
                                    });
                        Log.i("SETDATA", "onSuccess writing document");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("SETDATA", "Error writing document", e);
                    }
                });


    }
    Task<?> taskTextRec = null;

   public void textRecognizerByImage(Bitmap inputImage, int rd){
       Executor executorText = ContextCompat.getMainExecutor(this);
       TextView imgv = (TextView) findViewById(R.id.textView);

       try {

       taskTextRec = recognizer.process(InputImage.fromBitmap(inputImage,rd));

       taskTextRec.addOnCompleteListener(
               executorText, task -> {
                   if (task.isSuccessful()){
                       if (task.getResult() != null) {
                           Text text = (Text) task.getResult();
                           Log.i("textRecognizerByImage", "textRecognizerByImage " + text.getText());
                           if (!text.getText().isEmpty()) {
                               String linha = getLinha(text.getText());
                               if (!linha.isEmpty()) {
                                   saveText(linha);
                                   runOnUiThread(new Runnable() {
                                       @Override
                                       public void run() {
                                           imgv.setText(linha);
                                       }
                                   });
                                     saveImageNoClassification(processImg(inputImage));
                               }
                           }
                       }
                   }
               });

       }catch (Exception e){
           Log.i("textRecognizerByImage", "textRecognizerByImage catch" + e.getMessage());

       }

    }

    public void saveImageNoClassification(Bitmap croppedImage){
        try {
            String path = getApplication().getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath();
            OutputStream fOut = null;
            long time= System.currentTimeMillis();
            File directory = new File(path+"/bus-no-classificarion");
            if (!directory.exists())
                directory.mkdirs();
            File file = new File(path+"/bus-no-classificarion", time+"recog-imagem.jpg"); // the File to save , append increasing numeric counter to prevent files from getting overwritten.
            Log.i("getAbsolutePath", "getAbsolutePath " + file.getAbsolutePath());
            file.createNewFile();  ///
            fOut = new FileOutputStream(file);

            Bitmap pictureBitmap = croppedImage; // obtaining the Bitmap
            pictureBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut); // saving the Bitmap to a file compressed as a JPEG with 85% compression rate
            fOut.flush(); // Not really required
            fOut.close(); // do not forget to close the stream

            MediaStore.Images.Media.insertImage(getContentResolver(),file.getAbsolutePath(),file.getName(),file.getName());
        }catch (Exception e){
            Log.i("Exception", "Exception savefile " + e.getMessage());

        }
    }

    public void label(Rect rect,Rect crop, InputImage inputImage){
        Executor executorLabel = ContextCompat.getMainExecutor(this);
        int rotationDegrees = inputImage.getRotationDegrees();
        try {

            Task<List<ImageLabel>> task = imageLabeler.process(inputImage);
            ImageView imgv = (ImageView) findViewById(R.id.imageViewLabel);
            imgv.setDrawingCacheEnabled(true);
            task.addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
                @Override
                public void onSuccess(List<ImageLabel> imageLabels) {
                    if (imageLabels!=null){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                DisplayMetrics displayMetrics = new DisplayMetrics();
                                getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                                int height = displayMetrics.heightPixels;
                                int width = displayMetrics.widthPixels;
                                Bitmap tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                                Canvas tempCanvas = new Canvas(tempBitmap);
                                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                paint.setStyle(Paint.Style.STROKE);
                                paint.setColor(Color.GREEN);
                                paint.setStrokeWidth(5);
                                float leftx = rect.left;
                                float topy = rect.top;
                                float rightx = rect.right;
                                float bottomy = rect.bottom;
//                                tempCanvas.drawRect(leftx, topy, rightx, bottomy, paint);
                                paint.setTextSize(70);
                                if (imageLabels.size()>0) {
                                    for (int i = 0; i < imageLabels.size(); i++) {
                                    tempCanvas.drawText(imageLabels.get(0).getText() + " "
                                            + imageLabels.get(0).getConfidence(), leftx, bottomy + 50, paint);
                                    String label = imageLabels.get(0).getText();
                                    imgv.setImageBitmap(tempBitmap);
                                    if (label.toLowerCase().contains("bus") || label.toLowerCase().contains("streetcar")){

                                        textRecognizerByImage(inputImage.getBitmapInternal(),rotationDegrees);

                                    }
                                    Log.i("getlabel", "getlabel " + label);
                                    }

                                }
                            }
                        });
                    }

                }
            });


        }catch (Exception e){
            Log.i("Exception", "Exception e "+e.getMessage());

        }
    }
    Matrix analysisToTarget = new Matrix();
    ImageProxy imageP;
    private class YourAnalyzer implements ImageAnalysis.Analyzer {

        @SuppressLint("RestrictedApi")
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {

                analysisToTarget =mPreviewView.getSensorToViewTransform();
                Bitmap mediaImage = imageProxy.toBitmap();

                if (mediaImage != null) {

                        Matrix sensorToTarget = mPreviewView.getSensorToViewTransform();
                    Matrix sensorToAnalysis =
                            new Matrix(imageProxy.getImageInfo().getSensorToBufferTransformMatrix());
                    // Calculate the rotation added by ML Kit.
                    RectF sourceRect = new RectF(0, 0, imageProxy.getWidth(),
                            imageProxy.getHeight());
                    RectF bufferRect = rotateRect(sourceRect,
                            imageProxy.getImageInfo().getRotationDegrees());
                    Matrix analysisToMlKitRotation = getRectToRect(sourceRect, bufferRect,
                            imageProxy.getImageInfo().getRotationDegrees());
                    // Concat the MLKit transformation with sensor to Analysis.
                    sensorToAnalysis.postConcat(analysisToMlKitRotation);
                    // Invert to get analysis to sensor.
                    sensorToAnalysis.invert(analysisToTarget);
                    // Concat sensor to target to get analysisToTarget.
                    analysisToTarget.postConcat(sensorToTarget);

                    InputImage image =
                            InputImage.fromBitmap(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                    mlkitapi(image,imageProxy);

                    Log.i("onSuccess", "onSuccess sensorToTarget");
                }
        }
    }

    public String getLinha(String linha){
        Linhas linhas = new Linhas();
        String[] map = linhas.getMap();
        int count = 0;
        for (int i = 0; i <  map.length; i++) {
            String numLinha = map[i].split(" : ")[0];
            String nomeLinha = map[i].split(" : ")[1];
            String number = linha.replaceAll("[^0-9]","");
            String onlyletter = linha.replaceAll(("[a-zA-Z]+"),"");
            Log.i("getLinha","getLinha ##############################################");
            Log.i("getLinha","getLinha "+ linha);
            Log.i("getLinha","getLinha number "+ number);
            Log.i("getLinha","getLinha onlyletter "+ onlyletter);
            Log.i("getLinha","getLinha ##############################################");
            if (onlyletter.length()>3) {
                if (onlyletter.contains(nomeLinha)) {
                    if (stripAccents(linha).toLowerCase().equals(stripAccents(nomeLinha).toLowerCase())) {
                        count = 3;
                        return map[i];
                    }
                }
            }else
            if (onlyletter.length()==3) {
                if (number.contains(numLinha)) {
                        count = 3;
                        return map[i];
                }
            }else
            if(number.equals(numLinha)){
                return map[i];
            }else
            if(number.length()>3){
                String n = number.substring(0,3);
                if (numLinha.contains(n))
                    return map[i];
            }else
            if(linha.length()<=3)
                if(number.equals(numLinha)){
                    count=1;
                    return map[i];
                }
        }
        return "";
    }

    public static String stripAccents(String s)
    {
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        return s;
    }

    public static Bitmap changeBitmapContrastBrightness(Bitmap bmp, float contrast, float brightness)
    {
        ColorMatrix cm = new ColorMatrix(new float[]
                {
                        contrast, 0, 0, 0, brightness,
                        0, contrast, 0, 0, brightness,
                        0, 0, contrast, 0, brightness,
                        0, 0, 0, 1, 0
                });

        Bitmap ret = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());

        Canvas canvas = new Canvas(ret);

        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(bmp, 0, 0, paint);

        return ret;
    }

    public Bitmap processImg(Bitmap image){
        GPUImageFilter filter = new GPUImageFilter();
        GPUImage mGPUImage = new GPUImage(this);
        mGPUImage.setImage(image);
        mGPUImage.setFilter(new GPUImageLightenBlendFilter());
        return mGPUImage.getBitmapWithFilterApplied();
    }

}

class DrawView extends androidx.appcompat.widget.AppCompatImageView {

    Rect rect;
    public DrawView(Context context) {
        super(context);

    }
    DrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    DrawView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint paint = new Paint();
        paint.setColor(Color.TRANSPARENT);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        float leftx = rect.left;
        float topy = rect.top;
        float rightx = rect.right;
        float bottomy = rect.bottom;
        canvas.drawRect(leftx, topy, rightx, bottomy, paint);
    }
}


abstract class ImgCallBack {
     public void onSuccess(){}
    public void onError(){}
}

