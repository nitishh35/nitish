sendTeamNotification.groovy)
import groovy.json.JsonOutput

def call() {
    try {

        // --------------------------
        // 1. Resolve build status
        // --------------------------
        String buildStatus = currentBuild.result ?: "SUCCESS"
        String stageName   = env.failedStage ?: "NA"
        String buildUrl    = env.BUILD_URL ?: ""

        // --------------------------
        // 2. Resolve triggering user
        // --------------------------
        def userInfo = getBuildTriggeredUserDetails()
        String triggeredUserName  = userInfo?.userName ?: "Unknown"
        String triggeredUserEmail = userInfo?.userEmail ?: "Unknown"

        // Suppress notifications for service account
        if (triggeredUserName.equalsIgnoreCase("SVC-APP-RLCT")) {
            println "INFO: Notification suppressed for service account user: ${triggeredUserName}"
            return
        }

        // --------------------------
        // 3. Build notification JSON
        // --------------------------
        Map buildData = [:]
        buildData["pipelineURL"]     = buildUrl
        buildData["triggeredBy"]     = triggeredUserName
        buildData["triggeredByEmail"]= triggeredUserEmail
        buildData["status"]          = buildStatus
        buildData["stage"]           = stageName

        String buildDataJson = JsonOutput.toJson(buildData)

        // --------------------------
        // 4. Resolve Teams channel
        // --------------------------
        String channelUrl = getProductWorkflowChannelUrl(buildUrl)

        if (!channelUrl) {
            println "WARN: No valid Teams channel found. Skipping notification."
            return
        }

        // --------------------------
        // 5. Send Teams Notification
        // --------------------------
        notifyTeam(buildDataJson, channelUrl)

    } catch (Exception e) {
        println "ERROR: sendTeamNotification failed: ${e.message}"
    }
}




// ======================================================================
// HELPERS
// ======================================================================

// Extract Product/Capability folder name from Jenkins BUILD_URL
def getProductWorkflowChannelUrl(String buildUrl) {

    try {
        def content = libraryResource('pipeline-global-config/workflow-urls.properties')
        def props   = readProperties(text: content)

        /*
            Expected URL pattern:
            https://jenkins/job/API-Products/job/Payments/job/buildNumber/

            This regex extracts "Payments"
        */
        def matcher = buildUrl =~ /job\/([^\/]+)\/job\/([^\/]+)\//
        String folderName = null

        if (matcher.find()) {
            folderName = matcher.group(2)
        } else {
            println "WARN: Could not derive folder name from URL: ${buildUrl}"
            return null
        }

        String channelUrl = props[folderName]

        if (!channelUrl) {
            println "WARN: No Teams webhook mapped to folder '${folderName}'"
            return null
        }

        return channelUrl

    } catch (Exception e) {
        println "ERROR: Failed extracting Teams channel: ${e.message}"
        return null
    }
}



// ======================================================================
// Send Teams notification via curl
// ======================================================================
def notifyTeam(String buildDataJson, String channelUrl) {

    try {
        String sanitizedJson = buildDataJson.replace("'", "'\\''")

        def responseCode = sh(
            script: """
                curl -k -w '%{http_code}' \
                -H 'Content-Type: application/json' \
                -H 'Accept: application/json' \
                -X POST '${channelUrl}' \
                -d '${sanitizedJson}'
            """,
            returnStdout: true
        ).trim()

        println "INFO: Teams notification HTTP response code: ${responseCode}"

    } catch (Exception ex) {
        println "ERROR: Teams notification failed: ${ex.message}"
    }
}
