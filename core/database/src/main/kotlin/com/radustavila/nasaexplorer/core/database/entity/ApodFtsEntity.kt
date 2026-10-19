package com.radustavila.nasaexplorer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * Full-text index over the cached entries' title and explanation. Added in schema v4.
 *
 * An external-content table: the text itself stays in `apods`, and the triggers Room generates keep
 * the index in step with every insert, update and delete there - a sync never has to touch it. Rows
 * are matched back to `apods` through the shared `rowid`.
 *
 * `apods` keys rows by date, not by an INTEGER PRIMARY KEY, so its rowids stay stable only as long
 * as nothing VACUUMs the database. Anything that does must rebuild the index afterwards
 * (`INSERT INTO apods_fts(apods_fts) VALUES ('rebuild')`, as the v3 -> v4 migration does).
 *
 * The Porter tokenizer folds English inflections together, so "galaxies" also finds "galaxy".
 */
@Fts4(contentEntity = ApodEntity::class, tokenizer = FtsOptions.TOKENIZER_PORTER)
@Entity(tableName = "apods_fts")
data class ApodFtsEntity(
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "explanation")
    val explanation: String,
)
