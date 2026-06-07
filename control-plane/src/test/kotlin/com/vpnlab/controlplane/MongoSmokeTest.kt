package com.vpnlab.controlplane

import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
@Testcontainers
class MongoSmokeTest {

    companion object {
        @Container
        @JvmStatic
        val mongo: MongoDBContainer = MongoDBContainer("mongo:8.0")

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri") { mongo.replicaSetUrl }
        }
    }

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    @Test
    fun `mongo template is wired and can run a ping command`() {
        val ok = mongoTemplate.db.runCommand(Document("ping", 1)).getDouble("ok")
        assertThat(ok).isEqualTo(1.0)
    }

    @Test
    fun `mongo can insert and retrieve a document`() {
        val coll = mongoTemplate.db.getCollection("smoke")
        coll.drop()
        coll.insertOne(Document("k", "v"))
        val found = coll.find(Document("k", "v")).first()
        assertThat(found).isNotNull
        assertThat(found?.getString("k")).isEqualTo("v")
        coll.drop()
    }
}
