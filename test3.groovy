
updateglobalcounetrfile

def call(def countValReset = null, def mockCount = null) {
    def envProperty = loadEnvironmentProperties()
    def counterFile = envProperty.fortify_global_counter_file
    def fortifyApiToken = envProperty.fortify_api_credential_id
    def fortifyApiURL = envProperty.fortify_api_url
    def pendingJobThreshold = envProperty.fortify_count_threshold as Integer
    def notificationEnabled = (envProperty.fortify_notification_enabled == null) ? 
        true : envProperty.fortify_notification_enabled.toLowerCase() == "true"
    
    if (!notificationEnabled) {
        println "INFO: Fortify Team Notification is DISABLED. Skipping notification processing."
        return
    }
    
    def notified = false
    if (fileExists(counterFile)) {
        def content = readFile(counterFile).trim()
        if (content.equalsIgnoreCase("true")) {
            notified = true
        } else if (content.equalsIgnoreCase("false")) {
            notified = false
        } else {
            println "WARN: Counter file invalid. Resetting to false."
            writeFile(file: counterFile, text: "false\n")
            notified = false
        }
        println "INFO: Previous notification state: ${notified}"
    } else {
        println "WARN: Counter file missing. Creating new one with false."
        writeFile(file: counterFile, text: "false\n")
        notified = false
    }
    
    // Get the count (use mock if provided, otherwise call API)
    def fortifyScanCount = (mockCount != null) ? mockCount : getFortifyPendingJobsCount(fortifyApiToken, fortifyApiURL)
    println "INFO: Fortify pending job count: ${fortifyScanCount}"
    
    // Check if threshold is exceeded
    if (fortifyScanCount > pendingJobThreshold) {
        if (!notified) {
            println "INFO: Threshold exceeded. FIRST FAILURE. Sending notification."
            notifyTeamsChannel()
            writeFile(file: counterFile, text: "true\n")
        } else {
            println "INFO: Threshold exceeded but already notified earlier. Skipping notification."
        }
        return
    }
    
    // THIS IS THE KEY FIX: Reset happens here when count is NORMAL (below threshold)
    println "INFO: Fortify job count normal. Resetting notification state."
    writeFile(file: counterFile, text: "false\n")
}

def getFortifyPendingJobsCount(def fortifyApiToken, def fortifyApiURL) {
    withCredentials([string(credentialsId: fortifyApiToken, variable: 'credentials')]) {
        def scanResponse = sh(
            script: "curl -ksH 'Authorization: FortifyToken ${credentials}' '${fortifyApiURL}/cloudjobs?fields=jobState&q=jobState:PENDING'",
            returnStdout: true
        ).trim()
        def jsonResponse = readJSON(text: scanResponse)
        def jobsCount = jsonResponse.count ?: 0
        println "INFO: Pending jobs count in Fortify: ${jobsCount}"
        return jobsCount
    }
}
================================================
================================================
    sendteamnotofication
def call() {
    try {
        def buildStatus = currentBuild.result
        def stageName = "NA"
        def channelUrl
        
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
        channelUrl = getProductWorkflowChannelUrl()
        
        if (channelUrl) {
            notifyTeam(buildDataJson, channelUrl)
        }
    } catch (Exception e) {
        println "ERROR: Failed to send Teams notification: ${e.message}"
    }
}

def getProductWorkflowChannelUrl() {
    def content = libraryResource('pipeline-global-config/workflow-urls.properties')
    def props = readProperties text: content
    def buildUrl = env.BUILD_URL ?: ""
    def productName
    def channelUrl
    
    def commonFrameworkMatcher = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)/
    if (commonFrameworkMatcher.find()) {
        println "INFO: Common-Framework folder detected"
        productName = "Common-Framework"
        channelUrl = props[productName]
        if (channelUrl) {
            println "INFO: Channel URL found for Common-Framework"
            return channelUrl
        } else {
            println "WARN: No channel URL configured for Common-Framework"
            return null
        }
    }
    
    def matcher = buildUrl =~ /\/job\/API-Products\/job\/([^\/]+)/
    if (matcher.find()) {
        productName = matcher.group(1)
        println "INFO: API-Products detected: ${productName}"
    } else {
        println "WARN: Skipping team notification for this build, unable to extract the capability folder name from the URL: ${buildUrl}"
        return null
    }
    
    channelUrl = props[productName]
    if (channelUrl) {
        println "INFO: Channel URL found for ${productName}"
        return channelUrl
    }
    return null
}

