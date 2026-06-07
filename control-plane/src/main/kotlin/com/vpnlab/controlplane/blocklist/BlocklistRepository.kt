package com.vpnlab.controlplane.blocklist

import org.springframework.data.mongodb.repository.MongoRepository

interface BlocklistRepository : MongoRepository<BlockedDomain, String> {
    fun findAllByIsWildcard(isWildcard: Boolean): List<BlockedDomain>
}
