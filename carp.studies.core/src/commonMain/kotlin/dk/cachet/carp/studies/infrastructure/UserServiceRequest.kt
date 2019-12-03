package dk.cachet.carp.studies.infrastructure

import dk.cachet.carp.common.EmailAddress
import dk.cachet.carp.common.UUID
import dk.cachet.carp.common.ddd.createServiceInvoker
import dk.cachet.carp.common.ddd.createServiceInvokerOverloaded
import dk.cachet.carp.common.ddd.ServiceInvoker
import dk.cachet.carp.studies.application.UserService
import dk.cachet.carp.studies.domain.users.Account
import dk.cachet.carp.studies.domain.users.Participant
import dk.cachet.carp.studies.domain.users.Username
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable


/**
 * Serializable application service requests to [UserService] which can be executed on demand.
 */
@Polymorphic
@Serializable
sealed class UserServiceRequest
{
    @Serializable
    @Suppress( "RemoveExplicitTypeArguments" ) // Needed for JS build to work.
    data class CreateAccountWithUsername( val username: Username ) :
        UserServiceRequest(),
        ServiceInvoker<UserService, Account>
            by createServiceInvokerOverloaded<UserService, @ParameterName( "username" ) Username, Account>(
                UserService::createAccount, "username", username )

    @Serializable
    @Suppress( "RemoveExplicitTypeArguments" ) // Needed for JS build to work.
    data class CreateAccountWithEmailAddress( val emailAddress: EmailAddress ) :
        UserServiceRequest(),
        ServiceInvoker<UserService, Unit>
            by createServiceInvokerOverloaded<UserService, @ParameterName( "emailAddress" ) EmailAddress, Unit>(
                UserService::createAccount, "emailAddress", emailAddress )

    @Serializable
    data class CreateParticipant( val studyId: UUID, val accountId: UUID ) :
        UserServiceRequest(),
        ServiceInvoker<UserService, Participant> by createServiceInvoker( UserService::createParticipant, studyId, accountId )

    @Serializable
    data class InviteParticipant( val studyId: UUID, val emailAddress: EmailAddress ) :
        UserServiceRequest(),
        ServiceInvoker<UserService, Participant> by createServiceInvoker( UserService::inviteParticipant, studyId, emailAddress )

    @Serializable
    data class GetParticipantsForStudy( val studyId: UUID ) :
        UserServiceRequest(),
        ServiceInvoker<UserService, List<Participant>> by createServiceInvoker( UserService::getParticipantsForStudy, studyId )
}