def notifyTeam(def buildDataJson, def channelUrl) {
    def escapedJson = buildDataJson.replace("'", "'\\''")
    def responseCode = sh(
        script: """curl -k -w '%{http_code}' -H 'Content-Type: application/json' -H 'Accept: application/json' -X POST '${channelUrl}' -d '${escapedJson}' -s""",
        returnStdout: true
    ).trim()
    println "INFO: The received HTTP response code from DevOps teams channel curl request: ${responseCode}"
}
========================================
    ===================================
    updateglobalcounetrfile for testing in poc jenkins

def call(def countValReset, def mockCount = null) {

    def envProperty = loadEnvironmentProperties()

    def counterFile          = envProperty.fortify_global_counter_file
    def fortifyApiToken      = envProperty.fortify_api_credential_id
    def fortifyApiURL        = envProperty.fortify_api_url
    def pendingJobThreshold  = envProperty.fortify_count_threshold as Integer

    def notificationEnabled =
        (envProperty.fortify_notification_enabled == null) ?
            true :
            envProperty.fortify_notification_enabled.toLowerCase() == "true"

    if (!notificationEnabled) {
        println "INFO: Fortify Team Notification is DISABLED. Skipping notification processing."
        return
    }

    // ---------------------------------------
    // Read notification state (true / false)
    // ---------------------------------------
    def notified = false

    if (fileExists(counterFile)) {
        def content = readFile(counterFile).trim()

        if (content.equalsIgnoreCase("true")) {
            notified = true
        } else if (content.equalsIgnoreCase("false")) {
            notified = false
        } else {
            println "WARN: Counter file invalid. Resetting to false."
            writeFile(file: counterFile, text: "false\n")
            notified = false
        }

        println "INFO: Previous notification state: ${notified}"
    } else {
        println "WARN: Counter file missing. Creating new one with false."
        writeFile(file: counterFile, text: "false\n")
        notified = false
    }

    // ---------------------------------------
    // Get Fortify pending job count
    // ---------------------------------------
    def fortifyScanCount = (mockCount != null)
        ? mockCount
        : getFortifyPendingJobsCount(fortifyApiToken, fortifyApiURL)

    println "INFO: Fortify pending job count: ${fortifyScanCount}"

    // ---------------------------------------
    // FAILURE case → notify once
    // ---------------------------------------
    if (fortifyScanCount > pendingJobThreshold) {

        if (!notified) {
            println "INFO: Threshold exceeded. FIRST FAILURE. Sending notification."
            notifyTeamsChannel()
            writeFile(file: counterFile, text: "true\n")
        } else {
            println "INFO: Threshold exceeded but already notified earlier. Skipping."
        }
        return
    }

    // ---------------------------------------
    // SUCCESS case → reset state
    // ---------------------------------------
    println "INFO: Fortify job count normal. Resetting notification state."
    writeFile(file: counterFile, text: "false\n")
}

=====================================================================
updateglobalcounetrfile

def call(def countValReset) {
    def envProperty = loadEnvironmentProperties()
    def counterFile = envProperty.fortify_global_counter_file
    def fortifyApiToken = envProperty.fortify_api_credential_id
    def fortifyApiURL = envProperty.fortify_api_url
    def pendingJobThreshold = envProperty.fortify_count_threshold as Integer
    def notificationEnabled = (envProperty.fortify_notification_enabled == null) ? 
        true : envProperty.fortify_notification_enabled.toLowerCase() == "true"
    
    if (!notificationEnabled) {
        println "INFO: Fortify Team Notification is DISABLED. Skipping notification processing."
        return
    }
    
    def notified = false
    if (fileExists(counterFile)) {
        def content = readFile(counterFile).trim()
        if (content.equalsIgnoreCase("true")) {
            notified = true
        } else if (content.equalsIgnoreCase("false")) {
            notified = false
        } else {
            println "WARN: Counter file invalid. Resetting to false"
            writeFile(file: counterFile, text: "false\n")
        }
        println "INFO: Previous notification state: ${notified}"
    } else {
        println "WARN: No global counter file found. Creating with initial state: false"
        writeFile(file: counterFile, text: "false\n")
    }
    
    def fortifyScanCount = getFortifyPendingJobsCount(fortifyApiToken, fortifyApiURL)
    println "INFO: Fortify pending job count: ${fortifyScanCount}"
    
    if (fortifyScanCount > pendingJobThreshold) {
        if (!notified) {
            println "INFO: Threshold exceeded. FIRST FAILURE. Sending notification."
            notifyTeamsChannel()
            writeFile(file: counterFile, text: "true\n")
        } else {
            println "INFO: Threshold exceeded but already notified earlier. Skipping notification."
        }
        return
    }
    
    println "INFO: Pending count normal, resetting notification state"
    writeFile(file: counterFile, text: "false\n")
}

