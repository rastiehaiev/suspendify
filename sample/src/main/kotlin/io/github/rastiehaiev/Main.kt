package io.github.rastiehaiev

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val repository = Repository()
    val suspendifiedRepository = repository.suspendify(with = Dispatchers.IO)
    suspendifiedRepository.save("message")
}

@IrDump
@Suspendify
class Repository {
    fun find(): String = "Hello world!"

    fun save(value: String) {
        println("Saving $value")
    }
}
