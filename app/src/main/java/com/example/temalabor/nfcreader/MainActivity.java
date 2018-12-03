package com.example.temalabor.nfcreader;

import android.Manifest;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    TextView receivedMessage;
    PendingIntent pendingIntent;
    IntentFilter[] intentFilters;
    NfcAdapter nfcAdapter;
    NFCHelper nfcHelper;
    FirebaseFunctions function;
    FirebaseAuth auth;
    TokenClass.Token token;
    String secret = "VLHDVPQELHFQEPIFHEQBFIUKJBWSDIFKDSFBKfdoFULHOeqfugeqIFLKQGEFBSJHAMFVQIKHFGOEUWJLAGFBLWEFF";
    String imei;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        receivedMessage = findViewById(R.id.receivedMessage);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcHelper = new NFCHelper(this);
        auth = FirebaseAuth.getInstance();
        function = FirebaseFunctions.getInstance();

        Intent nfcIntent = new Intent(this, getClass());
        nfcIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        pendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, 0);

        IntentFilter ndefIntentFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            ndefIntentFilter.addDataType("text/plain");
            intentFilters = new IntentFilter[]{ndefIntentFilter};
        } catch (IntentFilter.MalformedMimeTypeException e) {
            Log.e(this.toString(), e.getMessage());
        }
        nfcAdapter.setNdefPushMessage(null, this);
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {Manifest.permission.READ_PHONE_STATE};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(permissions, 1);
            }
        }
        imei = tm.getDeviceId();
    }

    @Override
    protected void onResume() {
        super.onResume();
        nfcAdapter.enableForegroundDispatch(this, pendingIntent,
                intentFilters, null);
    }

    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            Parcelable[] receivedArray = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (receivedArray != null) {
                NdefMessage message = (NdefMessage) receivedArray[0];
                NdefRecord[] records = message.getRecords();
                try {
                    token = TokenClass.Token.parseFrom(records[0].getPayload());
                    authenticate(token.getToken());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void authenticate(final String strToken) {
        verifyToken(strToken)
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (task.isSuccessful())
                            updateUI(task.getResult(), strToken);
                        else {
                            updateUI("offline", strToken);
                        }
                    }
                });
    }

    private Task<String> verifyToken(String strToken) {
        Map<String, Object> data = new HashMap<>();
        data.put("imei", imei);
        data.put("token", strToken);

        return function.getHttpsCallable("verifyToken")
                .call(data).continueWith(new Continuation<HttpsCallableResult, String>() {
                    @Override
                    public String then(@NonNull Task<HttpsCallableResult> task) throws Exception {
                        String result = (String) task.getResult().getData();
                        return result;
                    }
                });
    }

    private void updateUI(String result, String strToken) {
        if (!result.equals("offline")) {
            if (isSuccessful(result)) {
                // token.getUid()
          /*  String strMessage = "Authentication Successful." + "\nUser email: " + auth.getCurrentUser().getEmail()
                    + "\nUID: " + auth.getCurrentUser().getUid() + "\nToken: " + token.getToken();*/
                receivedMessage.setText(result);
                tokenVerified(strToken, result).addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        String result = task.getResult();
                        receivedMessage.setText(result);
                    }
                });
            } else {

            }

        } else {
            Jws<Claims> jws;
            try {
                String encoded = Base64.encodeToString(secret.getBytes(), Base64.CRLF);
                JwtParser parser = Jwts.parser().setSigningKey(encoded);
                jws = parser.parseClaimsJws(strToken);
                int count = jws.getBody().get("count", Integer.class);

                if (count > 0) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", jws.getBody().get("id", String.class));
                    data.put("uid", jws.getBody().get("uid", String.class));
                    data.put("count", jws.getBody().get("count", Integer.class) - 1);
                    String verified = Jwts.builder()
                            .setExpiration(jws.getBody().getExpiration())
                            .setIssuedAt(jws.getBody().getIssuedAt())
                            .addClaims(data)
                            .signWith(SignatureAlgorithm.HS256, encoded)
                            .compact();
                    TokenClass.Token token = TokenClass.Token.newBuilder()
                            .setUid(jws.getBody().get("uid",String.class))
                            .setToken(verified)
                            .build();
                    NdefMessage message = nfcHelper.createTextMessage(token);
                    nfcHelper.getAdapter().setNdefPushMessage(message,MainActivity.this);
                    receivedMessage.setText(token.toString());
                    receivedMessage.setText("Token offline verified\nUsages: " + Integer.toString(jws.getBody().get("count",Integer.class)-1));
                }
                else {
                    receivedMessage.setText("No more usages!");
                }


            } catch (JwtException ex) {
                receivedMessage.setText(ex.getLocalizedMessage());
            }
        }

    }

    private Task<String> tokenVerified(String strToken, String result) {
        Map<String, Object> data = new HashMap<>();
        data.put("imei", imei);
        data.put("token", strToken);
        data.put("count", result);

        return function.getHttpsCallable("tokenVerified")
                .call(data).continueWith(new Continuation<HttpsCallableResult, String>() {
                    @Override
                    public String then(@NonNull Task<HttpsCallableResult> task) throws Exception {
                        String result = (String) task.getResult().getData();
                        return result;
                    }
                });

    }

    private boolean isSuccessful(String result) {
        return !result.equals("No more usages!") && !result.equals("Token doesnt exist");
    }

}