def getFortifyPendingJobsCount(def fortifyApiToken, def fortifyApiURL) {
    withCredentials([string(credentialsId: fortifyApiToken, variable: 'credentials')]) {
        def scanResponse = sh(
            script: "curl -ksH 'Authorization: FortifyToken ${credentials}' '${fortifyApiURL}/cloudjobs?fields=jobState&q=jobState:PENDING'",
            returnStdout: true
        ).trim()
        def jsonResponse = readJSON(text: scanResponse)
        def jobsCount = jsonResponse.count ?: 0
        println "INFO: Pending jobs count in Fortify: ${jobsCount}"
        return jobsCount
    }
}
======================================
    =======================================
    ============================
    ================

=====================================================
getbuildtriggetd details

import hudson.tasks.Mailer
import hudson.model.User
import hudson.model.Cause

def call() {
    def buildCause = currentBuild.rawBuild.getCause(Cause.UserIdCause)
    
    if (buildCause == null) {
        def upstreamCause = currentBuild.rawBuild.getCause(Cause.UpstreamCause)
        // Removed: if (upstreamCause != null) check
        def upstreamBuild = upstreamCause.getUpstreamRun()
        def upstreamUserCause = upstreamBuild.getCause(Cause.UserIdCause)
        // Removed: if (upstreamUserCause != null) check
        buildCause = upstreamUserCause
    }
    
    def userId = buildCause.getUserId()
    def userData = User.get(userId)
    def mailProp = userData.getProperty(Mailer.UserProperty.class)
    def userEmail = mailProp?.getAddress() ?: null
    def userName = userData.getDisplayName()
    
    return [
        userName: userName,
        userEmail: userEmail
    ]
}
==========================
    
    claude code with only chnage to the boolena to string parameter

/**
 * MAIN METHOD
 * Supports:
 *   updateGlobalCounterFile("runCheck")
 */
def call(def countValReset) {

    def envProperty = loadEnvironmentProperties()
    def counterFile = envProperty.fortify_global_counter_file
    def pendingJobThreshold = envProperty.fortify_count_threshold as Integer
    def notificationEnabled = (envProperty.fortify_notification_enabled ?: "true").toBoolean()

    if (!notificationEnabled) {
        println "INFO: Fortify Team Notification is DISABLED. Skipping processing."
        return
    }

    // Work with strings: "true" / "false"
    def notified = "false"

    if (fileExists(counterFile)) {
        def line = readFile(counterFile).trim()
        if (line.startsWith("notified-")) {
            notified = line.split("-")[1]  // do NOT convert to boolean
            println "INFO: Previous notification state = ${notified}"
        } else {
            println "WARN: Invalid counter file. Resetting."
            writeFile(file: counterFile, text: "notified-false\n")
        }
    } else {
        println "WARN: Counter file missing. Creating new one."
        writeFile(file: counterFile, text: "notified-false\n")
    }

    // -------------------------------
    // GET JOB COUNT (MOCK ONLY)
    // -------------------------------
    def fortifyScanCount = getMockScanCount()
    println "INFO: MOCK Fortify Scan Count = ${fortifyScanCount}"

    // -------------------------------
    // FAILURE CASE (count > threshold)
    // -------------------------------
    if (fortifyScanCount > pendingJobThreshold) {

        if (notified == "false") {
            println "INFO: Threshold exceeded. FIRST FAILURE. Sending notification."
            notifyTeamsChannel()
            writeFile(file: counterFile, text: "notified-true\n")
        } 
        else {
            println "INFO: Threshold exceeded BUT already notified. Skipping."
        }

        return
    }

    // -------------------------------
    // SUCCESS CASE (count <= threshold)
    // -------------------------------
    println "INFO: Count normal. Resetting notification state."
    writeFile(file: counterFile, text: "notified-false\n")
}


/**
 * BACKWARD-COMPATIBILITY WRAPPER
 * Allows:
 *   updateGlobalCounterFile("runCheck", 15)
 */
def call(def action, def mockCount) {

    println "INFO: MOCK MODE INVOKED → action=${action}, mockCount=${mockCount}"

    // save mock value so main logic can use it
    this.mockValue = mockCount

    // normalize action
    if (action.equalsIgnoreCase("runCheck")) {
        return call("mockRun")
    }

    if (action.equalsIgnoreCase("resetWithout Notification") ||
        action.equalsIgnoreCase("resetWithoutNotification") ||
        action.equalsIgnoreCase("reset")) {

        return call("reset")
    }

    println "WARN: Unknown action '${action}'. Passing to main call()"
    return call(action)
}


/**
 * MOCK Fortify count provider
 * Removes API call completely
 */
def getMockScanCount() {
    if (this.mockValue != null) {
        println "INFO: Using MOCK value = ${this.mockValue}"
        def tmp = this.mockValue
        this.mockValue = null  // prevent reuse
        return tmp
    }

    // If no mock provided, default to 0
    println "INFO: No mock value provided. Defaulting count = 0"
    return 0
}

