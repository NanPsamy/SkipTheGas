package com.example.skipthegas;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Locale;

import javax.annotation.Nullable;


/**
 * This a class that governs the payment screen, where a QR code is generated for payment transfer
 */
public class PaymentActivity extends AppCompatActivity {

    FirebaseFirestore firebaseFirestore;
    FirebaseAuth firebaseAuth;
    FirebaseUser currentUser;
    ImageView QR_Image;
    Button ratingButton;
    Button tipButton;
    QRCodeWriter writer;
    TextView fareView;
    TextView currentBalView;
    TextView tipEditText;

    String requestID;
    String riderEmail;
    double fare;
    double currentBal;

    String TAG = "PaymentActivity:";

    Intent feedBackIntent;

    /**
     * onCreate method for PaymentActivity
     * Retrieves and displays the associated layout file
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.payment_layout);

        feedBackIntent = new Intent(getApplicationContext(),TripFeedBackActivity.class);

        fareView = findViewById(R.id.ride_fare);
        currentBalView = findViewById(R.id.currentBalView);
        firebaseFirestore = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();

        tipEditText = findViewById(R.id.tip_edit_text);
        tipButton = findViewById(R.id.add_tip_button);

        Intent intent = getIntent();
        requestID = intent.getStringExtra("request_Id");
        if (currentUser!=null) {
            riderEmail = currentUser.getEmail();
        }

        firebaseFirestore
                .collection("all_requests")
                .document(requestID)
                .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    /**
                     * Retrieves payment-related data from the firebase database
                     * @param documentSnapshot
                     * @param e
                     */
                    @Override
                    public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                        if (e!=null) {
                            Log.i(TAG,"error occurred:"+e.getMessage());
                            return;
                        }
                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            String strFare = (String) documentSnapshot.get("est_fare");
                            String yourDriverEmail = (String) documentSnapshot.get("driver_email");
                            assert strFare != null;
                            Log.d(TAG,strFare);
                            feedBackIntent.putExtra("your_driver_email",yourDriverEmail);
                            fareView.setText(strFare);
                        } else {
                            Log.i(TAG,"Document does not exist");
                        }
                    }
                });

        firebaseFirestore
                .collection("users")
                .document(riderEmail)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    /**
                     * onComplete method gets the current balance of the rider from the database
                     * @param task
                     */
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot snapshot = task.getResult();
                            if (snapshot!=null && snapshot.exists()){
                                currentBal = (double)snapshot.get("QR_bucks");
                                currentBalView.setText(String.format(Locale.CANADA,"%.2f",(currentBal)));
                            } else {
                                Log.i(TAG,"document does not exist");
                            }
                        }
                    }
                });
        tipButton.setOnClickListener(new View.OnClickListener() {
            /**
             * Method is invoked when the tip button is clicked
             * If its a valid tip amount, it is added to the rider's payment amount
             * @param view
             */
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View view) {
                String tip = tipEditText.getText().toString();
                if (tip.length()==0){
                    Toast.makeText(PaymentActivity.this, "in valid", Toast.LENGTH_SHORT).show();
                } else {
                    double tipDouble = Double.parseDouble(tip);
                    fare = Double.parseDouble(fareView.getText().toString());
                    fare +=tipDouble;
                    fareView.setText(String.format(Locale.CANADA,"%.2f",fare));
                }
                tipEditText.setText("");
            }
        });

    }

    /**
     * Method generates the QR code on the rider's phone
     * Accounts for the ride fare and any tip amount added by the rider
     * @param view
     */
    public void generateButton(View view) {
        QR_Image = findViewById(R.id.imageView5);
        ratingButton = findViewById(R.id.rating_button);
        writer = new QRCodeWriter();
        fare = Double.parseDouble(fareView.getText().toString());
        currentBal = Double.parseDouble(currentBalView.getText().toString());
        try {
            BitMatrix bitMatrix = writer.encode(Double.toString(fare), BarcodeFormat.QR_CODE,500,500,null);
            int height = bitMatrix.getHeight();
            int width = bitMatrix.getWidth();

            // Create a Bitmap image with the same size as bitMatrix
            Bitmap bmp = Bitmap.createBitmap(width,height,Bitmap.Config.RGB_565);

            // Fill up the Bitmap bmp with the value of BitMatrix bitMatrix
            for (int x=0; x<width; x++) {
                for (int y=0; y<height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            // show the bitmap on the screen
            QR_Image.setImageBitmap(bmp);

        } catch (WriterException we) {
            Toast.makeText(this, "Unable to generate QR-code", Toast.LENGTH_SHORT).show();
            we.printStackTrace();
        }
        // charge from riders balance
        firebaseFirestore
                .collection("users")
                .document(riderEmail)
                .update("QR_bucks",currentBal - fare);
    }

    /**
     * Method redirects the rider to the driver ratings page after completion of the payment process
     * @param view
     */
    public void goToRating(View view) {
        Log.i(TAG,"request Id:"+requestID);
        feedBackIntent.putExtra("request_Id",requestID);
        feedBackIntent.putExtra("fare",fare);
        startActivity(feedBackIntent);
        finish();
    }
}
