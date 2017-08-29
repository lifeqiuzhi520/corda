package net.corda.node.services

import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.testing.contracts.DummyContract
import net.corda.core.identity.Party
import net.corda.testing.DUMMY_BANK_A
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.transpose
import net.corda.core.utilities.getOrThrow
import net.corda.core.transactions.TransactionBuilder
import net.corda.node.internal.AbstractNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.NodeBasedTest
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RaftNotaryServiceTests : NodeBasedTest() {
    private val notaryName = X500Name("CN=RAFT Notary Service,O=R3,OU=corda,L=London,C=GB")

    @Test
    fun `detect double spend`() {
        val (bankA) = listOf(
                startNode(DUMMY_BANK_A.name),
                startNotaryCluster(notaryName, 3).map { it.first() }
        ).transpose().getOrThrow()

        val notaryParty = bankA.services.networkMapCache.getNotary(notaryName)!!

        val inputState = issueState(bankA, notaryParty)

        val firstTxBuilder = TransactionBuilder(notaryParty).withItems(inputState)
        val firstSpendTx = bankA.services.signInitialTransaction(firstTxBuilder)

        val firstSpend = bankA.services.startFlow(NotaryFlow.Client(firstSpendTx))
        firstSpend.resultFuture.getOrThrow()

        val secondSpendBuilder = TransactionBuilder(notaryParty).withItems(inputState).run {
            val dummyState = DummyContract.SingleOwnerState(0, bankA.info.chooseIdentity())
            addOutputState(dummyState)
            this
        }
        val secondSpendTx = bankA.services.signInitialTransaction(secondSpendBuilder)
        val secondSpend = bankA.services.startFlow(NotaryFlow.Client(secondSpendTx))

        val ex = assertFailsWith(NotaryException::class) { secondSpend.resultFuture.getOrThrow() }
        val error = ex.error as NotaryError.Conflict
        assertEquals(error.txId, secondSpendTx.id)
    }

    private fun issueState(node: AbstractNode, notary: Party): StateAndRef<*> {
        return node.database.transaction {
            val builder = DummyContract.generateInitial(Random().nextInt(), notary, node.info.chooseIdentity().ref(0))
            val stx = node.services.signInitialTransaction(builder)
            node.services.recordTransactions(stx)
            StateAndRef(builder.outputStates().first(), StateRef(stx.id, 0))
        }
    }
}
