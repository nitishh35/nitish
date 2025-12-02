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



import groovy.json.JsonOutput

def call() {

    try {
        def buildStatus = currentBuild.result ?: "SUCCESS"
        def stageName   = env.failedStage ?: "NA"

        // Triggering user info
        def userInfo  = getBuildTriggeredUserDetails()
        def userEmail = userInfo.userEmail
        def userName  = userInfo.userName

        // Skip service accounts
        if (userName?.startsWith("SVC-")) {
            println "INFO: Notification suppressed for service account user: ${userName}"
            return
        }

        // Build notification payload
        def data = [
            pipelineURL     : env.BUILD_URL,
            triggerdBy      : userName,
            triggerdByEmail : userEmail,
            status          : buildStatus,
            stage           : (buildStatus == "FAILURE" ? stageName : "NA")
        ]

        def jsonPayload = JsonOutput.toJson(data)

        // Find webhook URL based on job folder
        def channelUrl = getProductWorkflowChannelUrl()

        if (!channelUrl) {
            println "WARN: No Teams webhook configured. Skipping notification."
            return
        }

        // Send Teams Notification
        notifyTeam(jsonPayload, channelUrl)

    } catch (Exception e) {
        println "ERROR: Failed to send Teams notification: ${e.message}"
    }
}

/////////////////////////////////////////////////////////////////////
// RESOLVE WEBHOOK BASED ON JOB PATH (API-Products & Common-Framework)
/////////////////////////////////////////////////////////////////////

def getProductWorkflowChannelUrl() {

    def content = libraryResource('pipeline-global-config/workflow-urls.properties')
    def props   = readProperties text: content
    def buildUrl = env.BUILD_URL ?: ""

    println "INFO: Resolving webhook for Build URL:"
    println buildUrl

    String productKey = null

    //-------------------------------------------------------------------
    // CASE 1 → API-Products jobs
    //-------------------------------------------------------------------
    def apiMatcher = buildUrl =~ /\/job\/API-Products\/job\/([^\/]+)\//
    if (apiMatcher.find()) {
        productKey = apiMatcher.group(1)
        println "INFO: API-Products detected → ${productKey}"
    }

    //-------------------------------------------------------------------
    // CASE 2 → Common-Framework (subfolder case)
    // /job/Common-Framework/job/<subfolder>/job/<job>
    //-------------------------------------------------------------------
    if (!productKey) {
        def cfMatcherSub = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)\/job\//
        if (cfMatcherSub.find()) {
            productKey = "Common-Framework"
            println "INFO: Common-Framework detected (subfolder)"
        }
    }

    //-------------------------------------------------------------------
    // CASE 3 → Common-Framework (direct job)
    // /job/Common-Framework/job/<job>
    //-------------------------------------------------------------------
    if (!productKey) {
        def cfMatcherDirect = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)\//
        if (cfMatcherDirect.find()) {
            productKey = "Common-Framework"
            println "INFO: Common-Framework detected (direct job)"
        }
    }

    //-------------------------------------------------------------------
    // No folder matched
    //-------------------------------------------------------------------
    if (!productKey) {
        println "WARN: No capability folder detected. Notification skipped."
        return null
    }

    //-------------------------------------------------------------------
    // Lookup webhook URL in workflow-urls.properties
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
// CURL NOTIFIER (Teams / Power Automate)
/////////////////////////////////////////////////////////////////////

def notifyTeam(String jsonPayload, String webhookUrl) {

    def safePayload = jsonPayload.replace("'", "'\"'\"'")

    def httpResponse = sh(
        script: """
            curl -s -k -w "%{http_code}" \
                -H 'Content-Type: application/json' \
                -X POST '${webhookUrl}' \
                -d '${safePayload}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: Teams webhook response code: ${httpResponse}"
}


