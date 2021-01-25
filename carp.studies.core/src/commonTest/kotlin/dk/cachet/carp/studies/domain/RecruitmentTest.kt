package dk.cachet.carp.studies.domain

import dk.cachet.carp.common.EmailAddress
import dk.cachet.carp.common.UUID
import dk.cachet.carp.common.users.EmailAccountIdentity
import dk.cachet.carp.deployment.domain.users.StudyInvitation
import dk.cachet.carp.protocols.infrastructure.test.createEmptyProtocol
import dk.cachet.carp.studies.domain.users.DeanonymizedParticipation
import kotlin.test.*


/**
 * Tests for [Recruitment].
 */
class RecruitmentTest
{
    @Test
    fun creating_recruitment_fromSnapshot_obtained_by_getSnapshot_is_the_same()
    {
        val recruitment = Recruitment( UUID.randomUUID() )
        val protocol = createEmptyProtocol()
        val invitation = StudyInvitation( "Test", "A study" )
        recruitment.readyForDeployment( protocol.getSnapshot(), invitation )
        recruitment.addParticipation( UUID.randomUUID(), DeanonymizedParticipation( UUID.randomUUID(), UUID.randomUUID() ) )

        val snapshot = recruitment.getSnapshot()
        val fromSnapshot = Recruitment.fromSnapshot( snapshot )

        assertEquals( recruitment.studyId, fromSnapshot.studyId )
        assertEquals( recruitment.studyProtocol, fromSnapshot.studyProtocol )
        assertEquals( recruitment.invitation, fromSnapshot.invitation )
        assertEquals( recruitment.participants, fromSnapshot.participants )
        assertEquals( recruitment.participations, fromSnapshot.participations )
    }

    @Test
    fun addParticipant_succeeds()
    {
        val recruitment = Recruitment( UUID.randomUUID() )

        val email = EmailAddress( "test@test.com" )
        val participant = recruitment.addParticipant( email )
        val participantEvents = recruitment.consumeEvents().filterIsInstance<Recruitment.Event.ParticipantAdded>()
        val retrievedParticipant = recruitment.participants

        assertEquals( EmailAccountIdentity( email ), participant.accountIdentity )
        assertEquals( participant, retrievedParticipant.single() )
        assertEquals( participant, participantEvents.single().participant )
    }

    @Suppress( "ReplaceAssertBooleanWithAssertEquality" )
    @Test
    fun addParticipant_twice_returns_same_participant()
    {
        val recruitment = Recruitment( UUID.randomUUID() )
        val email = EmailAddress( "test@test.com" )
        val p1 = recruitment.addParticipant( email )

        val p2 = recruitment.addParticipant( email )
        val participantEvents = recruitment.consumeEvents().filterIsInstance<Recruitment.Event.ParticipantAdded>()

        assertTrue( p1 == p2 )
        assertEquals( 1, participantEvents.size ) // Event should only be published for first participant.
    }

    @Test
    fun addParticipation_succeeds()
    {
        val recruitment = Recruitment( UUID.randomUUID() )
        val protocol = createEmptyProtocol()
        recruitment.readyForDeployment( protocol.getSnapshot(), StudyInvitation.empty() )

        assertTrue( recruitment.isReadyForDeployment )

        val studyDeploymentId = UUID.randomUUID()
        val participation = DeanonymizedParticipation( UUID.randomUUID(), UUID.randomUUID() )
        recruitment.addParticipation( studyDeploymentId, participation )
        assertEquals( Recruitment.Event.ParticipationAdded( studyDeploymentId, participation ), recruitment.consumeEvents().last() )
        assertEquals( participation, recruitment.getParticipations( studyDeploymentId ).single() )
    }

    @Test
    fun addParticipation_fails_when_study_protocol_not_locked_in()
    {
        val recruitment = Recruitment( UUID.randomUUID() )

        assertFalse( recruitment.isReadyForDeployment )

        val participation = DeanonymizedParticipation( UUID.randomUUID(), UUID.randomUUID() )
        val studyDeploymentId = UUID.randomUUID()
        assertFailsWith<IllegalStateException> { recruitment.addParticipation( studyDeploymentId, participation ) }
        val participationEvents = recruitment.consumeEvents().filterIsInstance<Recruitment.Event.ParticipationAdded>()
        assertEquals( 0, participationEvents.count() )
    }

    @Test
    fun getParticipations_fails_for_unknown_studyDeploymentId()
    {
        val recruitment = Recruitment( UUID.randomUUID() )

        val unknownId = UUID.randomUUID()
        assertFailsWith<IllegalArgumentException> { recruitment.getParticipations( unknownId ) }
    }
}
