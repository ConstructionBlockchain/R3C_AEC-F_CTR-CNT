package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.PartyAndAmount
import java.lang.IllegalStateException
import java.security.PublicKey
import java.util.*

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
class PayFlow(private val linearId: UniqueIdentifier, private val milestoneReference: String) : FlowLogic<UniqueIdentifier>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): UniqueIdentifier {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                linearId = listOf(linearId))
        val results = serviceHub.vaultService.queryBy<JobState>(queryCriteria)
        val inputStateAndRef = results.states.singleOrNull()
                ?: throw FlowException("There is no JobState with linear ID $linearId")
        val inputState = inputStateAndRef.state.data

        if (inputState.developer != ourIdentity) throw IllegalStateException("The developer must start this flow.")

        val milestoneIndex = findMilestone(inputState.milestones, milestoneReference)

        val milestoneToUpdate = inputState.milestones[milestoneIndex]
        val updatedMilestones = inputState.milestones.toMutableList()
        updatedMilestones[milestoneIndex] = milestoneToUpdate.copy(status = MilestoneStatus.PAID)

        val jobState = inputState.copy(milestones = updatedMilestones)

        val payCommand = Command(
                JobContract.Commands.PayMilestone(milestoneIndex),
                ourIdentity.owningKey
        )

        val transactionBuilder = TransactionBuilder(inputStateAndRef.state.notary)
                .addInputState(inputStateAndRef)
                .addOutputState(jobState, JobContract.ID)
                .addCommand(payCommand)

        val contractor = inputState.contractor

        val (_, cashSigningKeys) = Cash.generateSpend(serviceHub, transactionBuilder, milestoneToUpdate.amount, serviceHub.myInfo.legalIdentitiesAndCerts.first(), contractor)

        transactionBuilder.verify(serviceHub)

        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, cashSigningKeys + ourIdentity.owningKey)

        subFlow(FinalityFlow(signedTransaction))

        return jobState.linearId
    }

    private fun findMilestone(milestones : List<Milestone>, milestoneReference :  String) : Int {
        val milestoneResult =  milestones.filter{ milestone -> milestone.reference == milestoneReference }

        if(milestoneResult.isEmpty()){
            throw IllegalStateException("Cannot find Milestone with reference [".plus(milestoneReference).plus("]"))
        }

        return milestones.indexOf(milestoneResult[0])
    }


}