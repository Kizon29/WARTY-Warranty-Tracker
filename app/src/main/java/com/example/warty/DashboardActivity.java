
package com.example.warty;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DashboardActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration listener;

    private TextView tvTotal, tvActive, tvExpiring, tvExpired;
    private EditText etSearch;
    private RadioGroup rgFilter;
    private LinearLayout warrantyContainer;

    private final List<DocumentSnapshot> warranties =
            new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            goToLogin();
            return;
        }

        tvTotal = findViewById(R.id.tvTotal);
        tvActive = findViewById(R.id.tvActive);
        tvExpiring = findViewById(R.id.tvExpiring);
        tvExpired = findViewById(R.id.tvExpired);

        etSearch = findViewById(R.id.etSearch);
        rgFilter = findViewById(R.id.rgFilter);
        warrantyContainer = findViewById(R.id.warrantyContainer);

        Button btnAdd = findViewById(R.id.btnAddWarranty);
        Button btnLogout = findViewById(R.id.btnLogout);

        btnAdd.setOnClickListener(v ->
                startActivity(new Intent(
                        this, WarrantyFormActivity.class)));

        btnLogout.setOnClickListener(v -> {
            if (listener != null) {
                listener.remove();
                listener = null;
            }
            auth.signOut();
            goToLogin();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start,
                    int count, int after) {}

            @Override
            public void onTextChanged(
                    CharSequence s, int start,
                    int before, int count) {
                displayWarranties();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        rgFilter.setOnCheckedChangeListener(
                (group, checkedId) -> displayWarranties());

        loadWarranties();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics());
    }

    private GradientDrawable roundedBackground(
            int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void loadWarranties() {
        String uid = auth.getCurrentUser().getUid();

        listener = db.collection("users")
                .document(uid)
                .collection("warranties")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this,
                                "Failed to load warranties: "
                                        + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    warranties.clear();

                    if (snapshot != null) {
                        warranties.addAll(snapshot.getDocuments());
                    }

                    displayWarranties();
                });
    }

    private long daysRemaining(long expiryMillis) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar expiry = Calendar.getInstance();
        expiry.setTimeInMillis(expiryMillis);
        expiry.set(Calendar.HOUR_OF_DAY, 0);
        expiry.set(Calendar.MINUTE, 0);
        expiry.set(Calendar.SECOND, 0);
        expiry.set(Calendar.MILLISECOND, 0);

        return TimeUnit.MILLISECONDS.toDays(
                expiry.getTimeInMillis()
                        - today.getTimeInMillis());
    }

    private String statusFor(long days) {
        if (days < 0) return "EXPIRED";
        if (days <= 30) return "EXPIRING SOON";
        return "ACTIVE";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void displayWarranties() {
        warrantyContainer.removeAllViews();

        int active = 0;
        int expiring = 0;
        int expired = 0;

        String search = etSearch.getText().toString()
                .trim().toLowerCase(Locale.ROOT);

        int selectedFilter = rgFilter.getCheckedRadioButtonId();

        for (DocumentSnapshot doc : warranties) {
            Long expiryMillis = doc.getLong("expiryMillis");
            if (expiryMillis == null) continue;

            long days = daysRemaining(expiryMillis);
            String status = statusFor(days);

            if (status.equals("ACTIVE")) active++;
            else if (status.equals("EXPIRING SOON")) expiring++;
            else expired++;

            String product = safe(doc.getString("productName"));
            String brand = safe(doc.getString("brand"));
            String category = safe(doc.getString("category"));

            String searchable = (product + " " + brand + " " + category)
                    .toLowerCase(Locale.ROOT);

            if (!searchable.contains(search)) continue;

            if (selectedFilter == R.id.rbActive
                    && !status.equals("ACTIVE")) continue;

            if (selectedFilter == R.id.rbExpiring
                    && !status.equals("EXPIRING SOON")) continue;

            if (selectedFilter == R.id.rbExpired
                    && !status.equals("EXPIRED")) continue;

            addWarrantyCard(doc, product, brand, category, days, status);
        }

        tvTotal.setText(warranties.size() + "\nTOTAL");
        tvActive.setText(active + "\nACTIVE");
        tvExpiring.setText(expiring + "\nEXPIRING SOON");
        tvExpired.setText(expired + "\nEXPIRED");

        if (warrantyContainer.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText(warranties.isEmpty()
                    ? "No warranties yet. Add your first product!"
                    : "No matching warranties found.");
            empty.setTextColor(Color.GRAY);
            empty.setTextSize(15);
            empty.setPadding(dp(10), dp(30), dp(10), dp(30));
            warrantyContainer.addView(empty);
        }
    }

    private void addWarrantyCard(
            DocumentSnapshot doc,
            String product,
            String brand,
            String category,
            long days,
            String status) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(
                roundedBackground(Color.WHITE, 16));
        card.setElevation(dp(2));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);

        warrantyContainer.addView(card, params);

        TextView name = new TextView(this);
        name.setText(product);
        name.setTextSize(19);
        name.setTextColor(Color.rgb(23, 54, 93));
        name.setTypeface(null, Typeface.BOLD);
        card.addView(name);

        TextView subtitle = new TextView(this);
        subtitle.setText(
                (brand.isEmpty() ? "" : brand + " · ") + category);
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.rgb(100, 116, 139));
        subtitle.setPadding(0, dp(4), 0, dp(12));
        card.addView(subtitle);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(this);
        badge.setText(status);
        badge.setTextSize(11);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(dp(10), dp(6), dp(10), dp(6));

        int badgeColor;
        int textColor;

        if (status.equals("EXPIRED")) {
            badgeColor = Color.rgb(254, 226, 226);
            textColor = Color.rgb(185, 28, 28);
        } else if (status.equals("EXPIRING SOON")) {
            badgeColor = Color.rgb(254, 243, 199);
            textColor = Color.rgb(180, 83, 9);
        } else {
            badgeColor = Color.rgb(220, 252, 231);
            textColor = Color.rgb(21, 128, 61);
        }

        badge.setBackground(
                roundedBackground(badgeColor, 20));
        badge.setTextColor(textColor);
        bottom.addView(badge);

        TextView countdown = new TextView(this);
        countdown.setText(days >= 0
                ? days + " days remaining"
                : "Expired " + Math.abs(days) + " days ago");
        countdown.setTextSize(12);
        countdown.setTextColor(Color.rgb(100, 116, 139));
        countdown.setGravity(Gravity.END);

        LinearLayout.LayoutParams countdownParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1);
        countdownParams.leftMargin = dp(8);

        bottom.addView(countdown, countdownParams);
        card.addView(bottom);

        card.setOnClickListener(v -> showWarrantyDetails(doc));
    }

    private void showWarrantyDetails(DocumentSnapshot doc) {
        String details =
                "Product: " + safe(doc.getString("productName"))
                        + "\nBrand: " + safe(doc.getString("brand"))
                        + "\nCategory: " + safe(doc.getString("category"))
                        + "\nSeller: " + safe(doc.getString("seller"))
                        + "\nReceipt / Order No.: "
                        + safe(doc.getString("receiptNumber"))
                        + "\nPurchase Date: "
                        + safe(doc.getString("purchaseDate"))
                        + "\nExpiration Date: "
                        + safe(doc.getString("expiryDate"))
                        + "\nWarranty Duration: "
                        + doc.getLong("durationMonths") + " months"
                        + "\nNotes: " + safe(doc.getString("notes"));

        new AlertDialog.Builder(this)
                .setTitle(safe(doc.getString("productName")))
                .setMessage(details)
                .setNeutralButton("DELETE", (dialog, which) ->
                        confirmDelete(doc))
                .setNegativeButton("CLOSE", null)
                .setPositiveButton("EDIT", (dialog, which) -> {
                    Intent intent = new Intent(
                            this, WarrantyFormActivity.class);
                    intent.putExtra("warrantyId", doc.getId());
                    startActivity(intent);
                })
                .show();
    }

    private void confirmDelete(DocumentSnapshot doc) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Warranty?")
                .setMessage(
                        "This warranty will be permanently deleted.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE", (dialog, which) -> {
                    String uid = auth.getCurrentUser().getUid();

                    db.collection("users")
                            .document(uid)
                            .collection("warranties")
                            .document(doc.getId())
                            .delete()
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(this,
                                            "Warranty deleted",
                                            Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this,
                                            e.getMessage(),
                                            Toast.LENGTH_LONG).show());
                })
                .show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }
}
