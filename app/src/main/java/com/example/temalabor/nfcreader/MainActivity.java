package com.example.temalabor.nfcreader;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.protobuf.InvalidProtocolBufferException;

public class MainActivity extends AppCompatActivity {

    TextView receivedMessage;
    PendingIntent pendingIntent;
    IntentFilter[] intentFilters;
    NfcAdapter nfcAdapter;
    FirebaseFunctions function;
    FirebaseAuth auth;
    TokenClass.Token token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        receivedMessage = findViewById(R.id.receivedMessage);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
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

    public void authenticate(String strToken){
        verifyToken(strToken)
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (task.isSuccessful())
                            updateUI(task.getResult());
                        else {
                            token = null;
                            updateUI("");
                        }
                    }
                });
    }

    private Task<String> verifyToken(String strToken) {
        return function.getHttpsCallable("verifyToken")
                .call(strToken).continueWith(new Continuation<HttpsCallableResult, String>() {
                    @Override
                    public String then(@NonNull Task<HttpsCallableResult> task) throws Exception {
                        String result = (String) task.getResult().getData();
                        return result;
                    }
                });
    }

    private void updateUI(String result){
        if (token != null) {
            // token.getUid()
          /*  String strMessage = "Authentication Successful." + "\nUser email: " + auth.getCurrentUser().getEmail()
                    + "\nUID: " + auth.getCurrentUser().getUid() + "\nToken: " + token.getToken();*/
            receivedMessage.setText(result);
        }
        else
            receivedMessage.setText(R.string.auth_fail);
    }
}