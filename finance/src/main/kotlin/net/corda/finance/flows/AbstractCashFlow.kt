package net.corda.finance.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.NotaryException
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import java.util.*

/**
 * Initiates a flow that produces an Issue/Move or Exit Cash transaction.
 */
abstract class AbstractCashFlow<out T>(override val progressTracker: ProgressTracker) : FlowLogic<T>() {
    companion object {
        object PROPOSING_TX : ProgressTracker.Step("Proposing transaction")
        object GENERATING_ID : ProgressTracker.Step("Generating anonymous identities")
        object GENERATING_TX : ProgressTracker.Step("Generating transaction")
        object SIGNING_TX : ProgressTracker.Step("Signing transaction")
        object FINALISING_TX : ProgressTracker.Step("Finalising transaction")

        fun tracker() = ProgressTracker(GENERATING_ID, GENERATING_TX, SIGNING_TX, FINALISING_TX)
    }

    @Suspendable
    protected fun finaliseTx(participants: Set<Party>, tx: SignedTransaction, message: String) {
        try {
            subFlow(FinalityFlow(tx, participants))
        } catch (e: NotaryException) {
            throw CashException(message, e)
        }
    }

    /**
     * Combined signed transaction and identity lookup map, which is the resulting data from regular cash flows.
     * Specialised flows for unit tests differ from this.
     *
     * @param stx the signed transaction.
     * @param recipient the identity used for the other side of the transaction, where applicable (i.e. this is
     * null for exit transactions). For anonymous transactions this is the confidential identity generated for the
     * transaction, otherwise this is the well known identity.
     */
    @CordaSerializable
    data class Result(val stx: SignedTransaction, val recipient: AbstractParty?)

    abstract class AbstractRequest(val amount: Amount<Currency>)
}

class CashException(message: String, cause: Throwable?) : FlowException(message, cause) {
    constructor(message: String) : this(message, null)
}