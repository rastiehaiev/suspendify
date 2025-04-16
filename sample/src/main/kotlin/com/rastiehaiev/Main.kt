package com.rastiehaiev

import io.github.rastiehaiev.IrDump
import io.github.rastiehaiev.Suspendify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

fun main() = runBlocking {
    val repository = Repository()

    val suspendifiedRepository = repository.suspendify(Dispatchers.IO)
    suspendifiedRepository.save("message")

    Unit
}

@IrDump
@Suspendify
class Repository {
    fun find(): String = "Hello world!"

    fun save(value: String) {
        println("Saving $value")
    }
}

@IrDump
class RepositorySuspendified(
    private val delegate: Repository,
    private val dispatcher: CoroutineDispatcher,
) {
    suspend fun save(value: String) {
        withContext(dispatcher) {
            delegate.save(value)
        }
    }

    suspend fun find(): String =
        withContext(dispatcher) {
            delegate.find()
        }
}
