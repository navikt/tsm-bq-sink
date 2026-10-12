package no.nav.tsm.core.bq

import org.junit.Test
import java.time.OffsetDateTime
import kotlin.test.assertEquals


class BqTypesTest {

    @Test
    fun `test a complete BqPayload`() {
        val payload = BqPayload(
            "stringField" to BqTypes.String("testString"),
            "nullableStringField" to BqTypes.String.Nullable(null),
            // Example with GMT+2 and lotsa sub-second precision, which should be truncated to seconds and converted to UTC
            "dateTimeField" to BqTypes.DateTime(OffsetDateTime.parse("2024-06-01T14:34:56.123456789+02:00")),
            "dateField" to BqTypes.Date(java.time.LocalDate.parse("2024-06-01")),
            "nullableDateField" to BqTypes.Date.Nullable(null),
            "enumField" to BqTypes.Enum(TestEnum.VALUE1),
            "nullableEnumField" to BqTypes.Enum.Nullable(null),
            "subRecordField" to BqTypes.SubRecord(
                BqPayload(
                    "nestedString" to BqTypes.String("nestedValue"),
                    "nestedDateTime" to BqTypes.DateTime(OffsetDateTime.parse("2024-06-01T14:34:56.123456789+02:00"))
                )
            ),
            "nullableSubRecordField" to BqTypes.SubRecord.Nullable(null),
            "subRecordArrayField" to BqTypes.Array(
                listOf(
                    BqPayload(
                        "nestedString" to BqTypes.String("nestedValue1"),
                        "nestedDateTime" to BqTypes.DateTime(OffsetDateTime.parse("2024-06-01T14:34:56.123456789+02:00"))
                    ),
                    BqPayload(
                        "nestedString" to BqTypes.String("nestedValue2"),
                        "nestedDateTime" to BqTypes.DateTime(OffsetDateTime.parse("2024-06-01T14:34:56.123456789+02:00"))
                    )
                ),
            ),
            "nullableSubRecordArrayField" to BqTypes.Array.Nullable(null)
        )

        val expectedMap = mapOf(
            "stringField" to "testString",
            "nullableStringField" to null,
            "dateTimeField" to "2024-06-01T12:34:56",
            "dateField" to "2024-06-01",
            "nullableDateField" to null,
            "enumField" to "VALUE1",
            "nullableEnumField" to null,
            "subRecordField" to mapOf(
                "nestedString" to "nestedValue",
                "nestedDateTime" to "2024-06-01T12:34:56"
            ),
            "nullableSubRecordField" to null,
            "subRecordArrayField" to listOf(
                mapOf(
                    "nestedString" to "nestedValue1",
                    "nestedDateTime" to "2024-06-01T12:34:56"
                ),
                mapOf(
                    "nestedString" to "nestedValue2",
                    "nestedDateTime" to "2024-06-01T12:34:56"
                )
            ),
            "nullableSubRecordArrayField" to null
        )

        assertEquals(expectedMap, payload.toContent())
    }
}

private enum class TestEnum {
    VALUE1,
    VALUE2
}
