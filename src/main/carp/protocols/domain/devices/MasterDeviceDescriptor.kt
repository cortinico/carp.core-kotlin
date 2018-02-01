package carp.protocols.domain.devices

import kotlinx.serialization.Serializable


/**
 * A device which aggregates, synchronizes, and optionally uploads incoming data received from one or more connected devices (potentially just itself).
 * Typically, a desktop computer, smartphone, or web server.
 */
@Serializable
abstract class MasterDeviceDescriptor : DeviceDescriptor()