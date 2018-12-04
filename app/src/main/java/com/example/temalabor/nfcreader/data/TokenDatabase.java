package com.example.temalabor.nfcreader.data;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(
        entities = {OfflineToken.class},
        version = 1
)

public abstract class TokenDatabase extends RoomDatabase {
    public abstract TokenDao TokenDao();
}
