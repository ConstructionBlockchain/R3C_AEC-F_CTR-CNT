package com.template

import com.beust.klaxon.Klaxon
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.template.contracts.MilestoneExamples
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.MockNetwork
import org.junit.Test
import kotlin.test.assertEquals

class JobStateTest {
    private val network = MockNetwork(listOf("com.template", "net.corda.finance.contracts.asset"))
    private val partyA = network.createNode()
    private val partyB = network.createNode()
    private val gson = Gson()

    @Test
    fun `json test`() {
        val milestone = MilestoneExamples.fitWindowsMilestone()
        val milestones: MutableList<Milestone> = mutableListOf(milestone)
        val jobState = JobState(
                milestones = milestones,
                developer = partyA.info.chooseIdentity(),
                contractor = partyB.info.chooseIdentity(),
                allowPaymentOnAccount = true,
                contractAmount = 100000.0,
                retentionPercentage = 0.2)



        var toJson =  Klaxon().toJsonString(jobState)
        assertEquals(true, true)

        val json =  "{\"contractor\":\"O=Mock Company 2, L=London, C=GB\",\"developer\":\"O=Mock Company 1, L=London, C=GB\"," +
                "\"milestones\":[{\"amount\":\"10000\",\"description\":\"milestone1\",\"currency\":\"GBP\"}," +
                "{\"amount\":\"20000\",\"description\":\"milestone2\",\"currency\":\"GBP\"}]}"

        var fromJson = gson.fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)
        var milestoneMap : List<Map<String,String>> =  fromJson.get("milestones") as List<Map<String,String>>
        assertEquals(true, true)
    }


}