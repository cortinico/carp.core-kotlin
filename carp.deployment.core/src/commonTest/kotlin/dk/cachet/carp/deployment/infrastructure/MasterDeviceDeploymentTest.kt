package dk.cachet.carp.deployment.infrastructure

import dk.cachet.carp.deployment.domain.MasterDeviceDeployment
import dk.cachet.carp.protocols.infrastructure.test.STUBS_SERIAL_MODULE
import dk.cachet.carp.protocols.infrastructure.test.StubDeviceDescriptor
import dk.cachet.carp.protocols.infrastructure.test.StubMasterDeviceDescriptor
import dk.cachet.carp.protocols.infrastructure.test.StubTaskDescriptor
import dk.cachet.carp.protocols.infrastructure.test.StubTrigger
import kotlin.test.*


/**
 * Tests for [MasterDeviceDeployment] relying on core infrastructure.
 */
class MasterDeviceDeploymentTest
{
    @BeforeTest
    fun initializeSerializer()
    {
        JSON = createDeploymentSerializer( STUBS_SERIAL_MODULE )
    }

    @Test
    fun can_serialize_and_deserialize_devicedeployment_using_JSON()
    {
        val device = StubMasterDeviceDescriptor()
        val connected = StubDeviceDescriptor( "Connected" )
        val task = StubTaskDescriptor( "Task" )
        val trigger = StubTrigger( connected.roleName )

        val deployment = MasterDeviceDeployment(
            device,
            device.createRegistration(),
            setOf( connected ),
            mapOf( connected.roleName to connected.createRegistration() ),
            setOf( task ),
            mapOf( 0 to trigger ),
            setOf( MasterDeviceDeployment.TriggeredTask( 0, task.name, connected.roleName ) )
        )

        val json = deployment.toJson()
        val parsed = MasterDeviceDeployment.fromJson( json )
        assertEquals( deployment, parsed )
    }

    @Test
    fun getAllDevicesAndRegistrations_succeeds()
    {
        val master = StubMasterDeviceDescriptor( "Master" )
        val registration = master.createRegistration()
        val connected = StubDeviceDescriptor( "Connected" )

        val deployment = MasterDeviceDeployment(
                master, registration, setOf( connected ), // Registered master and unregistered connected device.
            emptyMap(), emptySet(), emptyMap(), emptySet() // Otherwise, empty deployment.
        )

        val devices = deployment.getAllDevicesAndRegistrations()
        assertEquals( 2, devices.size )
        assertEquals(
            MasterDeviceDeployment.Device( master, false, registration ),
            devices.firstOrNull { it.descriptor == master }
        )
        assertEquals(
            MasterDeviceDeployment.Device( connected, true, null ),
            devices.firstOrNull { it.descriptor == connected }
        )
    }

    @Test
    fun getTasksPerDevice_succeeds()
    {
        val device = StubMasterDeviceDescriptor( "Master" )
        val registration = device.createRegistration()
        val connected = StubDeviceDescriptor( "Connected" )
        val connectedRegistration = connected.createRegistration()
        val task = StubTaskDescriptor()
        val masterTrigger = StubTrigger( device.roleName )
        val connectedTrigger = StubTrigger( connected.roleName )

        val deployment = MasterDeviceDeployment(
            deviceDescriptor = device,
            configuration = registration,
            connectedDevices = setOf( connected ),
            connectedDeviceConfigurations = mapOf( connected.roleName to connectedRegistration ),
            tasks = setOf( task ),
            triggers = mapOf( 0 to masterTrigger, 1 to connectedTrigger ),
            triggeredTasks = setOf(
                MasterDeviceDeployment.TriggeredTask( 0, task.name, device.roleName ),
                MasterDeviceDeployment.TriggeredTask( 1, task.name, connected.roleName )
            )
        )
        val deviceTasks: List<MasterDeviceDeployment.DeviceTasks> = deployment.getTasksPerDevice()

        assertEquals( 2, deviceTasks.size )
        val expectedMasterDeviceTasks = MasterDeviceDeployment.DeviceTasks(
            device = MasterDeviceDeployment.Device( device, false, registration ),
            tasks = setOf( task )
        )
        val expectedConnectedDeviceTasks = MasterDeviceDeployment.DeviceTasks(
            device = MasterDeviceDeployment.Device( connected, true, connectedRegistration ),
            tasks = setOf( task )
        )
        assertEquals( expectedMasterDeviceTasks, deviceTasks.first { it.device.descriptor == device } )
        assertEquals( expectedConnectedDeviceTasks, deviceTasks.first { it.device.descriptor == connected } )
    }

    @Test
    fun getTasksPerDevice_includes_devices_with_no_tasks()
    {
        val device = StubMasterDeviceDescriptor( "Master" )
        val registration = device.createRegistration()

        val deployment = MasterDeviceDeployment(
            deviceDescriptor = device,
            configuration = registration,
            connectedDevices = emptySet(),
            connectedDeviceConfigurations = emptyMap(),
            tasks = emptySet(),
            triggers = emptyMap(),
            triggeredTasks = emptySet()
        )
        val tasks: List<MasterDeviceDeployment.DeviceTasks> = deployment.getTasksPerDevice()

        assertEquals( 1, tasks.size )
        val expectedDeviceTasks = MasterDeviceDeployment.DeviceTasks(
            device = MasterDeviceDeployment.Device( device, false, registration ),
            tasks = emptySet()
        )
        assertEquals( expectedDeviceTasks, tasks.single() )
    }

    @Test
    fun getTaskPerDevice_does_not_include_tasks_for_other_master_devices()
    {
        val master1 = StubMasterDeviceDescriptor( "Master 1" )
        val task = StubTaskDescriptor()
        val master2 = StubMasterDeviceDescriptor( "Master 2" )
        val master1Registration = master1.createRegistration()
        val master1Trigger = StubTrigger( master1.roleName )

        val deployment = MasterDeviceDeployment(
            deviceDescriptor = master1,
            configuration = master1Registration,
            connectedDevices = emptySet(),
            connectedDeviceConfigurations = emptyMap(),
            tasks = setOf( task ),
            triggers = mapOf( 0 to master1Trigger ),
            triggeredTasks = setOf(
                MasterDeviceDeployment.TriggeredTask( 0, task.name, master1.roleName ),
                MasterDeviceDeployment.TriggeredTask( 0, "Task on Master 2", master2.roleName )
            )
        )
        val tasks: List<MasterDeviceDeployment.DeviceTasks> = deployment.getTasksPerDevice()

        assertEquals( 1, tasks.size ) // The other master device (master2) is not included.
        val expectedMasterDeviceTasks = MasterDeviceDeployment.DeviceTasks(
            device = MasterDeviceDeployment.Device( master1, false, master1Registration ),
            tasks = setOf( task )
        )
        assertEquals( expectedMasterDeviceTasks, tasks.single() )
    }
}
