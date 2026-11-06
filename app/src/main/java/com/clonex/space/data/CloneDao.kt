package com.clonex.space.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CloneDao {

    @Query("SELECT * FROM clones ORDER BY installedAt DESC")
    fun getAll(): Flow<List<CloneEntry>>

    @Query("SELECT * FROM clones ORDER BY installedAt DESC")
    suspend fun getAllOnce(): List<CloneEntry>

    @Query("SELECT * FROM clones WHERE packageName = :packageName ORDER BY cloneIndex ASC")
    fun getByPackage(packageName: String): Flow<List<CloneEntry>>

    @Query("SELECT COUNT(*) FROM clones WHERE packageName = :packageName")
    suspend fun countForPackage(packageName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CloneEntry)

    @Query("DELETE FROM clones WHERE packageName = :packageName AND cloneIndex = :cloneIndex")
    suspend fun delete(packageName: String, cloneIndex: Int)

    @Query("UPDATE clones SET isPrivate = :isPrivate WHERE packageName = :packageName AND cloneIndex = :cloneIndex")
    suspend fun setPrivate(packageName: String, cloneIndex: Int, isPrivate: Boolean)

    @Query("DELETE FROM clones")
    suspend fun deleteAll()
}
