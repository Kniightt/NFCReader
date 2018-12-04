package com.example.temalabor.nfcreader.data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface TokenDao {
    @Query("SELECT * FROM offlinetoken")
    List<OfflineToken> getAll();

    @Query("DELETE FROM offlinetoken")
    void deleteAll();

    @Insert
    long insert(OfflineToken offlineToken);

    @Update

    void update(OfflineToken offlineToken);
    @Delete

    void deleteItem(OfflineToken offlineToken);
}
