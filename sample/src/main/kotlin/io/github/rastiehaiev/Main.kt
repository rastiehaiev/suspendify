package io.github.rastiehaiev

import io.github.rastiehaiev.repository.CompanyRepository
import io.github.rastiehaiev.repository.PositionRepository
import io.github.rastiehaiev.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.io.println

fun main() {
    runBlocking {
        val companyRepository = CompanyRepository()
        println(companyRepository)

        val userRepository = UserRepository()
        val userRepositorySuspendified = userRepository.suspendify(Dispatchers.IO)
        println(userRepositorySuspendified)

        val positionRepository = PositionRepository()
        val positionRepositorySuspendified = positionRepository.suspendify(Dispatchers.IO)
        println(positionRepositorySuspendified)

        val testRepo = TestRepo()
        val testRepoSuspendified = testRepo.suspendify(with = Dispatchers.IO)
        println(testRepoSuspendified)
        println(testRepoSuspendified.greeting())
    }
}

@Suspendifyable
@IrDump
class TestRepo {
    fun greeting(): String {
        return "Hello"
    }
}
