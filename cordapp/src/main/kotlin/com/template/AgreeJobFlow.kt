package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Agree the creation of a [JobState] representing a job organised by a developer and carried out by a [contractor].
 * The job is split into a set of [milestones].
 *
 * Should be run by the developer.
 *
 * @param milestones the milestones involved in the job.
 * @param contractor the contractor carrying out the job.
 * @param notaryToUse the notary to assign the output state to.
 */
@InitiatingFlow
@StartableByRPC
class AgreeJobFlow(val contractor: Party,
                   val contractAmount : Double,
                   val retentionPercentage : Double,
                   val allowPaymentOnAccount : Boolean,
                   val milestones: List<Milestone>,
                   val notaryToUse: Party) : FlowLogic<UniqueIdentifier>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): UniqueIdentifier {
        var jobState = JobState(developer = ourIdentity,
                contractor = contractor,
                contractAmount = contractAmount,
                retentionPercentage = retentionPercentage,
                allowPaymentOnAccount = allowPaymentOnAccount,
                milestones = milestones)

        val agreeJobCommand = Command(
                JobContract.Commands.AgreeJob(),
                listOf(ourIdentity.owningKey, contractor.owningKey))

        val transactionBuilder = TransactionBuilder(notaryToUse)
                .addOutputState(jobState, JobContract.ID)
                .addCommand(agreeJobCommand)

        transactionBuilder.verify(serviceHub)

        val partSignedTransaction =
                serviceHub.signInitialTransaction(transactionBuilder)

        val contractorSession = initiateFlow(contractor)
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(
                partSignedTransaction, listOf(contractorSession)))

        subFlow(FinalityFlow(fullySignedTransaction))

        return jobState.linearId
    }
}

@InitiatedBy(AgreeJobFlow::class)
class AgreeJobFlowResponder(val developerSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        class OurSignTransactionFlow : SignTransactionFlow(developerSession) {
            override fun checkTransaction(stx: SignedTransaction) {

            }
        }

        subFlow(OurSignTransactionFlow())
    }
}