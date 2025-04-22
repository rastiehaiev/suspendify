package io.github.rastiehaiev.repository

import io.github.rastiehaiev.Suspendify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Suspendify
class PositionRepository {

    suspend fun findById(id: String) = withContext(Dispatchers.IO) {
        delay(1000)
        "Position $id"
    }

    suspend fun save(id: String) = withContext(Dispatchers.IO) {
        println("Saving $id")
    }

    fun update(id: String) = println("Updating $id")
}
