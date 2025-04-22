package io.github.rastiehaiev

import io.github.rastiehaiev.repository.CompanyRepository
import io.github.rastiehaiev.repository.UserRepository
import io.github.rastiehaiev.repository.PositionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val userRepository = UserRepository()
        val userRepositorySuspendified = userRepository.suspendify(Dispatchers.IO)
        println(userRepositorySuspendified)

        println(userRepositorySuspendified.findById("1"))

        val companyRepository = CompanyRepository()
        val positionRepository = PositionRepository()
        val positionRepositorySuspendified = positionRepository.suspendify(Dispatchers.IO)
        println(positionRepositorySuspendified)

        val testRepo = TestRepo()
        val testRepoSuspendified = testRepo.suspendify(Dispatchers.IO)

        testRepoSuspendified.hello()
        testRepoSuspendified.hey("Hola!")
        val message = testRepoSuspendified.heyAndReturn("Hola, ", "Roman!")
        println(message)
    }
}

@Suspendify
class TestRepo {
    fun hello() = println("hello")

    fun greeting(): String {
        return "Hello"
    }

    fun someNumber(number: Int): Int = number + 1

    fun hey() {
        println("Hey")
    }

    fun hey(message: String) {
        println(message)
    }

    fun heyAndReturn(message: String, suffix: String): String {
        val newMessage = "$message $suffix"
        println(newMessage)
        return newMessage
    }
}
