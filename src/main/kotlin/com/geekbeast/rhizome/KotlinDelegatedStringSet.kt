package com.geekbeast.rhizome

/**
 * @author Drew Bailey &lt;drew@openlattice.com&gt;
 */
class KotlinDelegatedStringSet(strings: Set<String>) : Set<String> by strings {
    override fun equals(other: Any?): Boolean {

        return if (other !is Set<*> ) {
            false
        } else {
            this.size == other.size && this.containsAll(other)
        }
    }

    /**
     * Consistent with [equals], which treats this as an unordered set: the hash is the
     * order-independent sum of element hashes, matching the [Set] contract.
     */
    override fun hashCode(): Int = this.sumOf { it.hashCode() }
}
