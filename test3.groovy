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

def getProductWorkflowChannelUrl() {

    def content = libraryResource('pipeline-global-config/workflow-urls.properties')
    def props = readProperties text: content

    def buildUrl = env.BUILD_URL ?: ""

    // MATCH API-PRODUCTS
    def matcher = buildUrl =~ /\/job\/API-Products\/job\/([^\/]+)/

    def productName = ""

    if (matcher.find()) {
        productName = matcher.group(1)
        println "INFO: API-Products detected → ${productName}"
    } 
    else {

        // -------------------------------------------
        // ADDING COMMON-FRAMEWORK (SUBFOLDER) SUPPORT
        // -------------------------------------------
        def cfSubFolder = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)\/job\//
        if (cfSubFolder.find()) {
            productName = "Common-Framework"
            println "INFO: Common-Framework detected (subfolder)"
        }

        // -------------------------------------------
        // ADDING COMMON-FRAMEWORK (DIRECT JOB) SUPPORT
        // -------------------------------------------
        if (!productName) {
            def cfDirect = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)\//
            if (cfDirect.find()) {
                productName = "Common-Framework"
                println "INFO: Common-Framework detected (direct)"
            }
        }

        // -------------------------------------------
        // NOTHING MATCHED
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


