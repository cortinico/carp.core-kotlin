package dk.cachet.carp.deployment.domain

import dk.cachet.carp.common.DateTime
import dk.cachet.carp.protocols.domain.devices.AnyDeviceDescriptor
import dk.cachet.carp.protocols.domain.devices.AnyMasterDeviceDescriptor
import dk.cachet.carp.protocols.domain.devices.DeviceDescriptorSerializer
import dk.cachet.carp.protocols.domain.devices.DeviceRegistration
import dk.cachet.carp.protocols.domain.devices.DeviceRegistrationSerializer
import dk.cachet.carp.protocols.domain.tasks.TaskDescriptor
import dk.cachet.carp.protocols.domain.tasks.TaskDescriptorSerializer
import dk.cachet.carp.protocols.domain.triggers.Trigger
import dk.cachet.carp.protocols.domain.triggers.TriggerSerializer
import kotlinx.serialization.Serializable


/**
 * Contains the entire description and configuration for how a single master device participates in running a study.
 */
@Serializable
data class MasterDeviceDeployment(
    /**
     * The descriptor for the master device this deployment is intended for.
     */
    val deviceDescriptor: AnyMasterDeviceDescriptor,
    /**
     * Configuration for this master device.
     */
    @Serializable( DeviceRegistrationSerializer::class )
    val configuration: DeviceRegistration,
    /**
     * The devices this device needs to connect to.
     */
    val connectedDevices: Set<@Serializable( DeviceDescriptorSerializer::class ) AnyDeviceDescriptor>,
    /**
     * Preregistration of connected devices, including configuration such as connection properties, stored per role name.
     */
    val connectedDeviceConfigurations: Map<String, @Serializable( DeviceRegistrationSerializer::class ) DeviceRegistration>,
    /**
     * All tasks which should be able to be executed on this or connected devices.
     */
    val tasks: Set<@Serializable( TaskDescriptorSerializer::class ) TaskDescriptor>,
    /**
     * All triggers originating from this device and connected devices, stored per assigned id unique within the study protocol.
     */
    val triggers: Map<Int, @Serializable( TriggerSerializer::class ) Trigger>,
    /**
     * The specification of tasks triggered and the devices they are sent to.
     */
    val triggeredTasks: Set<TriggeredTask>
)
{
    /**
     * Specifies the task with [taskName] which is sent to [destinationDeviceRoleName] when the condition of the trigger with [triggerId] is met.
     */
    @Serializable
    data class TriggeredTask(
        /**
         * The id of the [Trigger] which describes the condition which when met sends the task with [taskName] to the device with [destinationDeviceRoleName].
         */
        val triggerId: Int,
        /**
         * The name of the task to send to [destinationDeviceRoleName] when the trigger condition is met.
         */
        val taskName: String,
        /**
         * The role name of the device to which to send the task with [taskName] when the [trigger] condition is met.
         */
        val destinationDeviceRoleName: String
    )

    /**
     * A participating master or connected device in a deployment (determined by [isConnectedDevice])
     * with a matching [registration] in case the device has been registered.
     */
    data class Device(
        val descriptor: AnyDeviceDescriptor,
        val isConnectedDevice: Boolean,
        val registration: DeviceRegistration?
    )

    /**
     * The set of [tasks] which may need to be executed on a master [device], or a connected [device], during a deployment.
     */
    data class DeviceTasks( val device: Device, val tasks: Set<TaskDescriptor> )


    /**
     * The time when this device deployment was last updated.
     * This corresponds to the most recent device registration as part of this device deployment.
     */
    val lastUpdateDate: DateTime =
        // TODO: Remove this workaround once JS serialization bug is fixed: https://github.com/Kotlin/kotlinx.serialization/issues/716
        if ( connectedDeviceConfigurations == null || configuration == null ) DateTime.now()
        else connectedDeviceConfigurations.values.plus( configuration )
            .map { it.registrationCreationDate.msSinceUTC }
            .maxOrNull()
            .let { DateTime( it!! ) }


    /**
     * Get master device and each of the devices this device needs to connect to and their current [DeviceRegistration].
     */
    fun getAllDevicesAndRegistrations(): List<Device> =
        connectedDevices.map { Device( it, true, connectedDeviceConfigurations[ it.roleName ] ) }
        .plus( Device( deviceDescriptor, false, configuration ) ) // Add master device registration.

    /**
     * Retrieves for this master device and all connected devices
     * the set of tasks which may be sent to them over the course of the deployment, or an empty set in case there are none.
     * Tasks which target other master devices are not included in this collection.
     */
    fun getTasksPerDevice(): List<DeviceTasks> = getAllDevicesAndRegistrations()
        .map { device ->
            val tasks = triggeredTasks
                .filter { it.destinationDeviceRoleName == device.descriptor.roleName }
                .map { triggered -> tasks.first { it.name == triggered.taskName } }
            DeviceTasks( device, tasks.toSet() )
        }
}
