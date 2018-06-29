package dk.cachet.carp.protocols.domain.tasks.measures

import dk.cachet.carp.protocols.domain.data.*
import dk.cachet.carp.protocols.domain.tasks.measures.Measure
import kotlinx.serialization.Serializable


@Serializable
data class StubMeasure(
    @Serializable( with = DataTypeSerializer::class )
    override val type: DataType = StubDataType() ) : Measure()