package io.github.rastiehaiev

import io.github.rastiehaiev.repository.CompanyRepository
import io.github.rastiehaiev.repository.PositionRepository
import io.github.rastiehaiev.repository.SettingsEntity
import io.github.rastiehaiev.repository.SettingsRepository
import io.github.rastiehaiev.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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

        val settingsRepository = SettingsRepositoryImpl()
        val settingsRepositorySuspendified = settingsRepository.suspendify(with = Dispatchers.IO)
        println(settingsRepositorySuspendified)
        println(settingsRepositorySuspendified.getSetting(id = "123"))
    }
}

class SettingsRepositoryImpl: SettingsRepository {

    override fun getSetting(id: String): SettingsEntity = SettingsEntity(id)
}
