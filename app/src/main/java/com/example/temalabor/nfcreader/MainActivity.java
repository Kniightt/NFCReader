package com.example.temalabor.nfcreader;

import android.Manifest;
import android.app.PendingIntent;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import com.auth0.android.jwt.JWT;
import com.example.temalabor.nfcreader.adapter.Adapter;
import com.example.temalabor.nfcreader.data.OfflineToken;
import com.example.temalabor.nfcreader.data.TokenDatabase;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    TextView receivedMessage;
    TextView numofTokens;
    Button btnClear;
    Button btnVerifiy;
    PendingIntent pendingIntent;
    IntentFilter[] intentFilters;
    NfcAdapter nfcAdapter;
    NFCHelper nfcHelper;
    FirebaseFunctions function;
    FirebaseAuth auth;
    TokenClass.Token token;
    String secret = "VLHDVPQELHFQEPIFHEQBFIUKJBWSDIFKDSFBKfdoFULHOeqfugeqIFLKQGEFBSJHAMFVQIKHFGOEUWJLAGFBLWEFF";
    String imei;
    Adapter adapter;
    TokenDatabase tokenDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        receivedMessage = findViewById(R.id.receivedMessage);
        numofTokens = findViewById(R.id.tvofflinetokens);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcHelper = new NFCHelper(this);
        auth = FirebaseAuth.getInstance();
        function = FirebaseFunctions.getInstance();
        btnClear = findViewById(R.id.btnclear);
        btnVerifiy = findViewById(R.id.btnverify);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DeleteAll();
            }
        });
        btnVerifiy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tryToPushTokens();
            }
        });
        adapter = new Adapter();
        tokenDatabase = Room.databaseBuilder(getApplicationContext(), TokenDatabase.class, "offline-tokens").build();
        loadTokensInBackground();
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
  //       authenticate(offlinetoken);
    }

    private void DeleteAll() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                tokenDatabase.TokenDao().deleteAll();
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                adapter.deleteAll();
                updateNumOfTokens();

            }
        }.execute();
    }

    private void tryToPushTokens() {

        for (OfflineToken token: adapter.getAll()) {
            pushToken(token).addOnCompleteListener(new OnCompleteListener<String>() {
                @Override
                public void onComplete(@NonNull Task<String> task) {
                    if (task.isSuccessful()){
                        if (task.getResult().equals("Error")){
                            receivedMessage.setText("Error");
                        }
                        else {
                            DeleteToken(Long.parseLong(task.getResult()));
                        }
                    }
                }
            });
        }

    }

    private void DeleteToken(long l) {
        final OfflineToken offlineToken = adapter.get(l);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                tokenDatabase.TokenDao().deleteItem(offlineToken);
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                adapter.delete(offlineToken);
                updateNumOfTokens();

            }
        }.execute();

    }

    private Task<String> pushToken(OfflineToken token) {
        Map<String,Object> data = new HashMap<>();
        data.put("token",token.token);
        data.put("imei",imei);
        return function.getHttpsCallable("pushedToken")
                .call(data).continueWith(new Continuation<HttpsCallableResult, String>() {
                    @Override
                    public String then(@NonNull Task<HttpsCallableResult> task) throws Exception {
                        String result = (String) task.getResult().getData();
                        return result;
                    }
                });
    }

    private void loadTokensInBackground() {
        new AsyncTask<Void, Void, List<OfflineToken>>() {
            @Override
            protected List<OfflineToken> doInBackground(Void... voids) {
                return tokenDatabase.TokenDao().getAll();
            }

            @Override
            protected void onPostExecute(List<OfflineToken> tokens) {
                adapter.addAll(tokens);
                updateNumOfTokens();

            }
        }.execute();
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
                final JWT jwt = new JWT(result);
                String count = jwt.getClaim("count").asString();
                tokenVerified(result, count).addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        String result = task.getResult();
                        receivedMessage.setText(jwt.getClaim("id").asString() +
                                "\nNew usage: " + jwt.getClaim("count").asString());
                    }
                });
                TokenClass.Token token = TokenClass.Token.newBuilder()
                        .setToken(result).build();
                NdefMessage message = nfcHelper.createTextMessage(token);
                nfcHelper.getAdapter().setNdefPushMessage(message, MainActivity.this);
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
                    data.put("sid", jws.getBody().get("sid",String.class));
                    data.put("useremail",jws.getBody().get("useremail",String.class));
                    data.put("count", jws.getBody().get("count", Integer.class) - 1);
                    String verified = Jwts.builder()
                            .setExpiration(jws.getBody().getExpiration())
                            .setIssuedAt(jws.getBody().getIssuedAt())
                            .addClaims(data)
                            .signWith(SignatureAlgorithm.HS256, encoded)
                            .compact();
                    Long id = Long.parseLong(jws.getBody().get("id", String.class));
                    if (adapter.contains(id)) {
                        updateOfflineToken(id, verified);
                    } else {
                        tokenOfflineVerified(id, verified);
                    }
                    TokenClass.Token token = TokenClass.Token.newBuilder()
                            .setToken(verified)
                            .build();
                    NdefMessage message = nfcHelper.createTextMessage(token);
                    nfcHelper.getAdapter().setNdefPushMessage(message, MainActivity.this);
                    receivedMessage.setText("Token offline verified\nUsages: " + Integer.toString(jws.getBody().get("count", Integer.class) - 1));
                } else {
                    receivedMessage.setText("No more usages!");
                    TokenClass.Token response = TokenClass.Token.newBuilder().setToken("No more usages!").build();
                    NdefMessage message = nfcHelper.createTextMessage(response);
                    nfcHelper.getAdapter().setNdefPushMessage(message, this);
                }


            } catch (JwtException ex) {
                receivedMessage.setText(ex.getLocalizedMessage());
                TokenClass.Token response = TokenClass.Token.newBuilder().setToken(ex.getLocalizedMessage()).build();
                NdefMessage message = nfcHelper.createTextMessage(response);
                nfcHelper.getAdapter().setNdefPushMessage(message, this);
            }
        }

    }

    private void updateOfflineToken(Long id, String verified) {
        final OfflineToken offlineToken = adapter.get(id);
        offlineToken.token = verified;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                tokenDatabase.TokenDao().update(offlineToken);
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                adapter.update(offlineToken);
                updateNumOfTokens();

            }
        }.execute();
    }

    private void tokenOfflineVerified(Long id, String verified) {
        final OfflineToken offlineToken = new OfflineToken();
        offlineToken.id = id;
        offlineToken.token = verified;
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                tokenDatabase.TokenDao().insert(offlineToken);
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                adapter.add(offlineToken);
                updateNumOfTokens();

            }
        }.execute();
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
        if (result.equals("No more usages!")) {
            receivedMessage.setText(result);
            TokenClass.Token response = TokenClass.Token.newBuilder().setToken(result).build();
            NdefMessage message = nfcHelper.createTextMessage(response);
            nfcHelper.getAdapter().setNdefPushMessage(message, this);
            return false;

        } else if (result.equals("Token doesnt exist")) {
            receivedMessage.setText(result);
            TokenClass.Token response = TokenClass.Token.newBuilder().setToken(result).build();
            NdefMessage message = nfcHelper.createTextMessage(response);
            nfcHelper.getAdapter().setNdefPushMessage(message, this);
            return false;
        } else return true;
    }

    public void updateNumOfTokens(){
        numofTokens.setText("Tokens: " + adapter.getCount());
    }

}
