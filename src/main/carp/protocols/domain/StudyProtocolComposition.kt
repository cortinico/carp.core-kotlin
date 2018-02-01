package carp.protocols.domain

import carp.protocols.domain.devices.*
import carp.protocols.domain.tasks.*


/**
 * A composition root used to initialize [StudyProtocol] with concrete implementations of its interfaces.
 */
abstract class StudyProtocolComposition internal constructor(
    protected val _deviceConfiguration: DeviceConfiguration,
    protected val _taskConfiguration: TaskConfiguration ) :
    DeviceConfiguration by _deviceConfiguration,
    TaskConfiguration by _taskConfiguration