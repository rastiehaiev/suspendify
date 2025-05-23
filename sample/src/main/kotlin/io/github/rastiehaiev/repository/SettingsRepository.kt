package io.github.rastiehaiev.repository

import io.github.rastiehaiev.Suspendifyable

@Suspendifyable
interface SettingsRepository {

    fun getSetting(id: String): SettingsEntity
}

data class SettingsEntity(val id: String)
