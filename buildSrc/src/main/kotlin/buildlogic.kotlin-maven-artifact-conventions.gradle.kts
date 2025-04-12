
import io.github.rastiehaiev.getDeployMetadata
import net.thebugmc.gradle.sonatypepublisher.PublishingType

plugins {
    id("buildlogic.kotlin-common-conventions")
    id("net.thebugmc.gradle.sonatype-central-portal-publisher")
}

private val metadata = project.getDeployMetadata()

val centralPortalUsername = findProperty("centralPortalUsername") as String?
val centralPortalPassword = findProperty("centralPortalPassword") as String?

val artifactIdString = project.findProperty("plugin.artifact.id") as String?
val descriptionString = project.findProperty("plugin.artifact.description") as String?

centralPortal {
    username = centralPortalUsername
    password = centralPortalPassword

    name = artifactIdString
    publishingType = PublishingType.AUTOMATIC

    pom {
        name.set(artifactIdString)
        description.set(descriptionString)
        url.set(metadata.githubUrl)
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set(metadata.developerId)
                name.set(metadata.developerName)
                email.set(metadata.developerEmail)
            }
        }
        scm {
            connection.set(metadata.scmConnection)
            developerConnection.set(metadata.scmConnection)
            url.set(metadata.scmUrl)
        }
        issueManagement {
            system.set(metadata.issueManagementSystem)
            url.set(metadata.issueManagementUrl)
        }
    }
}
