package com.rastiehaiev

import io.github.rastiehaiev.CoroutineFriendly
import io.github.rastiehaiev.IrDump
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@IrDump
fun main() = runBlocking {
    val repository = Repository("Hello world!")
    println(repository)
    println(repository.find())

    val coroutineFriendlyRepository1 = repository.asCoroutineFriendly(Dispatchers.IO)
    println(coroutineFriendlyRepository1.find())
    coroutineFriendlyRepository1.save("Hey you")
    println(coroutineFriendlyRepository1.find())

    Unit
}

@IrDump
@CoroutineFriendly
class Repository(private val value: String) {
    fun find(): String = value

    fun save(value: String) {
        println("Saving $value")
    }
}

@IrDump
class RepositoryCoroutineFriendly(
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
