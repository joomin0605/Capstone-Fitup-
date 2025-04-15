package com.cookandroid.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.processing.SurfaceProcessorNode;
import androidx.camera.view.PreviewView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.mlkit.vision.common.internal.MultiFlavorDetectorCreator;

public class MyPageActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private Button homeButton, categoryButton, myFitButton, myPageButton;
    private EditText pwEdt, idEdt;
    private Button gosignupButton, loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mypage_activity);

        mAuth = FirebaseAuth.getInstance();

        idEdt = findViewById(R.id.idEdt);
        pwEdt = findViewById(R.id.pwEdt);
        homeButton = findViewById(R.id.homeButton);
        categoryButton = findViewById(R.id.categoryButton);
        myFitButton = findViewById(R.id.myFitButton);
        myPageButton = findViewById(R.id.myPageButton);
        gosignupButton = findViewById(R.id.gosingupButton);
        loginButton = findViewById(R.id.loginButton);

        loginButton.setOnClickListener(v -> loginUser());
        gosignupButton.setOnClickListener(v -> {
            startActivity(new Intent(MyPageActivity.this, CreateAccount.class));
        });

        myPageButton.setOnClickListener(null);

        categoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MyPageActivity.this, CategoryActivity.class);
                startActivity(intent);
            }
        });

        myFitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MyPageActivity.this, MyFitActivity.class);
                startActivity(intent);
            }
        });

        homeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MyPageActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });
    }
    private void loginUser() {
        String email = idEdt.getText().toString().trim();
        String password = pwEdt.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "이메일과 비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                   if (task.isSuccessful()) {
                       Toast.makeText(this, "로그인 성공", Toast.LENGTH_SHORT).show();
                       startActivity(new Intent(MyPageActivity.this, MainActivity.class));
                       finish();
                   }
                   else {
                       Toast.makeText(this, "로그인 실패: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                   }
                });
    }
}