=================================================================================
remove the boolen, only string

def call(def countValReset) {
    def envProperty = loadEnvironmentProperties()
    def counterFile = envProperty.fortify_global_counter_file
    def fortifyApiToken = envProperty.fortify_api_credential_id
    def fortifyApiURL = envProperty.fortify_api_url
    def pendingJobThreshold = envProperty.fortify_count_threshold as Integer
    def notificationEnabled = (envProperty.fortify_notification_enabled ?: "true").toBoolean()
    
    if (!notificationEnabled) {
        println "INFO: Fortify Team Notification is DISABLED. Skipping notification processing."
        return
    }
    
    // FIXED: Work with string directly, no string-to-boolean conversion
    def notified = "false"
    
    if (fileExists(counterFile)) {
        def line = readFile(counterFile).trim()
        if (line.startsWith("notified-")) {
            notified = line.split("-")[1]  // Keep as string: "true" or "false"
            println "INFO: Previous notification state = ${notified}"
        } else {
            println "WARN: Counter file invalid. Resetting to notified-false"
            writeFile(file: counterFile, text: "notified-false\n")
        }
    } else {
        println "WARN: No global counter file found"
        writeFile(file: counterFile, text: "notified-false\n")
    }
    
    def fortifyScanCount = getFortifyPendingJobsCount(fortifyApiToken, fortifyApiURL)
    println "INFO: Fortify pending job count ${fortifyScanCount}"
    
    if (fortifyScanCount > pendingJobThreshold) {
        // FIXED: Compare strings directly instead of using boolean
        if (notified == "false") {
            // FAILURE (count > threshold)
            println "INFO: Threshold exceeded. FIRST FAILURE. Sending Teams Notification"
            notifyTeamsChannel()
            writeFile(file: counterFile, text: "notified-true\n")
        } else {
            println "INFO: Threshold exceeded but already notified earlier. Skipping notify"
        }
    } else {
        // CASE 2: SUCCESS (count <= threshold)
        // Reset notification flag
        println "INFO: Pending count normal. Resetting notification state"
        writeFile(file: counterFile, text: "notified-false\n")
    }
}

def getFortifyPendingJobsCount(def fortifyApiToken, def fortifyApiURL) {
    withCredentials([string(credentialsId: fortifyApiToken, variable: 'credentials')]) {
        def scanResponse = sh(script: "curl -ksH 'Authorization: FortifyToken ${credentials}' '${fortifyApiURL}/cloudjobs?fields=jobState&q=jobState:PENDING'", returnStdout: true).trim()
        def jsonResponse = readJSON text: scanResponse
        def jobsCount = jsonResponse.count ?: 0
        println "INFO: Pending jobs count in Fortify: ${jobsCount}"
        return jobsCount
    }
}

======================================================================================================

==========================================

    logstage file

/**
 * Wrapper for pipeline stages with logging and post-stage actions
 * 
 * @param stageName - Name of the stage being executed
 * @param body - Closure containing the stage logic
 */
def call(String stageName, Closure body) {
    def greenBold = '\u001B[1;32m'
    def resetColor = '\u001B[0m'
    
    try {
        println "${greenBold}***** STAGE '${stageName}' STARTED *****${resetColor}"
        
        // Execute the stage body
        body()
        
        println "${greenBold}***** STAGE '${stageName}' COMPLETED SUCCESSFULLY *****${resetColor}"
        
        // Post-stage actions based on stage name
        handlePostStageActions(stageName)
        
    } catch (Exception err) {
        // Store failed stage name for error reporting
        env.failedStage = stageName
        println "ERROR: Stage '${stageName}' failed with error: ${err.message}"
        throw err
    }
}

/**
 * Handles post-stage actions for specific stages
 */
def handlePostStageActions(String stageName) {
    // Reset notification counter after successful Fortify scan
    if (stageName.equalsIgnoreCase("scan-fortify")) {
        println "INFO: Fortify scan completed successfully. Resetting global notification counter."
        try {
            updateGlobalCounterFile("reset")
        } catch (Exception e) {
            // Don't fail the stage if counter update fails
            println "WARN: Failed to reset global counter: ${e.message}"
        }
    }
    
    // Add other post-stage actions here as needed
    // Example:
    // if (stageName.equalsIgnoreCase("deploy-production")) {
    //     notifyDeploymentSuccess()
    // }
}
===========================================================================
    ==============================
    notifyteamchannel

