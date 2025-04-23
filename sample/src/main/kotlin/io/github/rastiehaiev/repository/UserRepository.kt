package io.github.rastiehaiev.repository

import io.github.rastiehaiev.Suspendifyable

@Suspendifyable
class UserRepository {

    fun findById(id: String) = UserEntity(id)
}

data class UserEntity(val id: String)
