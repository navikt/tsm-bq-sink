package no.nav.tsm.bq.migrations

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.Clustering
import com.google.cloud.bigquery.StandardTableDefinition
import no.nav.tsm.ktor.logger
import no.nav.tsm.sykmelding.SYKMELDINGER_TABLE

private val logger = logger()

fun v2ClusterNasjonal(bq: BigQuery) {
    logger.info("Migrating to schema version 2")

    val clustering = Clustering.newBuilder()
        .setFields(listOf("regelutfall", "type", "meta_avsender_system"))
        .build()

    val table = bq.getTable(SYKMELDINGER_TABLE)
    val definition = table.getDefinition<StandardTableDefinition>()

    bq.update(
        table.toBuilder()
            .setDefinition(definition.toBuilder().setClustering(clustering).build())
            .build()
    )

    bq.insertSchemaVersion(2)
}
