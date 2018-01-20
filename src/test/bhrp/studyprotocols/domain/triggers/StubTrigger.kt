package bhrp.studyprotocols.domain.triggers

import bhrp.studyprotocols.domain.devices.DeviceDescriptor


data class StubTrigger( override val sourceDevice: DeviceDescriptor, val uniqueProperty: String = "Unique" ) : Trigger()