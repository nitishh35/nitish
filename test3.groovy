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

        def buildStatus = currentBuild.result
        def stageName = env.failedStage ?: "NA"

        def userInfo = getBuildTriggeredUserDetails()
        def buildTriggeredUserEmailId = userInfo.userEmail
        def buildTriggeredUserName = userInfo.userName

        // Skip service account
        if (buildTriggeredUserName == 'SVC-APP-RLCT') {
            println "INFO: Notification suppressed for service account user: ${buildTriggeredUserName}"
            return
        }

        def buildData = [:]
        buildData["pipelineURL"]     = env.BUILD_URL
        buildData["triggerdBy"]      = buildTriggeredUserName
        buildData["triggerdByEmail"] = buildTriggeredUserEmailId
        buildData["status"]          = buildStatus
        buildData["stage"]           = (buildStatus == "FAILURE" ? stageName : "NA")

        def buildDataJson = JsonOutput.toJson(buildData)

        // Get the webhook URL for this job
        def channelUrl = getProductWorkflowChannelUrl()

        if (!channelUrl) {
            println "WARN: No webhook found for this job. Skipping notification."
            return
        }

        // Send the notification
        notifyTeam(buildDataJson, channelUrl)

    } catch (Exception e) {
        println "ERROR: Failed to send Teams notification: ${e.message}"
    }
}



////////////////////////////////////////////////////
// Resolves Team Notification URL
////////////////////////////////////////////////////

def getProductWorkflowChannelUrl() {

    def content = libraryResource('pipeline-global-config/workflow-urls.properties')
    def props   = readProperties(text: content)
    def buildUrl = env.BUILD_URL ?: ""

    println "INFO: Resolving webhook for job URL:"
    println buildUrl


    //--------------------------------------------------------------
    // CASE 1: API-Products (existing logic, do not change)
    //--------------------------------------------------------------
    def apiMatcher = buildUrl =~ /\/job\/API-Products\/job\/([^\/]+)\//
    if (apiMatcher.find()) {

        def productName = apiMatcher.group(1)
        println "INFO: API-Products job detected → ${productName}"

        def channelUrl = props[productName]
        if (channelUrl) {
            return channelUrl
        }

        println "WARN: No API-Products webhook found for key '${productName}'"
        return null
    }


    //--------------------------------------------------------------
    // CASE 2: Common-Framework → NEW LOGIC ADDED HERE
    //--------------------------------------------------------------
    def cfMatcher = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)\//
    if (cfMatcher.find()) {

        def subfolder = cfMatcher.group(1)

        println "INFO: Common-Framework job detected → Subfolder: ${subfolder}"

        // Always use the Common-Framework webhook key
        def channelUrl = props["Common-Framework"]

        if (channelUrl) {
            println "INFO: Using Common-Framework webhook"
            return channelUrl
        }

        println "WARN: No Common-Framework webhook found in properties!"
        return null
    }


    //--------------------------------------------------------------
    // CASE 3: No match → skip notification
    //--------------------------------------------------------------
    println "WARN: Job does not match API-Products or Common-Framework"
    return null
}



////////////////////////////////////////////////////
// Sends the notification to Teams (curl)
////////////////////////////////////////////////////

def notifyTeam(def payloadJson, def webhookUrl) {

    def safeJson = payloadJson.replace("'", "'\"'\"'")

    def responseCode = sh(
        script: """
            curl -s -k -w "%{http_code}" \
                 -H 'Content-Type: application/json' \
                 -X POST '${webhookUrl}' \
                 -d '${safeJson}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: Teams response code: ${responseCode}"
}

==================================


    def getProductWorkflowChannelUrl() {

    def content = libraryResource('pipeline-global-config/workflow-urls.properties')
    def props   = readProperties text: content
    def buildUrl = env.BUILD_URL ?: ""

    println "INFO: Resolving product name for URL:"
    println buildUrl

    String productName = null

    // -----------------------
    // Case A: API-Products
    // -----------------------
    def apiMatcher = buildUrl =~ /\/job\/API-Products\/job\/([^\/]+)\//
    if (apiMatcher.find()) {
        productName = apiMatcher.group(1)
        println "INFO: API-Products match → ${productName}"
    }

    // -----------------------
    // Case B: Common-Framework WITH subfolder
    // /job/Common-Framework/job/<subfolder>/job/<job>/
    // -----------------------
    if (!productName) {
        def cfMatcherSub = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)\/job\//
        if (cfMatcherSub.find()) {
            productName = "Common-Framework"   // Always use main key
            println "INFO: Common-Framework subfolder match"
        }
    }

    // -----------------------
    // Case C: Common-Framework WITHOUT subfolder
    // /job/Common-Framework/job/<job>/
    // -----------------------
    if (!productName) {
        def cfMatcherDirect = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)\//
        if (cfMatcherDirect.find()) {
            productName = "Common-Framework"
            println "INFO: Direct Common-Framework job match"
        }
    }

    if (!productName) {
        println "WARN: No matching product key found for this URL"
        return null
    }

    def url = props[productName]
    if (!url) {
        println "WARN: No webhook found for key = ${productName}"
        return null
    }

    println "INFO: Using Teams webhook key = ${productName}"
    return url
}

