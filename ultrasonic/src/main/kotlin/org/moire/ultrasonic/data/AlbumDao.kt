package org.moire.ultrasonic.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import org.moire.ultrasonic.domain.Album

@Dao
interface AlbumDao : GenericDao<Album> {
    /**
     * Clear the whole database
     */
    @Query("DELETE FROM albums")
    fun clear()

    /**
     * Get all albums
     */
    @Query("SELECT * FROM albums")
    fun get(): List<Album>

    /**
     * Get all albums in a specific range
     */
    @Query("SELECT * FROM albums LIMIT :offset,:size")
    fun get(size: Int, offset: Int = 0): List<Album>

    /**
     * Get all albums in a specific range in a certain order
     */
    @Query("SELECT * FROM albums ORDER BY artist ASC LIMIT :offset,:size ")
    fun orderedByArtist(size: Int, offset: Int = 0): List<Album>

    /**
     * Get all albums in a specific range in a certain order
     */
    @Query("SELECT * FROM albums ORDER BY created DESC LIMIT :offset,:size ")
    fun orderedByAge(size: Int, offset: Int = 0): List<Album>

    /**
     * Get all albums in a specific range in a certain order
     */
    @Query("SELECT * FROM albums ORDER BY title ASC LIMIT :offset,:size ")
    fun orderedByName(size: Int, offset: Int = 0): List<Album>

    /**
     * Get album by id
     */
    @Query("SELECT * FROM albums where id LIKE :albumId LIMIT 1")
    fun get(albumId: String): Album?

    /**
     * Get albums by artist
     */
    @Query("SELECT * FROM albums WHERE artistId LIKE :id")
    fun byArtist(id: String): List<Album>

    /**
     * Clear albums by artist
     */
    @Query("DELETE FROM albums WHERE artistId LIKE :id")
    fun clearByArtist(id: String)

    /**
     * TODO: Make generic
     * Upserts (insert or update) an object to the database
     *
     * @param obj the object to upsert
     */
    @Transaction
    @JvmSuppressWildcards
    fun upsert(obj: Album) {
        val id = insertIgnoring(obj)
        if (id == -1L) {
            update(obj)
        }
    }

    /**
     * Upserts (insert or update) a list of objects
     *
     * @param objList the object to be upserted
     */
    @Transaction
    @JvmSuppressWildcards
    fun upsert(objList: List<Album>) {
        val insertResult = insertIgnoring(objList)
        val updateList: MutableList<Album> = ArrayList()
        for (i in insertResult.indices) {
            if (insertResult[i] == -1L) {
                updateList.add(objList[i])
            }
        }
        if (updateList.isNotEmpty()) {
            update(updateList)
        }
    }
}
