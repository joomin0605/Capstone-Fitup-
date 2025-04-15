package com.cookandroid.myapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.Image.Plane;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CreateAccount extends AppCompatActivity {

    private EditText idEditText, passwordEditText, heightEditText, weightEditText;
    private RadioGroup genderGroup;
    private RadioButton maleRB, femaleRB;
    private Button signupButton;
    private PreviewView previewView;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private PoseLandmarker poseLandmarker;
    private ExecutorService cameraExecutor;

    private float latestShoulderWidth = 0f;
    private float latestHipWidth = 0f;
    private float upperBodyLength = 0f;
    private float lowerBodyLength = 0f;

    @Override
    @androidx.camera.core.ExperimentalGetImage
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_account);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        idEditText = findViewById(R.id.idEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        heightEditText = findViewById(R.id.heightEditText);
        weightEditText = findViewById(R.id.weightEditText);
        genderGroup = findViewById(R.id.genderGroup);
        maleRB = findViewById(R.id.maleRB);
        femaleRB = findViewById(R.id.femaleRB);
        signupButton = findViewById(R.id.singupButton);
        previewView = findViewById(R.id.previewView);

        cameraExecutor = Executors.newSingleThreadExecutor();

        setupCamera();

        signupButton.setOnClickListener(v -> handleSignup());
    }

    @androidx.camera.core.ExperimentalGetImage
    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void setupCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().build();

                PoseLandmarkerOptions options = PoseLandmarkerOptions.builder()
                        .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setResultListener(this::processPoseResult)
                        .build();

                poseLandmarker = PoseLandmarker.createFromOptions(this, options);

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    Image mediaImage = imageProxy.getImage();
                    if (mediaImage != null) {
                        MPImage mpImage = createMpImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                        poseLandmarker.detectAsync(mpImage, System.currentTimeMillis());
                    }
                    imageProxy.close();
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processPoseResult(PoseLandmarkerResult result, MPImage image) {
        if (result.landmarks().isEmpty()) return;

        var landmarks = result.landmarks().get(0);

        float shoulder = distance(landmarks.get(11).x(), landmarks.get(11).y(), landmarks.get(12).x(), landmarks.get(12).y());
        float hip = distance(landmarks.get(23).x(), landmarks.get(23).y(), landmarks.get(24).x(), landmarks.get(24).y());

        float midShoulderX = (landmarks.get(11).x() + landmarks.get(12).x()) / 2;
        float midShoulderY = (landmarks.get(11).y() + landmarks.get(12).y()) / 2;
        float midHipX = (landmarks.get(23).x() + landmarks.get(24).x()) / 2;
        float midHipY = (landmarks.get(23).y() + landmarks.get(24).y()) / 2;

        float upper = distance(midShoulderX, midShoulderY, midHipX, midHipY);
        float lower = distance(landmarks.get(24).x(), landmarks.get(24).y(), landmarks.get(32).x(), landmarks.get(32).y());

        latestShoulderWidth = shoulder;
        latestHipWidth = hip;
        upperBodyLength = upper;
        lowerBodyLength = lower;
    }

    private float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    private MPImage createMpImage(Image image, int rotationDegrees) {
        Bitmap bitmap = yuv420ToBitmap(image);
        Bitmap rotatedBitmap = rotateBitmap(bitmap, rotationDegrees);
        return new BitmapImageBuilder(rotatedBitmap).build();
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        if (degrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private Bitmap yuv420ToBitmap(Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Image format is not YUV_420_888");
        }

        Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] jpegBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }

    private void handleSignup() {
        String email = idEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String gender = maleRB.isChecked() ? "male" : "female";

        float ratio = latestShoulderWidth / latestHipWidth;
        float ecto, meso, endo;

        if (gender.equals("male")) {
            ecto = 1.0f - ratio;
            meso = Math.abs(1.0f - ratio);
            endo = latestHipWidth > latestShoulderWidth ? 1.0f : 0.0f;
        } else {
            float femaleRatio = latestHipWidth / latestShoulderWidth;
            ecto = femaleRatio < 0.9f ? 1.0f : 0.0f;
            meso = Math.abs(1.0f - femaleRatio);
            endo = femaleRatio > 1.1f ? 1.0f : 0.0f;
        }

        Map<String, Float> scores = new HashMap<>();
        scores.put("Ectomorph", ecto);
        scores.put("Mesomorph", meso);
        scores.put("Endomorph", endo);

        List<Map.Entry<String, Float>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

        String primary = sorted.get(0).getKey();
        String secondary = sorted.get(1).getKey();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("email", email);
                            userData.put("gender", gender);
                            userData.put("height", heightEditText.getText().toString());
                            userData.put("weight", weightEditText.getText().toString());
                            userData.put("primaryType", primary);
                            userData.put("secondaryType", secondary);
                            userData.put("shoulderWidth", latestShoulderWidth);
                            userData.put("hipWidth", latestHipWidth);
                            userData.put("upperBodyLength", upperBodyLength);
                            userData.put("lowerBodyLength", lowerBodyLength);
                            userData.put("timestamp", System.currentTimeMillis());

                            db.collection("users").document(user.getUid()).set(userData)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this, "회원가입 완료!", Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(this, MainActivity.class));
                                        finish();
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(this, "저장 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        Toast.makeText(this, "회원가입 실패: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (poseLandmarker != null) poseLandmarker.close();
        cameraExecutor.shutdown();
    }
}