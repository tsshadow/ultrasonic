package org.moire.ultrasonic.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Room Dao for the Server Setting table
 */
@Dao
interface ServerSettingDao {

    /**
     *Inserts a new Server Setting to the table
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg serverSetting: ServerSetting)

    /**
     * Deletes a Server Setting from the table
     */
    @Delete
    suspend fun delete(serverSetting: ServerSetting)

    /**
     * Updates an existing Server Setting in the table
     */
    @Update
    suspend fun update(vararg serverSetting: ServerSetting)

    /**
     * Loads all Server Settings from the table
     */
    @Query("SELECT * FROM serverSetting")
    fun loadAllServerSettings(): LiveData<List<ServerSetting>>

    /**
     * Finds a Server Setting by its unique Id
     */
    @Query("SELECT * FROM serverSetting WHERE [id] = :id")
    suspend fun findById(id: Int): ServerSetting?

    /**
     * Finds a Server Setting by its Index in the Select List
     */
    @Query("SELECT * FROM serverSetting WHERE [index] = :index")
    suspend fun findByIndex(index: Int): ServerSetting?

    /**
     * Finds a Server Setting by its Index in the Select List
     * @return LiveData of the ServerSetting
     */
    @Query("SELECT * FROM serverSetting WHERE [index] = :index")
    fun getLiveServerSettingByIndex(index: Int): LiveData<ServerSetting?>

    /**
     * Retrieves the count of rows in the table
     */
    @Query("SELECT COUNT(*) FROM serverSetting")
    suspend fun count(): Int?

    /**
     * Retrieves the count of rows in the table as a LiveData
     */
    @Query("SELECT COUNT(*) FROM serverSetting")
    fun liveServerCount(): LiveData<Int?>

    /**
     * Retrieves the greatest value of the Index column in the table
     */
    @Query("SELECT MAX([index]) FROM serverSetting")
    suspend fun getMaxIndex(): Int?
}