def call() {
    def envProperty = loadEnvironmentProperties()
    def devopsWorkFlowUrl = envProperty.devops_workflow_url
    def fortifyWorkFlowUrl = envProperty.fortify_workflow_url
    def emailAddressList = envProperty.to_email_address_list
    def fortifyChannelEmails = envProperty.fortify_channel_emails_json
    def pipelineUrl = env.BUILD_URL ?: ""
    
    // Create JSON for DevOps channel
    def baseDevOpsJson = createChannelJson(emailAddressList, pipelineUrl)
    println "INFO: DevOps JSON to send: ${baseDevOpsJson}"
    def escapedDevOpsJson = baseDevOpsJson.replace("'", "'\\''")
    
    def devopsChannelResponse = sh(
        script: "curl -s -w '\\n%{http_code}' -H 'Content-Type: application/json' -H 'Accept: application/json' -X POST '${devopsWorkFlowUrl}' -d '${escapedDevOpsJson}'",
        returnStdout: true
    ).trim()
    
    def devopsLines = devopsChannelResponse.split('\n')
    def devopsResponseBody = devopsLines.size() > 1 ? devopsLines[0..-2].join('\n') : ''
    def devopsResponseCode = devopsLines[-1]
    
    println "INFO: DevOps Teams channel response code: ${devopsResponseCode}"
    println "INFO: DevOps Teams channel response body: ${devopsResponseBody}"
    
    // Create JSON for Fortify channel
    def enrichedFortifyJson = createChannelJson(fortifyChannelEmails, pipelineUrl)
    println "INFO: Fortify JSON to send: ${enrichedFortifyJson}"
    def escapedFortifyJson = enrichedFortifyJson.replace("'", "'\\''")
    
    def fortifyChannelResponse = sh(
        script: "curl -s -w '\\n%{http_code}' -H 'Content-Type: application/json' -H 'Accept: application/json' -X POST '${fortifyWorkFlowUrl}' -d '${escapedFortifyJson}'",
        returnStdout: true
    ).trim()
    
    def fortifyLines = fortifyChannelResponse.split('\n')
    def fortifyResponseBody = fortifyLines.size() > 1 ? fortifyLines[0..-2].join('\n') : ''
    def fortifyResponseCode = fortifyLines[-1]
    
    println "INFO: Fortify support channel response code: ${fortifyResponseCode}"
    println "INFO: Fortify support channel response body: ${fortifyResponseBody}"
}

def createChannelJson(def emailInput, def pipelineUrl) {
    def emailMap = [:]
    def emails = []
    
    // Check if input is a comma-separated string or already a list
    if (emailInput instanceof String) {
        emails = emailInput.split(',').collect { it.trim() }
    } else if (emailInput instanceof List) {
        emails = emailInput
    } else {
        println "WARN: Unexpected email input type: ${emailInput.getClass()}"
        emails = [emailInput.toString()]
    }
    
    // Create email keys dynamically using standard for loop with counter
    int counter = 1
    for (String e : emails) {
        emailMap.put("email" + counter, e)
        counter++
    }
    
    // Add pipeline URL
    emailMap["pipelineUrl"] = pipelineUrl
    
    def jsonString = groovy.json.JsonOutput.toJson(emailMap)
    println "INFO: Channel JSON created: ${jsonString}"
    return jsonString
}
=========================================================================================================
sendteamnotifcation

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

def getProductWorkflowChannelUrl() {
    def content = libraryResource('pipeline-global-config/workflow-urls.properties')
    def props = readProperties text: content
    def buildUrl = env.BUILD_URL ?: ""
    
    // Check for Common-Framework first
    def commonFrameworkMatcher = buildUrl =~ /\/job\/Common-Framework\/job\/([^\/]+)/
    if (commonFrameworkMatcher.find()) {
        def productName = "Common-Framework"
        println "INFO: Common-Framework folder detected"
        def channelUrl = props[productName]
        if (channelUrl) {
            println "INFO: Channel URL found for Common-Framework"
            return channelUrl
        } else {
            println "WARN: No channel URL configured for Common-Framework"
            return null
        }
    }
    
    // Existing API-Products logic (unchanged)
    def matcher = buildUrl =~ /\/job\/API-Products\/job\/([^\/]+)/
    def productName = ""
    
    if (matcher.find()) {
        productName = matcher.group(1)
        println "INFO: API-Products detected: ${productName}"
    } else {
        println "WARN: Skipping team notification for this build, unable to extract the capability folder name from the URL: ${buildUrl}"
        return null
    }
    
    def channelUrl = props[productName]
    
    if (channelUrl) {
        println "INFO: Channel URL found for ${productName}"
        return channelUrl
    } else {
        println "WARN: No channel URL configured for ${productName}"
        return null
    }
}

