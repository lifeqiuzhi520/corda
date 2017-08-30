package net.corda.core.crypto.types

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
class MerkleTreeException(val reason: String) : Exception("Partial Merkle Tree exception. Reason: $reason")