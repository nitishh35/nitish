def call() {
    def envProperty = loadEnvironmentProperties()

    def devopsWorkFlowUrl      = envProperty.devops_workflow_url
    def fortifyWorkFlowUrl     = envProperty.fortify_workflow_url
    def fortifyChannelEmails   = envProperty.fortify_channel_emails_json
    def emailAddressList       = envProperty.to_email_address_list

    def pipelineUrl = env.BUILD_URL ?: ""

    // ---------------------------
    // DevOps JSON
    // ---------------------------
    def devOpsJson = createEmailJson(emailAddressList)
    devOpsJson.put("pipelineUrl", pipelineUrl)

    def devOpsBody = groovy.json.JsonOutput.toJson(devOpsJson)
    println "INFO: DevOps JSON to send: ${devOpsBody}"

    def devOpsResponse = sh(
        script: """
            curl -s -o /dev/null -w "%{http_code}" \
                 -H "Content-Type: application/json" \
                 -H "Accept: application/json" \
                 -X POST "${devopsWorkFlowUrl}" \
                 -d '${devOpsBody.replace("'", "'\\\\''")}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: DevOps Teams channel response code: ${devOpsResponse}"

    // ---------------------------
    // Fortify JSON (JsonSlurper SAFE)
    // ---------------------------
    def fortifyString = fortifyChannelEmails?.trim() ?: ""

    // If Jenkins loads the value as '{"email1"..."}'
    if (fortifyString.startsWith("'") && fortifyString.endsWith("'")) {
        fortifyString = fortifyString.substring(1, fortifyString.length() - 1)
    }

    // Parse JSON safely
    def fortifyJson = new groovy.json.JsonSlurper().parseText(fortifyString)
    fortifyJson.put("pipelineUrl", pipelineUrl)

    def fortifyBody = groovy.json.JsonOutput.toJson(fortifyJson)
    println "INFO: Fortify JSON to send: ${fortifyBody}"

    def fortifyResponse = sh(
        script: """
            curl -s -o /dev/null -w "%{http_code}" \
                 -H "Content-Type: application/json" \
                 -H "Accept: application/json" \
                 -X POST "${fortifyWorkFlowUrl}" \
                 -d '${fortifyBody.replace("'", "'\\\\''")}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: Fortify Teams channel response code: ${fortifyResponse}"
}

===============================
    avoaid lazymap error

def call() {
    // Do NOT modify this — you requested to keep it as is
    def envProperty = loadEnvironmentProperties()

    def devopsWorkFlowUrl      = envProperty.devops_workflow_url
    def fortifyWorkFlowUrl     = envProperty.fortify_workflow_url
    def fortifyChannelEmails   = envProperty.fortify_channel_emails_json
    def emailAddressList       = envProperty.to_email_address_list

    def pipelineUrl = env.BUILD_URL ?: ""


    // ------------------------------------------------------------------
    // DEVOPS NOTIFICATION DISABLED — COMMENTED AS YOU REQUESTED
    // ------------------------------------------------------------------
    /*
    def devOpsJson = createEmailJson(emailAddressList)
    devOpsJson.put("pipelineUrl", pipelineUrl)

    def devOpsBody = groovy.json.JsonOutput.toJson(devOpsJson)
    println "INFO: DevOps JSON to send: ${devOpsBody}"

    def devOpsResponse = sh(
        script: """
            curl -s -o /dev/null -w "%{http_code}" \
                 -H "Content-Type: application/json" \
                 -H "Accept: application/json" \
                 -X POST "${devopsWorkFlowUrl}" \
                 -d '${devOpsBody.replace("'", "'\\\\''")}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: DevOps Teams channel response code: ${devOpsResponse}"
    */


    // ------------------------------------------------------------------
    // FORTIFY NOTIFICATION — SAFE FROM LazyMap ERRORS
    // ------------------------------------------------------------------

    // Convert LazyMap value → pure String (critical fix)
    def fortifyString = "${fortifyChannelEmails}".trim()

    // If Jenkins adds wrapping single quotes, remove them
    if (fortifyString.startsWith("'") && fortifyString.endsWith("'")) {
        fortifyString = fortifyString.substring(1, fortifyString.length() - 1)
    }

    // Safe JSON parsing (no LazyMap interaction)
    def fortifyJson = new groovy.json.JsonSlurper().parseText(fortifyString)

    // Add pipeline URL
    fortifyJson.put("pipelineUrl", pipelineUrl)

    def fortifyBody = groovy.json.JsonOutput.toJson(fortifyJson)
    println "INFO: Fortify JSON to send: ${fortifyBody}"

    def fortifyResponse = sh(
        script: """
            curl -s -o /dev/null -w "%{http_code}" \
                 -H "Content-Type: application/json" \
                 -H "Accept: application/json" \
                 -X POST "${fortifyWorkFlowUrl}" \
                 -d '${fortifyBody.replace("'", "'\\\\''")}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: Fortify Teams channel response code: ${fortifyResponse}"
}



// ------------------------------------------------------------------
// FUNCTION: Converts comma-separated emails → JSON map
// Used only for DevOps (which is now disabled)
// ------------------------------------------------------------------
def createEmailJson(def addressList) {
    def emails = addressList.split(",").collect { it.trim() }
    def map = [:]

    emails.eachWithIndex { email, index ->
        map["email${index + 1}"] = email
    }
    return map
}

        ==============================================


     sendteamsnotification for common framework

def call() {

    try {
        // Build status & failed stage
        def buildStatus = currentBuild.result ?: "SUCCESS"
        def stageName   = env.failedStage ?: "NA"

        // User who triggered the build
        def userInfo  = getBuildTriggeredUserDetails()
        def userEmail = userInfo?.userEmail ?: "NA"
        def userName  = userInfo?.userName ?: "UNKNOWN"

        // Skip service accounts
        if (userName?.startsWith("SVC-")) {
            println "INFO: Notification suppressed for service account user: ${userName}"
            return
        }

        // Build payload
        def data = [
            pipelineURL     : env.BUILD_URL,
            triggerdBy      : userName,
            triggerdByEmail : userEmail,
            status          : buildStatus,
            stage           : (buildStatus == "FAILURE" ? stageName : "NA")
        ]

        // Convert Map → JSON (Pipeline built-in)
        def jsonPayload = writeJSON returnText: true, json: data

        // Detect folder & get webhook
        def channelUrl = getProductWorkflowChannelUrl()

        if (!channelUrl) {
            println "WARN: No Teams webhook configured. Skipping notification."
            return
        }

        // Send notification
        notifyTeam(jsonPayload, channelUrl)

    } catch (Exception e) {
        println "ERROR: Failed to send Teams notification: ${e.message}"
    }
}

/////////////////////////////////////////////////////////////////////
// RESOLVE WEBHOOK BASED ON JOB LOCATION
/////////////////////////////////////////////////////////////////////

def getProductWorkflowChannelUrl() {

    def content  = libraryResource('pipeline-global-config/workflow-urls.properties')
    def props    = readProperties text: content
    def buildUrl = env.BUILD_URL ?: ""

    println "INFO: Resolving webhook for Build URL:"
    println buildUrl

    String productKey = null

    //-------------------------------------------------------------------
    // Case 1 → API-Products/<ProductFolder>/...
    //-------------------------------------------------------------------
    def apiMatch = buildUrl =~ /\/job\/API-Products\/job\/([^\/]+)\//
    if (apiMatch.find()) {
        productKey = apiMatch.group(1)
        println "INFO: API-Products detected → ${productKey}"
    }

    //-------------------------------------------------------------------
    // Case 2a → Common-Framework subfolder job
    //-------------------------------------------------------------------
    if (!productKey) {
        def cfSubMatch = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)\/job\//
        if (cfSubMatch.find()) {
            productKey = "Common-Framework"
            println "INFO: Common-Framework detected (subfolder)"
        }
    }

    //-------------------------------------------------------------------
    // Case 2b → Common-Framework direct job
    //-------------------------------------------------------------------
    if (!productKey) {
        def cfDirectMatch = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)\//
        if (cfDirectMatch.find()) {
            productKey = "Common-Framework"
            println "INFO: Common-Framework detected (direct)"
        }
    }

    //-------------------------------------------------------------------
    // No folder match
    //-------------------------------------------------------------------
    if (!productKey) {
        println "WARN: No capability folder detected. Notification skipped."
        return null
    }

    //-------------------------------------------------------------------
    // Look up webhook in properties file
    //-------------------------------------------------------------------
    def webhookUrl = props[productKey]

    if (!webhookUrl) {
        println "WARN: No webhook configured for key '${productKey}'"
        return null
    }

    println "INFO: Using webhook for key → ${productKey}"
    return webhookUrl
}

/////////////////////////////////////////////////////////////////////
// CURL NOTIFIER
/////////////////////////////////////////////////////////////////////

def notifyTeam(String jsonPayload, String webhookUrl) {

    def safePayload = jsonPayload.replace("'", "'\"'\"'")

    def httpResponse = sh(
        script: """
            curl -s -k -w "%{http_code}" \\
                -H 'Content-Type: application/json' \\
                -X POST '${webhookUrl}' \\
                -d '${safePayload}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: Teams webhook response code: ${httpResponse}"
}
------------------------------------------------------------------------------

    update code as per syf code for sendteamsnotification


    def call() {

    try {

        def buildStatus = currentBuild.result
        def stageName = "NA"

        if (currentBuild.result.equalsIgnoreCase("FAILURE")) {
            stageName = env.failedStage
        }

        def userInfo = getBuildTriggeredUserDetails()
        def buildTriggeredUserEmailId = userInfo.userEmail
        def buildTriggeredUserName = userInfo.userName

        if (buildTriggeredUserName == 'SVC-APP-RLCT') {
            println "INFO: Notification suppressed for service account user: ${buildTriggeredUserName}"
            return
        }

        def buildData = [:]
        buildData["pipelineURL"] = env.BUILD_URL
        buildData["triggerdBy"] = buildTriggeredUserName
        buildData["triggerdByEmail"] = buildTriggeredUserEmailId
        buildData["status"] = buildStatus

        if (buildStatus == "FAILURE") {
            buildData["stage"] = stageName
        } else if (buildStatus == "SUCCESS" || buildStatus == "ABORTED" || buildStatus == "UNSTABLE") {
            buildData["stage"] = "NA"
        }

        def buildDataJson = groovy.json.JsonOutput.toJson(buildData)

        def channelUrl = getProductWorkflowChannelUrl()

        if (channelUrl) {
            notifyTeam(buildDataJson, channelUrl)
        }

    } catch (Exception e) {
        println "ERROR: Failed to send Teams notification: ${e.message}"
    }
}

///////////////////////////////////////////////////////
// RESOLVE WEBHOOK URL
///////////////////////////////////////////////////////

def getProductWorkflowChannelUrl() {

    def content = libraryResource('pipeline-global-config/workflow-urls.properties')
    def props = readProperties text: content
    def buildUrl = env.BUILD_URL ?: ""

    // -------------------------
    // API-PRODUCTS MATCH
    // -------------------------
    def matcher = buildUrl =~ /\/job\/API-Products\/job\/([^\/]+)/
    def productName = ""

    if (matcher.find()) {
        productName = matcher.group(1)
        println "INFO: API-Products detected → ${productName}"
    } else {

        // -------------------------------------------
        // COMMON-FRAMEWORK (SUBFOLDER MATCH ADDED)
        // -------------------------------------------
        def cfSub = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)\/job\//
        if (cfSub.find()) {
            productName = "Common-Framework"
            println "INFO: Common-Framework detected (subfolder)"
        }

        // -------------------------------------------
        // COMMON-FRAMEWORK (DIRECT MATCH ADDED)
        // -------------------------------------------
        if (!productName) {
            def cfDirect = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)\//
            if (cfDirect.find()) {
                productName = "Common-Framework"
                println "INFO: Common-Framework detected (direct)"
            }
        }

        // -------------------------------------------
        // NO MATCH
        // -------------------------------------------
        if (!productName) {
            println "WARN: Skipping team notification for this build, unable to extract the capability folder name from the URL: ${buildUrl}"
            return null
        }
    }

    def channelUrl = props[productName]

    if (!channelUrl) {
        println "WARN: No webhook configured for key '${productName}'"
        return null
    }

    return channelUrl
}