def notifyTeam(def buildDataJson, def channelUrl) {
    def responseCode = sh(
        script: "curl -k -w '%{http_code}' -H 'Content-Type: application/json' -H 'Accept: application/json' -X POST '${channelUrl}' -d '${buildDataJson}' -o /dev/null -s",
        returnStdout: true
    ).trim()
    
    println "INFO: The received HTTP response code from DevOps teams channel curl request: ${responseCode}"
}
==========================================
    ===================
    ===============

inset key for pipleine url.for fortifyteam notification 
def call() {

    def envProperty = loadEnvironmentProperties()

    def devopsWorkFlowUrl      = envProperty.devops_workflow_url
    def fortifyWorkFlowUrl     = envProperty.fortify_workflow_url
    def emailAddressList       = envProperty.to_email_address_list
    def fortifyChannelEmails   = envProperty.fortify_channel_emails_json

    def pipelineUrl = env.BUILD_URL ?: ""

    // ------------------------------------------------------------
    // DEVOPS JSON (pipelineUrl added INSIDE createEmailJson)
    // ------------------------------------------------------------
    def baseDevOpsJson = createEmailJson(emailAddressList, pipelineUrl)

    println "INFO: DevOps JSON to send: ${baseDevOpsJson}"

    def escapedDevOpsJson = baseDevOpsJson.replace("'", "'\\\\''")

    def devopsChannelResponseCode = sh(
            script: """curl -s -o /dev/null -w '%{http_code}' \
                -H 'Content-Type: application/json' \
                -H 'Accept: application/json' \
                -X POST '${devopsWorkFlowUrl}' \
                -d '${escapedDevOpsJson}'""",
            returnStdout: true
    ).trim()

    println "INFO: DevOps Teams channel response code: ${devopsChannelResponseCode}"


    // ------------------------------------------------------------
    // FORTIFY JSON (NOW USING SAFE PARSE + ADD KEY, NO .replace)
    // ------------------------------------------------------------
    def enrichedFortifyJson = createFortifyJson(fortifyChannelEmails, pipelineUrl)

    println "INFO: Fortify JSON to send: ${enrichedFortifyJson}"

    def escapedFortifyJson = enrichedFortifyJson.replace("'", "'\\\\''")

    def fortifyChannelResponseCode = sh(
            script: """curl -s -o /dev/null -w '%{http_code}' \
                -H 'Content-Type: application/json' \
                -H 'Accept: application/json' \
                -X POST '${fortifyWorkFlowUrl}' \
                -d '${escapedFortifyJson}'""",
            returnStdout: true
    ).trim()

    println "INFO: Fortify support channel response code: ${fortifyChannelResponseCode}"
}


// ==================================================================
// FUNCTION 1: Build DevOps JSON with emails + pipelineUrl
// ==================================================================
def createEmailJson(def emailAddressList, def pipelineUrl) {

    def emails = emailAddressList.split(',').collect { it.trim() }
    def emailMap = [:]

    emails.eachWithIndex { email, index ->
        emailMap["email${index + 1}"] = email
    }

    // Add pipeline URL (Manager Request)
    emailMap["pipelineUrl"] = pipelineUrl

    def jsonString = groovy.json.JsonOutput.toJson(emailMap)

    println "INFO: Email JSON created: ${jsonString}"

    return jsonString
}


