package com.eottadwotji.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/**
 * 주차 히스토리 (Room) — v2에서 SharedPreferences JSON 보관을 대체.
 * 쓰기는 출차(만료) 시점 1회뿐이라 배터리/성능 부담 없음.
 */
@Entity(tableName = "parking_records")
data class ParkingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val floor: String?,
    val zone: String?,
    val memo: String?,
    val latitude: Double?,
    val longitude: Double?,
    val lotName: String?,
    val photoUri: String?
)

@Dao
interface ParkingRecordDao {
    @Insert
    suspend fun insert(record: ParkingRecord)

    /** 대시보드 최근 기록 카드용 */
    @Query("SELECT * FROM parking_records ORDER BY endedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ParkingRecord>>

    @Query("SELECT * FROM parking_records ORDER BY endedAt DESC")
    fun all(): Flow<List<ParkingRecord>>
}

@Database(entities = [ParkingRecord::class], version = 1, exportSchema = false)
abstract class HistoryDb : RoomDatabase() {
    abstract fun dao(): ParkingRecordDao

    companion object {
        @Volatile
        private var instance: HistoryDb? = null

        fun get(context: Context): HistoryDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, HistoryDb::class.java, "parking_history"
                ).build().also {
                    instance = it
                    migrateLegacyJson(context.applicationContext, it)
                }
            }

        /** v1이 prefs에 쌓아둔 JSON 히스토리를 1회 이관 */
        private fun migrateLegacyJson(context: Context, db: HistoryDb) {
            val prefs = context.getSharedPreferences("eottadwotji", Context.MODE_PRIVATE)
            val json = prefs.getString("history_json", null) ?: return
            Thread {
                runCatching {
                    val array = JSONArray(json)
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        db.runInTransactionBlocking(
                            ParkingRecord(
                                startedAt = o.optLong("startedAt"),
                                endedAt = o.optLong("endedAt"),
                                floor = o.optString("floor").ifEmpty { null },
                                zone = o.optString("zone").ifEmpty { null },
                                memo = o.optString("memo").ifEmpty { null },
                                latitude = if (o.has("lat")) o.getDouble("lat") else null,
                                longitude = if (o.has("lon")) o.getDouble("lon") else null,
                                lotName = o.optString("lotName").ifEmpty { null },
                                photoUri = null
                            )
                        )
                    }
                    prefs.edit().remove("history_json").apply()
                }
            }.start()
        }

        private fun HistoryDb.runInTransactionBlocking(record: ParkingRecord) {
            // 마이그레이션 스레드에서의 동기 insert
            kotlinx.coroutines.runBlocking { dao().insert(record) }
        }
    }
}
