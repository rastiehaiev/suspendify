package com.rastiehaiev

import io.github.rastiehaiev.IrDump
import io.github.rastiehaiev.asCoroutineFriendly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@IrDump
fun main() {
    val repository = Repository()
    val repositoryCoroutineFriendly = repository.asCoroutineFriendly()
}

@IrDump
class Repository {
    fun find(): String = "Hello world!"

    fun save(value: String) = Unit
}

@IrDump
class RepositoryCoroutineFriendly(private val repo: Repository) {
    suspend fun save(value: String) =
        withContext(Dispatchers.IO) {
            repo.save(value)
        }

    suspend fun find(): String =
        withContext(Dispatchers.IO) {
            repo.find()
        }
}
