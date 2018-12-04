package com.example.temalabor.nfcreader.adapter;

import com.example.temalabor.nfcreader.data.OfflineToken;

import java.util.ArrayList;
import java.util.List;

public class Adapter {

    private List<OfflineToken> tokens = new ArrayList<>();

    public void add(OfflineToken offlineToken) {
        tokens.add(offlineToken);
    }

    public void addAll(List<OfflineToken> offlineTokens) {
        this.tokens.clear();
        this.tokens = offlineTokens;
    }

    public boolean contains(Long id) {
        for (OfflineToken token : tokens) {
           if (token.id.equals(id)) return true;
        }
        return false;
    }

    public OfflineToken get(Long id) {
        for (OfflineToken ot: tokens) {
            if(ot.id.equals(id)) return ot;
        }
        return null;
    }

    public void update(OfflineToken offlineToken) {
        for (OfflineToken ot: tokens) {
            if(ot.id.equals(offlineToken.id)) ot.token = offlineToken.token;
        }
    }

    public List<OfflineToken> getAll() {
        return tokens;
    }

    public void delete(OfflineToken offlineToken) {
        tokens.remove(offlineToken);
    }

    public void deleteAll() {
        tokens.clear();
    }

    public String getCount() {
        return Integer.toString(tokens.size());
    }
}
