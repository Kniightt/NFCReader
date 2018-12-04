package com.example.temalabor.nfcreader.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity(tableName = "offlinetoken")
public class OfflineToken {

    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = false)
    public Long id;

    @ColumnInfo(name = "token")
    public String token;
}
