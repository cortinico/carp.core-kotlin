package dk.cachet.carp.detekt.extensions.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.DetektVisitor
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getReferenceTargets
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.source.getPsi


// TODO: In case annotation class cannot be found in bindingContext, report an error.
class VerifyImmutable( private val immutableAnnotation: String ) : Rule()
{
    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "Classes or classes extending from classes with an @Immutable annotation applied to them should be data classes, " +
        "may not contain mutable properties, and may only contain basic types and other Immutable properties.",
        Debt.TWENTY_MINS
    )

    // TODO: Cache already linted types as mutable or immutable.
    private val isTypeImmutableCache: MutableMap<String, Boolean> = mutableMapOf(
        "kotlin.Int" to true
    )


    override fun visitClassOrObject( classOrObject: KtClassOrObject )
    {
        val immutableVisitor = ImmutableVisitor( bindingContext )
        classOrObject.accept( immutableVisitor )
        if ( immutableVisitor.shouldBeImmutable )
        {
            val implementationVisitor = ImmutableImplementationVisitor( bindingContext )
            classOrObject.accept( implementationVisitor )
            if ( !implementationVisitor.isImmutable )
            {
                implementationVisitor.mutableEntities.forEach {
                    val message = "${classOrObject.name} is not immutable due to: ${it.second}"
                    report( CodeSmell( issue, it.first, message ) )
                }
            }
        }

        super.visitClassOrObject( classOrObject )
    }


    /**
     * Determines whether or not the class needs to be immutable.
     */
    internal inner class ImmutableVisitor( private val bindingContext: BindingContext ) : DetektVisitor()
    {
        var shouldBeImmutable: Boolean = false
            private set


        override fun visitClassOrObject( classOrObject: KtClassOrObject )
        {
            // Verify whether the immutable annotation is applied to the base class.
            shouldBeImmutable = classOrObject.annotationEntries
                .any {
                    val type = it.typeReference?.typeElement as KtUserType
                    val name = type.referenceExpression
                        ?.getReferenceTargets( bindingContext )
                        ?.filterIsInstance<ClassConstructorDescriptor>()?.firstOrNull()
                        ?.constructedClass?.fqNameSafe?.asString()
                    name == immutableAnnotation
                }

            // Recursively verify whether the next base class might have the immutable annotation.
            if ( !shouldBeImmutable )
            {
                classOrObject.superTypeListEntries
                    .map { it.typeAsUserType?.referenceExpression?.getResolvedCall( bindingContext )?.resultingDescriptor }
                    .filterIsInstance<ClassConstructorDescriptor>()
                    .map { it.constructedClass.source.getPsi() as KtClassOrObject }
                    .forEach { it.accept( this ) }
            }
        }
    }

    /**
     * Determines for a class which needs to be immutable whether the implementation is immutable.
     */
    internal inner class ImmutableImplementationVisitor( private val bindingContext: BindingContext ) : DetektVisitor()
    {
        private val _mutableEntities: MutableList<Pair<Entity, String>> = mutableListOf()
        val mutableEntities: List<Pair<Entity, String>> = _mutableEntities

        val isImmutable: Boolean get() = _mutableEntities.isEmpty()
        var isVisitingInner: Boolean = false

        override fun visitClassOrObject( classOrObject: KtClassOrObject )
        {
            // Do not visit inner classes within the original one that is visited; they are analyzed separately.
            if ( isVisitingInner ) return
            isVisitingInner = true

            val klass = classOrObject as? KtClass
            if ( klass != null )
            {
                // Final immutable classes need to be data classes. It does not make sense NOT to make them data classes.
                if ( !klass.isAbstract() && !klass.isData() )
                {
                    _mutableEntities.add(
                        Entity.from( klass ) to
                        "Immutable types need to be data classes." )
                }
            }

            super.visitClassOrObject( classOrObject )
        }

        override fun visitPrimaryConstructor( constructor: KtPrimaryConstructor )
        {
            val properties = constructor.valueParameters
                .filter { it.valOrVarKeyword != null }

            // Verify whether any properties in the constructor are defined as var.
            if ( properties.any { it.isMutable } )
            {
                _mutableEntities.add(
                    Entity.from( constructor ) to
                    "Immutable types may not contain var constructor parameters." )
            }

            // Verify whether any of the property types in the constructor are not immutable.
            for ( property in properties )
            {
                val userType = property.typeReference?.typeElement as KtUserType
                verifyType( userType, property )
            }

            super.visitPrimaryConstructor( constructor )
        }

        override fun visitProperty( property: KtProperty )
        {
            // Verify whether the property is defined as var.
            if ( property.isVar )
            {
                _mutableEntities.add(
                    Entity.from( property ) to
                    "Immutable types may not contain var properties." )
            }

            // Verify whether the property type is immutable.
            val userType = property.typeReference?.typeElement as KtUserType
            verifyType( userType, property )

            super.visitProperty( property )
        }

        private fun verifyType( type: KtUserType, locationUsed: PsiElement )
        {
            val descriptor = getDescriptor( type )
            val klazz = getKlazz( descriptor )
            val name = descriptor.fqNameSafe.asString()

            if ( name !in isTypeImmutableCache )
            {
                // In case the type name is not known and source cannot be verified, report.
                if ( klazz == null )
                {
                    _mutableEntities.add(
                        Entity.from( locationUsed ) to
                        "Could not verify whether property of type '$name' is immutable." )
                }
                // Recursively verify the type is immutable.
                else
                {
                    val isImmutableVisitor = ImmutableImplementationVisitor( bindingContext )
                    klazz.accept( isImmutableVisitor )
                    if ( !isImmutableVisitor.isImmutable )
                    {
                        _mutableEntities.add(
                            Entity.from( locationUsed ) to
                            "Type '$name' is not immutable." )
                    }
                }
            }
        }

        private fun getDescriptor( type: KtUserType ): DeclarationDescriptorWithSource
        {
            return type.referenceExpression
                // TODO: What if there are more or no reference targets?
                ?.getReferenceTargets( bindingContext )?.first() as DeclarationDescriptorWithSource
        }

        private fun getKlazz( descriptor: DeclarationDescriptorWithSource ): KtClassOrObject?
        {
            return when ( val sourceElement = descriptor.source.getPsi() )
            {
                null -> null
                is KtClassOrObject -> sourceElement
                is KtTypeAlias ->
                {
                    val aliasedType = sourceElement.getTypeReference()?.typeElement as KtUserType
                    val aliasedTypeDescriptor = getDescriptor( aliasedType )
                    getKlazz( aliasedTypeDescriptor )
                }
                else -> throw UnsupportedOperationException( "VerifyImmutable does not support analyzing $sourceElement." )
            }
        }
    }
}
