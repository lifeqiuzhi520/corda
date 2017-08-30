package net.corda.core.crypto.types

import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable

/**
 * A [SignableData] object is the packet actually signed.
 * It works as a wrapper over transaction id and signature metadata.
 *
 * @param txId transaction's id.
 * @param signatureMetadata meta data required.
 */
@CordaSerializable
data class SignableData(val txId: SecureHash, val signatureMetadata: SignatureMetadata)