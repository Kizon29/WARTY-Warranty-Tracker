package com.example.warty;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() != null) {
            openDashboard();
            return;
        }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        btnLogin.setOnClickListener(v -> loginUser());
        btnRegister.setOnClickListener(v -> registerUser());
    }

    private boolean validateInputs() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (email.isEmpty() ||
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email");
            return false;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must have at least 6 characters");
            return false;
        }

        return true;
    }

    private void registerUser() {
        if (!validateInputs()) return;

        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        btnLogin.setEnabled(false);
        btnRegister.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    btnLogin.setEnabled(true);
                    btnRegister.setEnabled(true);

                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Account created successfully!",
                                Toast.LENGTH_SHORT).show();

                        openDashboard();
                    } else {
                        showError(task.getException());
                    }
                });
    }

    private void loginUser() {
        if (!validateInputs()) return;

        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        btnLogin.setEnabled(false);
        btnRegister.setEnabled(false);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    btnLogin.setEnabled(true);
                    btnRegister.setEnabled(true);

                    if (task.isSuccessful()) {
                        openDashboard();
                    } else {
                        showError(task.getException());
                    }
                });
    }

    private void showError(Exception exception) {
        String message = exception == null
                ? "Something went wrong."
                : exception.getLocalizedMessage();

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void openDashboard() {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
