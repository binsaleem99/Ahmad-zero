package com.zero.crm.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "leads")
data class Lead(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val status: String, // "Hot Lead", "Follow-Up", "Not Interested"
    val rating: Int, // 1 to 5
    val callDuration: String, // e.g. "03:42"
    val offerType: String = "Template 1", // "Template 1", "Template 2", "Template 3"
    val lastContacted: Long = System.currentTimeMillis(),
    val nextMeeting: String? = null,
    val mediaUri: String? = null, // Schema version 2 addition
    val isArchived: Boolean = false
)

@Entity(tableName = "timeline_events")
data class TimelineEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val leadId: Int,
    val type: String, // "whatsapp_offer", "call_log", "manual_note"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val offerType: String = "" // Associated template if any, so we can track template history
)

@Entity(tableName = "offer_templates")
data class OfferTemplateEntity(
    @PrimaryKey val id: Int, // 1, 2, or 3
    val name: String,
    val arabicName: String,
    val messageTemplate: String, // e.g., "السلام عليكم..."
    val defaultPrice: Int,
    val minPrice: Int,
    val maxPrice: Int,
    val currency: String = "د.ك",
    val mediaUri: String? = null,
    val isArchived: Boolean = false
)

@Dao
interface LeadDao {
    @Query("SELECT * FROM leads ORDER BY lastContacted DESC")
    fun getAllLeads(): Flow<List<Lead>>

    @Query("SELECT * FROM leads WHERE id = :id")
    suspend fun getLeadById(id: Int): Lead?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: Lead): Long

    @Query("DELETE FROM leads WHERE id = :id")
    suspend fun deleteLead(id: Int)

    @Query("SELECT * FROM timeline_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<TimelineEvent>>

    @Query("SELECT * FROM timeline_events WHERE leadId = :leadId ORDER BY timestamp DESC")
    fun getEventsForLead(leadId: Int): Flow<List<TimelineEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TimelineEvent)

    @Query("SELECT * FROM offer_templates ORDER BY id ASC")
    fun getAllTemplates(): Flow<List<OfferTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: OfferTemplateEntity)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE leads ADD COLUMN mediaUri TEXT DEFAULT NULL")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Safe migration: No schema changes are required for version 6,
        // but we define this to prevent destructive wipes during app updates.
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE leads ADD COLUMN mediaUri TEXT DEFAULT NULL")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE leads ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [Lead::class, TimelineEvent::class, OfferTemplateEntity::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun leadDao(): LeadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zerocrm_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class LeadRepository(private val leadDao: LeadDao) {
    val allLeads: Flow<List<Lead>> = leadDao.getAllLeads()
    val allEvents: Flow<List<TimelineEvent>> = leadDao.getAllEvents()
    val allTemplates: Flow<List<OfferTemplateEntity>> = leadDao.getAllTemplates()

    suspend fun getLeadById(id: Int): Lead? = leadDao.getLeadById(id)

    suspend fun insertLead(lead: Lead): Long = leadDao.insertLead(lead)

    suspend fun deleteLead(id: Int) = leadDao.deleteLead(id)

    fun getEventsForLead(leadId: Int): Flow<List<TimelineEvent>> = leadDao.getEventsForLead(leadId)

    suspend fun insertEvent(event: TimelineEvent) = leadDao.insertEvent(event)

    suspend fun insertTemplate(template: OfferTemplateEntity) = leadDao.insertTemplate(template)
}

enum class LicenseState {
    FREE_UNDER_LIMIT,
    FREE_OVER_LIMIT_LOCKED,
    SUBSCRIBED_SAAS,
    ORGANIZATION_UNLOCKED
}
