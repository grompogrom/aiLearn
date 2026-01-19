package core.github

/**
 * Utility functions for working with GitHub URLs and links.
 */
object GithubUtils {
    /**
     * Parses a GitHub MR/PR link to extract owner, repo, and PR number.
     * Supports both GitHub PR format and GitLab MR format.
     * 
     * @param link The MR/PR link
     * @return Triple of (owner, repo, prNumber) or null if parsing fails
     */
    fun parseMrLink(link: String): Triple<String, String, String>? {
        // GitHub PR format: https://github.com/owner/repo/pull/123
        val githubPattern = Regex("""https?://github\.com/([^/]+)/([^/]+)/(?:pull|merge_requests)/(\d+)""")
        val githubMatch = githubPattern.find(link)
        if (githubMatch != null) {
            val (owner, repo, prNumber) = githubMatch.destructured
            return Triple(owner, repo, prNumber)
        }
        
        // GitLab MR format: https://gitlab.com/owner/repo/-/merge_requests/123
        val gitlabPattern = Regex("""https?://gitlab\.com/([^/]+)/([^/]+)/-/merge_requests/(\d+)""")
        val gitlabMatch = gitlabPattern.find(link)
        if (gitlabMatch != null) {
            val (owner, repo, prNumber) = gitlabMatch.destructured
            return Triple(owner, repo, prNumber)
        }
        
        return null
    }
}
