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
    }
}
