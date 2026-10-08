package no.nav.tsm.core

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.StandardSQLTypeName
import com.google.cloud.bigquery.TableId

const val DATASET_ID = "tsm_kafka_sink"

val MIGRATIONS_TABLE: TableId = TableId.of(DATASET_ID, "_migrations")
val SYKMELDINGER_TABLE: TableId = TableId.of(DATASET_ID, "sykmeldinger")

fun BigQuery.getCurrentSchemaVersion(): Long {
    return this.query(
        QueryJobConfiguration.newBuilder(
            "SELECT schema_version FROM `${DATASET_ID}._migrations` ORDER BY last_updated DESC LIMIT 1"
        ).build()
    ).values.firstOrNull()?.get(0)?.longValue ?: 0L
}

fun BigQuery.insertSchemaVersion(version: Long) {
    insertAll(
        InsertAllRequest.newBuilder(MIGRATIONS_TABLE)
            .addRow(mapOf("schema_version" to version, "last_updated" to System.currentTimeMillis() / 1000.0))
            .build()
    )
}

fun nonNullableField(name: String, type: StandardSQLTypeName): Field =
    Field.newBuilder(name, type).setMode(Field.Mode.REQUIRED).build()

fun field(name: String, type: StandardSQLTypeName): Field =
    Field.newBuilder(name, type).setMode(Field.Mode.NULLABLE).build()
