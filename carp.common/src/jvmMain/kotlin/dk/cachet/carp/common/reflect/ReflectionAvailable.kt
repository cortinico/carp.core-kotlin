@file:Suppress( "MatchingDeclarationName" )

package dk.cachet.carp.common.reflect

import kotlin.reflect.KClass


@PublishedApi
internal actual object Reflection
{
    actual val isReflectionAvailable: Boolean =
        try
        {
            Int::class.supertypes // Accessing 'supertypes' fails if `kotlin.reflect` is not loaded.
            true
        }
        catch ( e: KotlinReflectionNotSupportedError ) { false }


    @PublishedApi
    internal actual inline fun <reified T> extendsType( klass: KClass<*> ): Boolean
    {
        val baseTypes = klass.supertypes.map { it.classifier as KClass<*> }
        return baseTypes.contains( T::class )
    }
}
