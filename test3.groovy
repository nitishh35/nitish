workinhg code for the devops chnanel and fortify chnnael

def call() {
    def envProperty = loadEnvironmentProperties()

    def devopsWorkFlowUrl      = envProperty.devops_workflow_url
    def fortifyWorkFlowUrl     = envProperty.fortify_workflow_url
    def fortifyChannelEmails   = envProperty.fortify_channel_emails_json
    def emailAddressList       = envProperty.to_email_address_list

    def pipelineUrl = env.BUILD_URL ?: ""

    // ---------------------------
    // Build DevOps JSON
    // ---------------------------
    def devOpsJson = createEmailJson(emailAddressList)
    devOpsJson.put("pipelineUrl", pipelineUrl)

    def devOpsBody = groovy.json.JsonOutput.toJson(devOpsJson)
    println "INFO: DevOps JSON to send: ${devOpsBody}"

    // Call DevOps Teams
    def devOpsResponse = sh(
        script: """
            curl -s -o /dev/null -w "%{http_code}" \\
                 -H "Content-Type: application/json" \\
                 -H "Accept: application/json" \\
                 -X POST "${devopsWorkFlowUrl}" \\
                 -d '${devOpsBody.replace("'", "'\\\\''")}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: DevOps Teams channel response code: ${devOpsResponse}"

    // ---------------------------
    // Build Fortify JSON
    // ---------------------------
    def fortifyJson = new groovy.json.JsonSlurper().parseText(fortifyChannelEmails)
    fortifyJson.put("pipelineUrl", pipelineUrl)

    def fortifyBody = groovy.json.JsonOutput.toJson(fortifyJson)
    println "INFO: Fortify JSON to send: ${fortifyBody}"

    // Call Fortify Teams
    def fortifyResponse = sh(
        script: """
            curl -s -o /dev/null -w "%{http_code}" \\
                 -H "Content-Type: application/json" \\
                 -H "Accept: application/json" \\
                 -X POST "${fortifyWorkFlowUrl}" \\
                 -d '${fortifyBody.replace("'", "'\\\\''")}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: Fortify Teams channel response code: ${fortifyResponse}"
}



def createEmailJson(def addressList) {
    def emails = addressList.split(",").collect { it.trim() }
    def map = [:]

    emails.eachWithIndex { email, index ->
        map["email${index + 1}"] = email
    }

    return map
}

    ---------------------------------------------
        ==============================================


       import groovy.json.JsonOutput

def call() {
    try {

        def buildStatus = currentBuild.result
        def stageName = "NA"

        if (currentBuild.result?.equalsIgnoreCase("FAILURE")) {
            stageName = env.failedStage
        }

        // Get who triggered the build
        def userInfo = getBuildTriggeredUserDetails()
        def buildTriggeredUserEmailId = userInfo.userEmail
        def buildTriggeredUserName = userInfo.userName

        // Skip service accounts (original logic preserved)
        if (buildTriggeredUserName?.startsWith('SVC-APP-RLCT')) {
            println "INFO: Notification suppressed for service account user: ${buildTriggeredUserName}"
            return
        }

        // Build JSON payload
        def buildData = [:]
        buildData["pipelineURL"]      = env.BUILD_URL
        buildData["triggerdBy"]       = buildTriggeredUserName
        buildData["triggerdByEmail"]  = buildTriggeredUserEmailId
        buildData["status"]           = buildStatus

        if (buildStatus == "FAILURE") {
            buildData["stage"] = stageName
        } else if (buildStatus in ["SUCCESS", "ABORTED", "UNSTABLE"]) {
            buildData["stage"] = "NA"
        }

        def buildDataJson = JsonOutput.toJson(buildData)

        // Resolve the Teams webhook
        def channelUrl = getProductWorkflowChannelUrl()
        if (channelUrl) {
            notifyTeam(buildDataJson, channelUrl)
        } else {
            println "WARN: No channel URL resolved; skipping notifyTeam()"
        }

    } catch (Exception e) {
        println "ERROR: Failed to send Teams notification: ${e.message}"
    }
}

///////////////////////////////////////////////////////////////////////////
//  DYNAMIC PRODUCT → WEBHOOK RESOLUTION
///////////////////////////////////////////////////////////////////////////

def getProductWorkflowChannelUrl() {

    def content = libraryResource('pipeline-global-config/workflow-urls.properties')
    def props   = readProperties text: content
    def buildUrl = env.BUILD_URL ?: ""

    println "\nINFO: Resolving Teams webhook for Build URL:"
    println buildUrl

    ///////////////////////////////////////////////////////////////////////////
    // 1) API-PRODUCTS (UNCHANGED)
    ///////////////////////////////////////////////////////////////////////////
    def apiMatcher = buildUrl =~ /\/job\/API-Products\/job\/([^\/]+)\//
    if (apiMatcher.find()) {

        def productName = apiMatcher.group(1)
        println "INFO: Detected API-Products job → ${productName}"

        def channelUrl = props[productName]
        if (channelUrl) {
            return channelUrl
        }

        println "WARN: No webhook found for API-Products folder '${productName}'"
        return null
    }

    ///////////////////////////////////////////////////////////////////////////
    // 2) COMMON-FRAMEWORK (ENHANCED WITH DYNAMIC SUBFOLDER SUPPORT)
    ///////////////////////////////////////////////////////////////////////////
    def cfMatcher = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)\/job\/([^\/]+)\//
    if (cfMatcher.find()) {

        def subfolder = cfMatcher.group(1)
        def jobName   = cfMatcher.group(2)

        println "INFO: Detected Common-Framework job"
        println "INFO:   Subfolder = ${subfolder}"
        println "INFO:   Job       = ${jobName}"

        // Try dynamic per-subfolder key: Common-Framework.<subfolder>
        def subKey = "Common-Framework.${subfolder}"

        if (props[subKey]) {
            println "INFO: Using subfolder-specific webhook → ${subKey}"
            return props[subKey]
        }

        // Fallback to default Common-Framework channel
        if (props["Common-Framework"]) {
            println "INFO: Using default Common-Framework webhook"
            return props["Common-Framework"]
        }

        println "WARN: No webhook configured for '${subKey}' or 'Common-Framework'"
        return null
    }

    ///////////////////////////////////////////////////////////////////////////
    // 3) NOTHING MATCHED → SKIP ALERTS
    ///////////////////////////////////////////////////////////////////////////
    println "WARN: Did not match API-Products or Common-Framework structure; skipping notifications."
    return null
}

///////////////////////////////////////////////////////////////////////////
//  CURL HANDLER (ORIGINAL LOGIC PRESERVED)
///////////////////////////////////////////////////////////////////////////

def notifyTeam(def buildDataJson, def channelUrl) {

    // Make JSON shell-safe
    def safePayload = buildDataJson.replace("'", "'\"'\"'")

    def responseCode = sh(
        script: """
            curl -k -w '%{http_code}' \
                 -H 'Content-Type: application/json' \
                 -H 'Accept: application/json' \
                 -X POST '${channelUrl}' \
                 -d '${safePayload}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: Teams Webhook Response Code: ${responseCode}"
    return responseCode
}
