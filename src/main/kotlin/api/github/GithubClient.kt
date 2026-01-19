package api.github

import core.config.AppConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(GithubClient::class.java)

@Serializable
private data class CommentRequest(val body: String)

/**
 * Client for interacting with GitHub REST API.
 * 
 * @param config Application configuration containing GitHub token and timeout settings
 */
class GithubClient(
    private val config: AppConfig
) : AutoCloseable {
    
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMillis
        }
        install(ContentNegotiation) {
            json()
        }
    }
    
    /**
     * Gets the diff of a pull request from GitHub API.
     * 
     * Uses endpoint:
     *   GET https://api.github.com/repos/{owner}/{repo}/pulls/{prNumber}
     * with header:
     *   Accept: application/vnd.github.v3.diff
     * 
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber PR number
     * @return The diff as string, or null if failed
     */
    suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: String): String? {
        val token = config.githubToken
        if (token.isBlank()) {
            logger.warn("GitHub token is not configured")
            return null
        }

        val url = "https://api.github.com/repos/$owner/$repo/pulls/$prNumber"
        logger.info("Requesting PR diff from GitHub API: $url")

        return try {
            val response: HttpResponse = client.get(url) {
                header(HttpHeaders.Accept, "application/vnd.github.v3.diff")
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.UserAgent, "aiLearn")
            }

            logger.debug("GitHub API response status: ${response.status.value} ${response.status.description}")

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                logger.warn("GitHub API returned error ${response.status.value}: $body")
                null
            } else {
                val diff = response.bodyAsText()
                if (diff.isBlank()) {
                    logger.warn("GitHub API returned empty diff")
                    null
                } else {
                    logger.info("Successfully retrieved PR diff (length: ${diff.length})")
                    diff
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to get PR diff from GitHub API", e)
            null
        }
    }
    
    /**
     * Posts a comment to a pull request via GitHub API.
     * 
     * Uses endpoint:
     *   POST https://api.github.com/repos/{owner}/{repo}/issues/{issueNumber}/comments
     * 
     * Note: For PRs, the issue number is the same as the PR number.
     * 
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber PR number (same as issue number)
     * @param commentBody The comment text to post
     * @return true if successful, false otherwise
     */
    suspend fun postComment(owner: String, repo: String, prNumber: String, commentBody: String): Boolean {
        val token = config.githubToken
        if (token.isBlank()) {
            logger.warn("GitHub token is not configured")
            return false
        }

        val url = "https://api.github.com/repos/$owner/$repo/issues/$prNumber/comments"
        logger.info("Posting comment to PR via GitHub API: $url")

        return try {
            val commentRequest = CommentRequest(body = commentBody)
            val response: HttpResponse = client.post(url) {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.UserAgent, "aiLearn")
                header(HttpHeaders.ContentType, "application/json")
                setBody(commentRequest)
            }

            logger.debug("GitHub API response status: ${response.status.value} ${response.status.description}")

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                logger.warn("GitHub API returned error ${response.status.value}: $body")
                false
            } else {
                logger.info("Successfully posted comment to PR")
                true
            }
        } catch (e: Exception) {
            logger.error("Failed to post comment to PR via GitHub API", e)
            false
        }
    }
    
    override fun close() {
        logger.debug("Closing GitHub client")
        client.close()
        logger.info("GitHub client closed")
    }
}
