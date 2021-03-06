package dk.cachet.carp.common

import dk.cachet.carp.common.serialization.createCarpStringPrimitiveSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable


/**
 * Represents a single moment in time, stored as milliseconds since 1 January 1970 UTC.
 */
@Serializable( DateTimeSerializer::class )
expect class DateTime( msSinceUTC: Long )
{
    val msSinceUTC: Long


    companion object
    {
        fun now(): DateTime
        fun fromString( string: String ): DateTime
    }


    /**
     * Output as ISO 8601 UTC date and time in extended format with optional fractional seconds precision of up to 9 decimal places.
     * E.g., "2020-01-01T12:00:00.000Z"
     */
    override fun toString(): String
}


/**
 * A custom serializer for [DateTime].
 */
object DateTimeSerializer : KSerializer<DateTime> by createCarpStringPrimitiveSerializer( { s -> DateTime.fromString( s ) } )