///////////////////////////////////////////////////////
// CURL NOTIFICATION
///////////////////////////////////////////////////////

def notifyTeam(def buildDataJson, def channelUrl) {

    def responseCode = sh(
        script: """
            curl -k -w '%{http_code}' \
            -H 'Content-Type: application/json' \
            -H 'Accept: application/json' \
            -X POST '${channelUrl}' \
            -d '${buildDataJson.replace("'", "'\\''")}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: The received HTTP response code from DevOps teams channel curl request: ${responseCode}"
}

======================================

    getbuildtriggereduserdetails

def call() {

    def causes = currentBuild.getBuildCauses()

    // 1. User manually triggered the job
    def userCause = causes.find { it._class?.endsWith("UserIdCause") }
    if (userCause) {
        return [
            userName : userCause.userName ?: "UNKNOWN",
            userEmail: userCause.userEmail ?: "unknown"
        ]
    }

    // 2. Upstream job triggered this job
    def upstreamCause = causes.find { it._class?.endsWith("UpstreamCause") }
    if (upstreamCause) {

        // Some Jenkins controllers populate upstream user
        if (upstreamCause.upstreamUser) {
            return [
                userName : upstreamCause.upstreamUser,
                userEmail: "unknown"
            ]
        }

        // Otherwise return SYSTEM
        return [
            userName : "SYSTEM",
            userEmail: "system@local"
        ]
    }

    // 3. Timer / SCM / Unknown
    return [
        userName : "SYSTEM",
        userEmail: "system@local"
    ]
}
=====================

    Scripts not permitted to use method hudson.model.Cause$UpstreamCause getUpstreamRun
Scripts not permitted to use method hudson.model.User get
Scripts not permitted to use method hudson.tasks.Mailer$UserProperty getAddress
Scripts not permitted to use method hudson.model.Cause getShortDescription
====================


    method hudson.model.Cause$UpstreamCause getUpstreamRun
method hudson.model.Cause$UpstreamCause getUpstreamBuild
method hudson.model.Cause$UpstreamCause getUpstreamProject
method hudson.model.Cause getShortDescription
method hudson.model.User get
method hudson.tasks.Mailer$UserProperty getAddress
method hudson.model.User getDisplayName
method hudson.model.Cause$UserIdCause getUserId
method hudson.model.AbstractBuild getCause
