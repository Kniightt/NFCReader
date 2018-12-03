package com.example.temalabor.nfcreader;

import android.content.Context;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;

public class NFCHelper {
    private NfcAdapter nfcAdapter;

    public NFCHelper(Context context) {
        nfcAdapter = NfcAdapter.getDefaultAdapter(context);
    }

    public NdefMessage createTextMessage(TokenClass.Token token) {
        NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], token.toByteArray());
        return new NdefMessage(new NdefRecord[]{record});
    }

    public NfcAdapter getAdapter(){
        return nfcAdapter;
    }
}
