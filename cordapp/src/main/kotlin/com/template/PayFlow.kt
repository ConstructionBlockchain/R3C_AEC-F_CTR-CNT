package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.Cash
import java.lang.IllegalStateException

/**
 * Change the status of a [Milestone] in a [JobState] from [MilestoneStatus.ACCEPTED] to [MilestoneStatus.PAID].
 *
 * Should be run by the developer.
 *
 * @param linearId the [JobState] to update.
 * @param milestoneIndex the index of the [Milestone] to be updated in the [JobState].
 */
@InitiatingFlow
@StartableByRPC
class PayFlow(private val linearId: UniqueIdentifier, private val milestoneReference: String) : FlowLogic<JobState>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): JobState {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                linearId = listOf(linearId))
        val results = serviceHub.vaultService.queryBy<JobState>(queryCriteria)
        val inputStateAndRef = results.states.singleOrNull()
                ?: throw FlowException("There is no JobState with linear ID $linearId")
        val inputState = inputStateAndRef.state.data

        if (inputState.developer != ourIdentity) throw IllegalStateException("The developer must start this flow.")

        var (newJobState, amountToPay, milestoneIndex) = ContractManager.applyPayMilestone(milestoneReference, inputState)

        val payCommand = Command(
                JobContract.Commands.PayMilestone(milestoneIndex),
                ourIdentity.owningKey
        )

        val transactionBuilder = TransactionBuilder(inputStateAndRef.state.notary)
                .addInputState(inputStateAndRef)
                .addOutputState(newJobState, JobContract.ID)
                .addCommand(payCommand)

        val contractor = inputState.contractor

        val (_, cashSigningKeys) = Cash.generateSpend(serviceHub, transactionBuilder, amountToPay, serviceHub.myInfo.legalIdentitiesAndCerts.first(), contractor)

        transactionBuilder.verify(serviceHub)

        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, cashSigningKeys + ourIdentity.owningKey)

        subFlow(FinalityFlow(signedTransaction))

        return newJobState
    }

}