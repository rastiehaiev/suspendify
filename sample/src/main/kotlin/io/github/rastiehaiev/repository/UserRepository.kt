package io.github.rastiehaiev.repository

import io.github.rastiehaiev.Suspendify

@Suspendify
class UserRepository {

    fun findById(id: String) = "User $id"
}