// ==================================================================
// FUNCTION 2: Build Fortify JSON SAFELY (parse + add key)
// ==================================================================
def createFortifyJson(def fortifyChannelEmails, def pipelineUrl) {

    // Convert Fortify JSON string → Map
    def fortifyMap = new groovy.json.JsonSlurper().parseText(fortifyChannelEmails)

    // Add pipeline URL
    fortifyMap["pipelineUrl"] = pipelineUrl

    // Convert Map → JSON string
    def jsonString = groovy.json.JsonOutput.toJson(fortifyMap)

    println "INFO: Fortify JSON created: ${jsonString}"

    return jsonString
}
==========≠=====================
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
    working code for both need to test
    def call() {

    def envProperty          = loadEnvironmentProperties()
    def devopsWorkFlowUrl    = envProperty.devops_workflow_url
    def fortifyWorkFlowUrl   = envProperty.fortify_workflow_url
    def emailAddressList     = envProperty.to_email_address_list
    def fortifyChannelEmails = envProperty.fortify_channel_emails_json

    def pipelineUrl = env.BUILD_URL ?: ""

    //----------------------------------------------------------------------
    // 1. CREATE DEVOPS JSON
    //----------------------------------------------------------------------
    def baseDevOpsJson = createEmailJson(emailAddressList)

    // Append pipelineUrl into JSON
    def enrichedDevOpsJson = baseDevOpsJson.replace(
        "}",
        ",\"pipelineUrl\": \"${pipelineUrl}\"}"
    )

    println "INFO: DevOps JSON to send: ${enrichedDevOpsJson}"

    // Escape single quotes for shell
    def escapedDevOpsJson = enrichedDevOpsJson.replace("'", "'\\\\''")

    //----------------------------------------------------------------------
    // SEND TO DEVOPS TEAMS CHANNEL
    //----------------------------------------------------------------------
    def devOpsResponseCode = sh(
        script: """
            curl -s -o /dev/null -w "%{http_code}" \
            -H "Content-Type: application/json" \
            -H "Accept: application/json" \
            -X POST "${devopsWorkFlowUrl}" \
            -d '${escapedDevOpsJson}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: DevOps Teams channel response code: ${devOpsResponseCode}"


    //----------------------------------------------------------------------
    // 2. CREATE FORTIFY JSON
    //----------------------------------------------------------------------
    def enrichedFortifyJson = fortifyChannelEmails.replace(
        "}",
        ",\"pipelineUrl\": \"${pipelineUrl}\"}"
    )

    println "INFO: Fortify JSON to send: ${enrichedFortifyJson}"

    def escapedFortifyJson = enrichedFortifyJson.replace("'", "'\\\\''")

    //----------------------------------------------------------------------
    // SEND TO FORTIFY TEAMS CHANNEL
    //----------------------------------------------------------------------
    def fortifyResponseCode = sh(
        script: """
            curl -s -o /dev/null -w "%{http_code}" \
            -H "Content-Type: application/json" \
            -H "Accept: application/json" \
            -X POST "${fortifyWorkFlowUrl}" \
            -d '${escapedFortifyJson}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: Fortify support channel response code: ${fortifyResponseCode}"
}



def createEmailJson(def emailAddressList) {

    def emails = emailAddressList.split(',').collect { it.trim() }
    def emailMap = [:]

    emails.eachWithIndex { email, index ->
        emailMap["email${index + 1}"] = email
    }

    def jsonString = groovy.json.JsonOutput.toJson(emailMap)

    println "INFO: Email JSON created: ${jsonString}"

    return jsonString
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


---------------------------------
    =================================

    updateglobalcounterfile
def call(def countValReset, def mockCount = null) {

    def envProperty = loadEnvironmentProperties()

    def counterFile             = envProperty.fortify_global_counter_file
    def fortifyApiToken         = envProperty.fortify_api_credential_id
    def fortifyApiURL           = envProperty.fortify_api_url
    def pendingJobThreshold     = envProperty.fortify_count_threshold as Integer
    def notificationEnabled     = (envProperty.fotify_notification_enabled ?: "true").toBoolean()

    if (!notificationEnabled) {
        println "INFO: Fortify Team Notification is DISABLED. Skipping notification processing."
        return
    }

    def notified = false

    if (fileExists(counterFile)) {
        def line = readFile(counterFile).trim()
        if (line.startsWith("notified-")) {
            notified = line.split("-")[1].toBoolean()
        }
        println "INFO: Previous notification state = ${notified}"
    } else {
        println "WARN: Counter file not found. Creating new one."
        writeFile(file: counterFile, text: "notified-false\n")
    }

    // ⭐ USE MOCK VALUE IF PROVIDED (for POC testing)
    def fortifyScanCount = (mockCount != null)
        ? mockCount
        : getFortifyPendingJobsCount(fortifyApiToken, fortifyApiURL)

    println "INFO: Fortify pending job count = ${fortifyScanCount}"

    // FAILURE case
    if (fortifyScanCount > pendingJobThreshold) {

        if (!notified) {
            println "INFO: First failure → sending notification"
            notifyTeamsChannel()
            writeFile(file: counterFile, text: "notified-true\n")
        } else {
            println "INFO: Already notified earlier → skipping"
        }

        return
    }

    // SUCCESS case → reset
    println "INFO: Fortify job count normal → resetting state"
    writeFile(file: counterFile, text: "notified-false\n")
}




// =====================================================
// Helper Method
// =====================================================

def getFortifyPendingJobsCount(def fortifyApiToken, def fortifyApiURL) {

    withCredentials([string(credentialsId: fortifyApiToken, variable: 'credentials')]) {

        def scanResponse = sh(
            script: """curl -ks -H 'Authorization: FortifyToken ${credentials}' '${fortifyApiURL}/cloudjobs?fields=jobState&q=jobState:PENDING'""",
            returnStdout: true
        ).trim()

        def jsonResponse = readJSON text: scanResponse

        def jobsCount = jsonResponse.count ?: 0

        println "INFO: Pending jobs count in Fortify: ${jobsCount}"

        return jobsCount
    }
}
==============================================================================

 properties([
    parameters([
        choice(
            name: 'SCENARIO',
            choices: ['FAILURE', 'SUCCESS', 'NORMAL'],
            description: 'Choose POC scenario'
        ),
        string(
            name: 'MOCK_COUNT',
            defaultValue: '0',
            description: 'Simulated fortifyScanCount for testing'
        )
    ])
])

node('any') {

    ansiColor('xterm') {

        try {

            println "============== FORTIFY POC TEST START =============="
            println "Selected Scenario       : ${params.SCENARIO}"
            println "Mock Fortify Count      : ${params.MOCK_COUNT}"
            println "==================================================="

            // Convert to integer
            def mockCount = params.MOCK_COUNT as Integer

            stage('POC: Run Scenario') {

                if (params.SCENARIO == 'FAILURE') {

                    println "POC → Simulating FAILURE scenario"
                    updateGlobalCounter("runCheck", mockCount)

                    // Fail the build on purpose
                    error("Simulated failure for POC testing")

                } else if (params.SCENARIO == 'SUCCESS') {

                    println "POC → Simulating SUCCESS scenario (reset notification)"
                    updateGlobalCounter("resetWithoutNotification")

                } else {

                    println "POC → Simulating NORMAL scenario (no threshold breach)"
                    updateGlobalCounter("runCheck", mockCount)
                }
            }

            println "POC Completed Successfully"

        } catch (err) {

            println "=========== FAILURE HANDLING BLOCK ==========="

            println "Build failed → Executing failure logic"
            def mockCount = params.MOCK_COUNT as Integer
            updateGlobalCounter("runCheck", mockCount)

            // Re-throw error so Jenkins marks build as failed
            throw err

        } finally {

            println "=========== FINALLY BLOCK EXECUTED ==========="
            println "Sending Final Teams Notification (if applicable)"

            sendTeamsNotification()

            println "============== POC TEST END =============="
        }
    }
}
==========================================

    update script for updateglobalcounterfile

def call(def countValReset) {

    def envProperty = loadEnvironmentProperties()

    def counterFile         = envProperty.fortify_global_counter_file
    def fortifyApiToken     = envProperty.fortify_api_credential_id
    def fortifyApiURL       = envProperty.fortify_api_url
    def pendingJobThreshold = envProperty.fortify_count_threshold as Integer
    def notificationEnabled = (envProperty.fotify_notification_enabled ?: "true").toBoolean()

    if (!notificationEnabled) {
        println "INFO: Fortify Team Notification is DISABLED. Skipping notification."
        return
    }

    // ------------------------------------------------
    // Load previous notification state
    // ------------------------------------------------
    def notified = false

    if (fileExists(counterFile)) {
        def line = readFile(counterFile).trim()

        if (line.startsWith("notified-")) {
            notified = line.split("-")[1].toBoolean()
            println "INFO: Previous notification state = ${notified}"
        } else {
            println "WARN: Counter file invalid. Resetting to notified-false"
            writeFile(file: counterFile, text: "notified-false\n")
        }

    } else {
        println "WARN: Counter file not found → creating new one"
        writeFile(file: counterFile, text: "notified-false\n")
    }

    // ------------------------------------------------
    // REAL Fortify API Call → fetch pending job count
    // ------------------------------------------------
    def fortifyScanCount = getFortifyPendingJobsCount(fortifyApiToken, fortifyApiURL)
    println "INFO: Fortify pending job count = ${fortifyScanCount}"

    // ------------------------------------------------
    // CASE 1 → FAILURE (count > threshold)
    // ------------------------------------------------
    if (fortifyScanCount > pendingJobThreshold) {

        if (!notified) {
            println "INFO: Threshold exceeded. FIRST FAILURE → Sending Teams Notification"
            notifyTeamsChannel()

            writeFile(file: counterFile, text: "notified-true\n")
        } else {
            println "INFO: Threshold exceeded but already notified earlier → Skipping notify"
        }

        return
    }

    // ------------------------------------------------
    // CASE 2 → SUCCESS (count <= threshold)
    // Reset notification flag
    // ------------------------------------------------
    println "INFO: Pending count normal → resetting notification state"
    writeFile(file: counterFile, text: "notified-false\n")
}



// =====================================================================
// REAL Fortify API Call
// =====================================================================
def getFortifyPendingJobsCount(def fortifyApiToken, def fortifyApiURL) {

    withCredentials([string(credentialsId: fortifyApiToken, variable: 'credentials')]) {

        def scanResponse = sh(
            script: """
                curl -ks -H 'Authorization: FortifyToken ${credentials}' \
                '${fortifyApiURL}/cloudjobs?fields=jobState&q=jobState:PENDING'
            """,
            returnStdout: true
        ).trim()

        def jsonResponse = readJSON text: scanResponse
        def jobsCount = jsonResponse.count ?: 0

        println "INFO: Fortify pending jobs = ${jobsCount}"
        return jobsCount
    }
}


