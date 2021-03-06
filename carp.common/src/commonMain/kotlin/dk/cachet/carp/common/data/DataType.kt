package dk.cachet.carp.common.data

import dk.cachet.carp.common.NamespacedId


/**
 * Defines a type of data which can be processed by the platform (e.g., measured/collected/uploaded).
 * This is used by the infrastructure to determine whether the requested data can be collected on a device,
 * how to upload it, how to process it in a secondary data stream, or how triggers can act on it.
 */
typealias DataType = NamespacedId
