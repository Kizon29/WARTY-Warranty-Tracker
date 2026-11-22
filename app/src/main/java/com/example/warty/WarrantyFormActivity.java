
package com.example.warty;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WarrantyFormActivity extends AppCompatActivity {

    private EditText etProduct, etBrand, etSeller;
    private EditText etReceipt, etNotes, etDuration;
    private EditText etCustomCategory;

    private Spinner spCategory;
    private RadioGroup rgDuration;
    private TextView tvSelectedDate, tvExpiryPreview;
    private TextView tvOptionalToggle;
    private LinearLayout optionalFields;
    private Button btnSave;

    private final Calendar purchaseDate = Calendar.getInstance();

    private int durationMonths = 12;
    private boolean updatingDuration = false;
    private String warrantyId;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private final String[] categories = {
            "Electronics",
            "Computer & Accessories",
            "Mobile & Tablets",
            "Home Appliances",
            "Audio & Gaming",
            "Furniture",
            "Tools & Equipment",
            "Others"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warranty_form);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            finish();
            return;
        }

        etProduct = findViewById(R.id.etProduct);
        etBrand = findViewById(R.id.etBrand);
        etSeller = findViewById(R.id.etSeller);
        etReceipt = findViewById(R.id.etReceipt);
        etNotes = findViewById(R.id.etNotes);
        etDuration = findViewById(R.id.etDuration);
        etCustomCategory = findViewById(R.id.etCustomCategory);

        spCategory = findViewById(R.id.spCategory);
        rgDuration = findViewById(R.id.rgDuration);

        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvExpiryPreview = findViewById(R.id.tvExpiryPreview);
        tvOptionalToggle = findViewById(R.id.tvOptionalToggle);
        optionalFields = findViewById(R.id.optionalFields);
        btnSave = findViewById(R.id.btnSave);

        Button btnDate = findViewById(R.id.btnDate);
        Button btnCancel = findViewById(R.id.btnCancel);

        warrantyId = getIntent().getStringExtra("warrantyId");

        setupCategories();
        setupDuration();

        tvSelectedDate.setText(formatDate(purchaseDate));
        updateExpiryPreview();

        btnDate.setOnClickListener(v -> selectDate());
        btnSave.setOnClickListener(v -> saveWarranty());
        btnCancel.setOnClickListener(v -> finish());

        tvOptionalToggle.setOnClickListener(v -> {
            boolean opening =
                    optionalFields.getVisibility() == View.GONE;

            optionalFields.setVisibility(
                    opening ? View.VISIBLE : View.GONE);

            tvOptionalToggle.setText(opening
                    ? "- Hide additional details"
                    : "+ Additional details (optional)");
        });

        if (warrantyId != null) {
            loadWarranty();
        }
    }

    private void setupCategories() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                categories
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        spCategory.setAdapter(adapter);

        spCategory.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view, int position, long id) {

                        boolean isOther =
                                categories[position].equals("Others");

                        etCustomCategory.setVisibility(
                                isOther ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent) {}
                });
    }

    private void setupDuration() {

        rgDuration.check(R.id.rb12);

        rgDuration.setOnCheckedChangeListener(
                (group, checkedId) -> {

                    if (updatingDuration || checkedId == -1) {
                        return;
                    }

                    if (checkedId == R.id.rb3) durationMonths = 3;
                    else if (checkedId == R.id.rb6) durationMonths = 6;
                    else if (checkedId == R.id.rb12) durationMonths = 12;
                    else if (checkedId == R.id.rb24) durationMonths = 24;

                    updatingDuration = true;
                    etDuration.setText("");
                    updatingDuration = false;

                    updateExpiryPreview();
                });

        etDuration.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {

                if (updatingDuration) return;

                updatingDuration = true;

                if (s.length() > 0) {
                    rgDuration.clearCheck();
                }

                updatingDuration = false;
                updateExpiryPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private String formatDate(Calendar date) {
        return new SimpleDateFormat(
                "MM/dd/yyyy", Locale.US)
                .format(date.getTime());
    }

    private void selectDate() {
        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    purchaseDate.set(year, month, day);
                    tvSelectedDate.setText(formatDate(purchaseDate));
                    updateExpiryPreview();
                },
                purchaseDate.get(Calendar.YEAR),
                purchaseDate.get(Calendar.MONTH),
                purchaseDate.get(Calendar.DAY_OF_MONTH)
        );

        picker.getDatePicker().setMaxDate(
                System.currentTimeMillis());

        picker.show();
    }

    private int getSelectedDuration() {
        String custom = etDuration.getText().toString().trim();

        if (!custom.isEmpty()) {
            try {
                return Integer.parseInt(custom);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        if (rgDuration.getCheckedRadioButtonId() == -1) {
            return -1;
        }

        return durationMonths;
    }

    private void updateExpiryPreview() {
        int months = getSelectedDuration();

        if (months < 1 || months > 1200) {
            tvExpiryPreview.setText(
                    "Enter a valid duration (1-1200 months)");
            return;
        }

        Calendar expiry = (Calendar) purchaseDate.clone();
        expiry.add(Calendar.MONTH, months);

        tvExpiryPreview.setText(
                "Warranty expires on " + formatDate(expiry));
    }

    private void saveWarranty() {
        String product = etProduct.getText().toString().trim();

        if (product.isEmpty()) {
            etProduct.setError("Product name is required");
            etProduct.requestFocus();
            return;
        }

        String category =
                spCategory.getSelectedItem().toString();

        if (category.equals("Others")) {
            category = etCustomCategory.getText()
                    .toString().trim();

            if (category.isEmpty()) {
                etCustomCategory.setError(
                        "Please specify your category");
                etCustomCategory.requestFocus();
                return;
            }
        }

        int months = getSelectedDuration();

        if (months < 1 || months > 1200) {
            etDuration.setError(
                    "Enter a valid warranty duration");
            etDuration.requestFocus();
            return;
        }

        Calendar expiry = (Calendar) purchaseDate.clone();
        expiry.add(Calendar.MONTH, months);

        Map<String, Object> warranty = new HashMap<>();

        warranty.put("productName", product);
        warranty.put("category", category);
        warranty.put("brand",
                etBrand.getText().toString().trim());
        warranty.put("seller",
                etSeller.getText().toString().trim());
        warranty.put("receiptNumber",
                etReceipt.getText().toString().trim());
        warranty.put("notes",
                etNotes.getText().toString().trim());
        warranty.put("purchaseDate", formatDate(purchaseDate));
        warranty.put("expiryDate", formatDate(expiry));
        warranty.put("purchaseMillis",
                purchaseDate.getTimeInMillis());
        warranty.put("expiryMillis",
                expiry.getTimeInMillis());
        warranty.put("durationMonths", months);

        String uid = auth.getCurrentUser().getUid();

        DocumentReference ref = db.collection("users")
                .document(uid)
                .collection("warranties")
                .document(warrantyId == null
                        ? db.collection("users")
                        .document(uid)
                        .collection("warranties")
                        .document().getId()
                        : warrantyId);

        btnSave.setEnabled(false);

        ref.set(warranty)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this,
                            "Warranty saved!",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this,
                            e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void loadWarranty() {
        TextView title = findViewById(R.id.tvFormTitle);
        title.setText("Edit Warranty");

        btnSave.setEnabled(false);

        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("warranties")
                .document(warrantyId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        finish();
                        return;
                    }

                    etProduct.setText(doc.getString("productName"));
                    etBrand.setText(doc.getString("brand"));
                    etSeller.setText(doc.getString("seller"));
                    etReceipt.setText(doc.getString("receiptNumber"));
                    etNotes.setText(doc.getString("notes"));

                    String savedCategory =
                            doc.getString("category");

                    boolean found = false;

                    for (int i = 0; i < categories.length; i++) {
                        if (categories[i].equals(savedCategory)) {
                            spCategory.setSelection(i);
                            found = true;
                            break;
                        }
                    }

                    if (!found && savedCategory != null) {
                        spCategory.setSelection(
                                categories.length - 1);
                        etCustomCategory.setText(savedCategory);
                    }

                    Long millis = doc.getLong("purchaseMillis");

                    if (millis != null) {
                        purchaseDate.setTimeInMillis(millis);
                        tvSelectedDate.setText(
                                formatDate(purchaseDate));
                    }

                    Long duration = doc.getLong("durationMonths");

                    if (duration != null) {
                        int months = duration.intValue();

                        if (months == 3)
                            rgDuration.check(R.id.rb3);
                        else if (months == 6)
                            rgDuration.check(R.id.rb6);
                        else if (months == 12)
                            rgDuration.check(R.id.rb12);
                        else if (months == 24)
                            rgDuration.check(R.id.rb24);
                        else {
                            etDuration.setText(
                                    String.valueOf(months));
                        }
                    }

                    optionalFields.setVisibility(View.VISIBLE);
                    tvOptionalToggle.setText(
                            "- Hide additional details");

                    updateExpiryPreview();
                    btnSave.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this,
                            e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
