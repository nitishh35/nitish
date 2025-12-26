def call() {
    def envProperty = loadEnvironmentProperties()
    def devopsWorkFlowUrl = envProperty.devops_workflow_url
    def fortifyWorkFlowUrl = envProperty.fortify_workflow_url
    def emailAddressList = envProperty.to_email_address_list
    def fortifyChannelEmails = envProperty.fortify_channel_emails_json
    def pipelineUrl = env.BUILD_URL ?: ""
    
    def baseDevOpsJson = createEmailJson(emailAddressList, pipelineUrl)
    println "INFO: DevOps JSON to send: ${baseDevOpsJson}"
    def escapedDevOpsJson = baseDevOpsJson.replace("'", "'\\''")
    def devopsChannelResponseCode = sh(
        script: "curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -H 'Accept: application/json' -X POST '${devopsWorkFlowUrl}' -d '${escapedDevOpsJson}'",
        returnStdout: true
    ).trim()
    println "INFO: DevOps Teams channel response code: ${devopsChannelResponseCode}"
    
    def enrichedFortifyJson = createEmailJson(fortifyChannelEmails, pipelineUrl)
    println "INFO: Fortify JSON to send: ${enrichedFortifyJson}"
    def escapedFortifyJson = enrichedFortifyJson.replace("'", "'\\''")
    def fortifyChannelResponseCode = sh(
        script: "curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -H 'Accept: application/json' -X POST '${fortifyWorkFlowUrl}' -d '${escapedFortifyJson}'",
        returnStdout: true
    ).trim()
    println "INFO: Fortify support channel response code: ${fortifyChannelResponseCode}"
}

def createEmailJson(def emailAddressList, def pipelineUrl) {
    def emails = emailAddressList.split(',')
    def emailMap = [:]
    
    emails.eachWithIndex { email, index ->
        emailMap["email${index + 1}"] = email.trim()
    }
    
    emailMap["pipelineUrl"] = pipelineUrl
    def jsonString = groovy.json.JsonOutput.toJson(emailMap)
    println "INFO: Email JSON created: ${jsonString}"
    return jsonString
}
=============================================================================

def notificationEnabled =
    (envProperty.fortify_notification_enabled != null &&
     envProperty.fortify_notification_enabled.toLowerCase() == "true")
println "DEBUG: fortify_notification_enabled raw value = '${envProperty.fortify_notification_enabled}'" println "DEBUG: notificationEnabled evaluated = ${notificationEnabled}"

updateglobalcounterfile
def call() {
    def envProperty = loadEnvironmentProperties()
    def counterFile = envProperty.fortify_global_counter_file
    def fortifyApiToken = envProperty.fortify_api_credential_id
    def fortifyApiURL = envProperty.fortify_api_url
    def pendingJobThreshold = envProperty.fortify_count_threshold as Integer
    
    // Manager's change: Remove defaulting to true for null
    def notificationEnabled = (envProperty.fortify_notification_enabled != null && 
                               envProperty.fortify_notification_enabled.toLowerCase() == "true")
    
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
            println "INFO: Already notified earlier. Skipping."
        }
        return
    }
    
    println "INFO: Pending count normal, resetting notification state"
    writeFile(file: counterFile, text: "false\n")
}

def getFortifyPendingJobsCount(def fortifyApiToken, def fortifyApiURL) {
    withCredentials([string(credentialsId: fortifyApiToken, variable: 'credentials')]) {
        def scanResponse = sh(script: "curl -ksH 'Authorization: FortifyToken ${credentials}' '${fortifyApiURL}/cloudjobs?fields=jobState&q=jobState:PENDING'", returnStdout: true).trim()
        def jsonResponse = readJSON(text: scanResponse)
        def jobsCount = jsonResponse.count ?: 0
        println "INFO: Pending jobs count in Fortify: ${jobsCount}"
        return jobsCount
    }
}
==========================================================
updateglobalcounterfile for pof final and working
    ------------------------------------------------
def call(def countValReset, def mockCount = null) {
    def envProperty = loadEnvironmentProperties()
    def counterFile = envProperty.fortify_global_counter_file
    def fortifyApiToken = envProperty.fortify_api_credential_id
    def fortifyApiURL = envProperty.fortify_api_url
    def pendingJobThreshold = envProperty.fortify_count_threshold as Integer
    
    // notificationEnabled must be TRUE/FALSE only, not string
    def notificationEnabled = 
        (envProperty.fortify_notification_enabled == null) ? 
        true : envProperty.fortify_notification_enabled.toLowerCase() == "true"
    
    if (!notificationEnabled) {
        println "INFO: Fortify Team Notification is DISABLED. Skipping notification processing."
        return
    }
    
    //--
    // Load existing state - SIMPLIFIED: read true/false directly
    //--
    def notified = false
    if (fileExists(counterFile)) {
        def content = readFile(counterFile).trim()
        if (content.equalsIgnoreCase("true")) {
            notified = true
            println "INFO: Previous notification state = true"
        } else if (content.equalsIgnoreCase("false")) {
            notified = false
            println "INFO: Previous notification state = false"
        } else {
            println "WARN: Counter file invalid. Resetting."
            writeFile(file: counterFile, text: "false\n")
        }
    } else {
        println "WARN: Counter file missing. Creating new one."
        writeFile(file: counterFile, text: "false\n")
    }
    
    //--
    // USE MOCK VALUE FOR LOCAL TESTING
    //--
    def fortifyScanCount = (mockCount != null) 
        ? mockCount 
        : getFortifyPendingJobsCount(fortifyApiToken, fortifyApiURL)
    
    println "INFO: Fortify pending job count = ${fortifyScanCount}"
    
    //--
    // FAILURE CASE
    //--
    if (fortifyScanCount > pendingJobThreshold) {
        if (!notified) {
            println "INFO: Threshold exceeded. FIRST FAILURE. Sending notification."
            notifyTeamsChannel()
            writeFile(file: counterFile, text: "true\n")
        } else {
            println "INFO: Already notified earlier. Skipping."
        }
        return
    }
    
    //--
    // SUCCESS CASE - reset notification
    //--
    println "INFO: Fortify job count normal. Resetting state."
    writeFile(file: counterFile, text: "false\n")
}

//
// Helper Method (API Call)
//
def getFortifyPendingJobsCount(def fortifyApiToken, def fortifyApiURL) {
    withCredentials([string(credentialsId: fortifyApiToken, variable: 'credentials')]) {
        def scanResponse = sh(
            script: """curl -ks -H 'Authorization: FortifyToken ${credentials}' \
                '${fortifyApiURL}/cloudjobs?fields=jobState&q=jobState:Pending'""",
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
   
