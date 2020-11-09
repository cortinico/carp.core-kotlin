package dk.cachet.carp.deployment.application

import dk.cachet.carp.common.UUID
import dk.cachet.carp.common.users.AccountIdentity
import dk.cachet.carp.deployment.domain.users.AccountService
import dk.cachet.carp.deployment.domain.users.ActiveParticipationInvitation
import dk.cachet.carp.deployment.domain.users.Participation
import dk.cachet.carp.deployment.domain.users.StudyInvitation
import dk.cachet.carp.protocols.infrastructure.test.createSingleMasterDeviceProtocol
import dk.cachet.carp.test.runSuspendTest
import kotlin.test.*


private val unknownId: UUID = UUID.randomUUID()


/**
 * Tests for implementations of [ParticipationService].
 */
abstract class ParticipationServiceTest
{
    /**
     * Create a deployment service and account service it depends on to be used in the tests.
     */
    abstract fun createService(): Triple<ParticipationService, DeploymentService, AccountService>


    @Test
    fun addParticipation_after_stop_not_allowed() = runSuspendTest {
        val (participationService, deploymentService, _) = createService()
        val studyDeploymentId = addTestDeployment( deploymentService )
        deploymentService.stop( studyDeploymentId )

        val accountId = AccountIdentity.fromUsername( "Test" )
        val invitation = StudyInvitation.empty()
        assertFailsWith<IllegalStateException>
            { participationService.addParticipation( studyDeploymentId, setOf( deviceRoleName ), accountId, invitation ) }
    }

    @Test
    fun addParticipation_has_matching_studyDeploymentId() = runSuspendTest {
        val (participationService, deploymentService, _) = createService()
        val studyDeploymentId = addTestDeployment( deploymentService )

        val accountIdentity = AccountIdentity.fromUsername( "test" )
        val invitation = StudyInvitation.empty()
        val participation = participationService.addParticipation( studyDeploymentId, setOf( deviceRoleName ), accountIdentity, invitation )

        assertEquals( studyDeploymentId, participation.studyDeploymentId )
    }

    @Test
    fun addParticipation_creates_new_account_for_new_identity() = runSuspendTest {
        val (participationService, deploymentService, accountService) = createService()
        val studyDeploymentId = addTestDeployment( deploymentService )

        val emailIdentity = AccountIdentity.fromEmailAddress( "test@test.com" )
        val invitation = StudyInvitation.empty()
        participationService.addParticipation( studyDeploymentId, setOf( deviceRoleName ), emailIdentity, invitation )

        // Verify whether account was added.
        val foundAccount = accountService.findAccount( emailIdentity )
        assertNotNull( foundAccount )
    }

    @Suppress( "ReplaceAssertBooleanWithAssertEquality" )
    @Test
    fun addParticipation_with_same_studyDeploymentId_and_identity() = runSuspendTest {
        val (participationService, deploymentService, _) = createService()
        val studyDeploymentId = addTestDeployment( deploymentService )

        val emailIdentity = AccountIdentity.fromEmailAddress( "test@test.com" )
        val invitation = StudyInvitation.empty()

        // Adding the same identity to a deployment returns the same participation.
        val p1: Participation = participationService.addParticipation( studyDeploymentId, setOf( deviceRoleName ), emailIdentity, invitation )
        val p2: Participation = participationService.addParticipation( studyDeploymentId, setOf( deviceRoleName ), emailIdentity, invitation )
        assertTrue( p1.id == p2.id )
    }

    @Test
    fun addParticipation_fails_for_second_differing_request() = runSuspendTest {
        val (participationService, deploymentService, _) = createService()
        val studyDeploymentId = addTestDeployment( deploymentService )
        val emailIdentity = AccountIdentity.fromEmailAddress( "test@test.com" )
        val invitation = StudyInvitation.empty()
        participationService.addParticipation( studyDeploymentId, setOf( deviceRoleName ), emailIdentity, invitation )

        val differentInvitation = StudyInvitation( "Different", "New description" )
        assertFailsWith<IllegalStateException>
        {
            participationService.addParticipation( studyDeploymentId, setOf( deviceRoleName ), emailIdentity, differentInvitation )
        }
    }

    @Test
    fun addParticipation_fails_for_unknown_studyDeploymentId() = runSuspendTest {
        val (participationService, _, _) = createService()

        val identity = AccountIdentity.fromUsername( "test" )
        assertFailsWith<IllegalArgumentException>
        {
            participationService.addParticipation( unknownId, setOf( "Device" ), identity, StudyInvitation.empty() )
        }
    }

    @Test
    fun addParticipation_fails_for_unknown_deviceRoleNames() = runSuspendTest {
        val (participationService, deploymentService, _) = createService()
        val studyDeploymentId = addTestDeployment( deploymentService )
        val emailIdentity = AccountIdentity.fromEmailAddress( "test@test.com" )
        val invitation = StudyInvitation.empty()

        assertFailsWith<IllegalArgumentException>
        {
            participationService.addParticipation( studyDeploymentId, setOf( "Wrong device" ), emailIdentity, invitation )
        }
    }

    @Test
    fun addParticipation_and_retrieving_invitation_succeeds() = runSuspendTest {
        val (participationService, deploymentService, accountService) = createService()
        val studyDeploymentId = addTestDeployment( deploymentService )
        val identity = AccountIdentity.fromEmailAddress( "test@test.com" )
        val invitation = StudyInvitation( "Test study", "description", "Custom data" )

        val participation = participationService.addParticipation( studyDeploymentId, setOf( deviceRoleName ), identity, invitation )
        val account = accountService.findAccount( identity )
        assertNotNull( account )
        val retrievedInvitations = participationService.getActiveParticipationInvitations( account.id )
        val expectedDeviceInvitation = ActiveParticipationInvitation.DeviceInvitation( deviceRoleName, false )
        assertEquals( ActiveParticipationInvitation( participation, invitation, setOf( expectedDeviceInvitation ) ), retrievedInvitations.single() )
    }


    private val deviceRoleName: String = "Master"

    /**
     * Add a test deployment to [deploymentService] for a protocol with a single master device with [deviceRoleName].
     */
    private suspend fun addTestDeployment( deploymentService: DeploymentService ): UUID
    {
        val protocol = createSingleMasterDeviceProtocol( deviceRoleName )
        val snapshot = protocol.getSnapshot()
        val status = deploymentService.createStudyDeployment( snapshot )

        return status.studyDeploymentId
    }
}
