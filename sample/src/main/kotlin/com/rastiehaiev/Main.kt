package com.rastiehaiev

import io.github.rastiehaiev.IrDump
import io.github.rastiehaiev.Suspendify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@IrDump
fun main() = runBlocking {
    val repository = Repository("Hello world!")
    println(repository.find())

    val suspendifiedRepository = repository.suspendify(Dispatchers.IO)
    println(suspendifiedRepository)
    suspendifiedRepository.save("Hey you")
    println(suspendifiedRepository.find())
}

@IrDump
@Suspendify
class Repository(private val value: String) {
    fun find(): String = value

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
