getBuildTriggeredUserDetails.groovy
--------------------------------------

    import hudson.tasks.Mailer
import hudson.model.User

def call() {
    try {
        def buildCause = currentBuild.rawBuild.getCause(hudson.model.Cause.UserIdCause.class)
        
        // Check if build was triggered by a user
        if (buildCause == null) {
            println "WARN: Build not triggered by a user. Checking for other causes..."
            
            // Handle other trigger types
            def allCauses = currentBuild.rawBuild.getCauses()
            for (cause in allCauses) {
                if (cause instanceof hudson.model.Cause.UpstreamCause) {
                    return [
                        userName: "Upstream Job: ${cause.getUpstreamProject()}",
                        userEmail: "upstream-trigger@syf.com"
                    ]
                } else if (cause instanceof hudson.triggers.TimerTrigger.TimerTriggerCause) {
                    return [
                        userName: "Scheduled Build (Timer)",
                        userEmail: "scheduler@syf.com"
                    ]
                } else if (cause instanceof hudson.triggers.SCMTrigger.SCMTriggerCause) {
                    return [
                        userName: "SCM Change",
                        userEmail: "scm-trigger@syf.com"
                    ]
                }
            }
            
            // Default if no specific cause found
            return [
                userName: "System/Automated Trigger",
                userEmail: "dse.api.devops@syf.com"
            ]
        }
        
        def userId = buildCause.getUserId()
        def userData = User.get(userId)
        def mailProp = userData.getProperty(Mailer.UserProperty.class)
        def userEmail = mailProp?.getAddress() ?: "${userId}@syf.com" // Fallback to syf.com domain
        def userName = userData.getDisplayName() ?: userId
        
        println "INFO: Build triggered by user: ${userName} (${userEmail})"
        
        return [
            userName: userName,  // ✅ FIXED: was "userflame" in original code
            userEmail: userEmail
        ]
        
    } catch (Exception e) {
        println "ERROR: Failed to get build triggered user details: ${e.message}"
        e.printStackTrace()
        
        // Return default values on error
        return [
            userName: "Unknown User",
            userEmail: "dse.api.devops@syf.com"
        ]
    }
}

=================================================

    logStage.groovy

--------------------

    def call(def stageName, Closure body) {
    def greenBold = '\u001B[1;32m'
    def resetColor = '\u001B[0m'
    
    try {
        println "${greenBold}** STAGE '${stageName}' STARTED **${resetColor}"
        
        body()
        
        println "${greenBold}** STAGE '${stageName}' COMPLETED SUCCESSFULLY **${resetColor}"
        
        // Reset Fortify counter after successful scan
        if (stageName.equalsIgnoreCase("scan-fortify")) {
            println "INFO: Fortify scan completed successfully, resetting global pending job counter."
            UpdateGlobalCounter("resetWithoutNotification")  // ✅ FIXED: Correct function name (was updateGlobalCounterFile)
        }
        
    } catch (err) {
        env.failedStage = stageName
        println "${resetColor}** STAGE '${stageName}' FAILED **"
        throw err
    }
}

=====================================================================

    UpdateGlobalCounter.groovy
---------------------

    def call(def countValReset) {
    def envProperty = loadEnvironmentProperties()
    def counterFile = envProperty.fortify_global_counter_file  // /appbin/install/build_counter/global-counter.properties
    def fortifyApiToken = envProperty.fortify_api_credential_id  // FORTIFYSCANCENTRALAPITOKEN
    def fortifyApiURL = envProperty.fortify_api_url  // https://fortify-ssc.app.syfbank.com:8443/ssc/api/v1
    def pendingJobThreshold = envProperty.fortify_count_threshold as Integer  // 5
    def notificationEnabled = (envProperty.fortify_notification_enabled ?: "true").toBoolean()  // false in config
    
    // ✅ FIXED: Correct negation operator (was InotificationEnabled)
    if (!notificationEnabled) {
        println "INFO: Fortify Team Notification is DISABLED. Skipping notification processing."
        return
    }
    
    def notified = false
    
    // Read previous notification state
    if (fileExists(counterFile)) {
        def props = readFile(counterFile).readLines()
        def notifiedEntry = props.find { it?.trim()?.startsWith("notified=") }
        if (notifiedEntry) {
            notified = notifiedEntry.split("=")[1].toBoolean()
            println "INFO: Previous notification state detected: notified=${notified}"
        } else {
            println "WARN: Notification status missing in counter file. Using default: notified=false"
        }
    } else {
        println "WARN: No global counter file found. Creating new counter."
    }
    
    // Get current Fortify pending jobs count
    def fortifyScanCount = getFortifyPendingJobsCount(fortifyApiToken, fortifyApiURL)
    
    // Only notify if not already notified AND threshold exceeded
    if (!notified && fortifyScanCount > pendingJobThreshold) {
        println "INFO: Pending job threshold exceeded (Count: ${fortifyScanCount}, Threshold: ${pendingJobThreshold}). Triggering notification."
        notifyTeamsChannel()
        notified = true
    } else if (notified) {
        println "INFO: Notification already sent for Fortify threshold. Skipping duplicate notification."
    } else {
        println "INFO: Pending jobs (${fortifyScanCount}) within threshold (${pendingJobThreshold}). No notification needed."
    }
    
    // Handle reset scenarios
    if (countValReset.equalsIgnoreCase("resetWithoutNotification")) {
        println "INFO: Reset request received, setting notified state to false."
        notified = false
    }
    
    // Write updated state
    writeFile(file: counterFile, text: "notified=${notified}\n")
    println "INFO: Global counter file updated successfully. Current state: notified=${notified}"
}

def getFortifyPendingJobsCount(def fortifyApiToken, def fortifyApiURL) {
    withCredentials([string(credentialsId: fortifyApiToken, variable: 'credentials')]) {
        def scanResponse = sh(
            script: """curl -ksH 'Authorization: FortifyToken \${credentials}' \
                      '${fortifyApiURL}/cloudjobs?fields=jobState&q=jobState:PENDING'""",
            returnStdout: true
        ).trim()
        def jsonResponse = readJSON text: scanResponse
        def jobsCount = jsonResponse.count ?: 0
        println "INFO: Pending jobs count in Fortify: ${jobsCount}"
        return jobsCount
    }
}

def notifyTeamsChannel() {
    try {
        def envProperty = loadEnvironmentProperties()
        def fortifyWorkflowUrl = envProperty.fortify_workflow_url
        def fortifyChannelEmails = envProperty.fortify_channel_emails_json
        
        def payload = [
            message: "Fortify scan queue has exceeded threshold",
            emails: fortifyChannelEmails,
            timestamp: new Date().format("yyyy-MM-dd HH:mm:ss")
        ]
        
        def payloadJson = groovy.json.JsonOutput.toJson(payload)
        
        def responseCode = sh(
            script: """curl -k -w '%{http_code}' -o /dev/null -s \
                      -H 'Content-Type: application/json' \
                      -X POST '${fortifyWorkflowUrl}' \
                      -d '${payloadJson.replace("'", "'\\''")}'""",
            returnStdout: true
        ).trim()
        
        println "INFO: Fortify Teams notification sent. HTTP response code: ${responseCode}"
    } catch (Exception e) {
        println "ERROR: Failed to send Fortify Teams notification: ${e.message}"
    }
}
===============================================================
    sendTeamsNotification.groovy
-----------------------

    def call() {
    try {
        // Only send notifications for failures
        def buildStatus = currentBuild.result ?: "SUCCESS"
        if (buildStatus != "FAILURE") {
            println "INFO: Build status is ${buildStatus}. No notification needed."
            return
        }
        
        // Load environment properties
        def envProperty = loadEnvironmentProperties()
        def counterFile = envProperty.fortify_global_counter_file  // /appbin/install/build_counter/global-counter.properties
        def notificationEnabled = (envProperty.fortify_notification_enabled ?: "true").toBoolean()
        
        if (!notificationEnabled) {
            println "INFO: Team notifications are DISABLED. Skipping notification."
            return
        }
        
        // Check if notification already sent for this build
        def buildId = "${env.JOB_NAME}-${env.BUILD_NUMBER}"
        def alreadyNotified = checkIfNotificationSent(counterFile, buildId)
        
        if (alreadyNotified) {
            println "INFO: Notification already sent for build ${buildId}. Skipping duplicate notification."
            return
        }
        
        def stageName = env.failedStage ?: "Unknown Stage"
        
        // Get user details who triggered the build
        def userInfo = getBuildTriggeredUserDetails()
        def buildTriggeredUserEmailId = userInfo.userEmail
        def buildTriggeredUserName = userInfo.userName
        
        // Skip notification for service accounts
        if (buildTriggeredUserName == 'SVC-APP-RLCT') {
            println "INFO: Notification suppressed for service account user: ${buildTriggeredUserName}"
            return
        }
        
        // Build notification payload with user details
        def buildData = [:]
        buildData["pipelineURL"] = env.BUILD_URL
        buildData["triggeredBy"] = buildTriggeredUserName
        buildData["triggeredByEmail"] = buildTriggeredUserEmailId
        buildData["status"] = buildStatus
        buildData["stage"] = stageName
        buildData["buildNumber"] = env.BUILD_NUMBER
        buildData["jobName"] = env.JOB_NAME
        buildData["timestamp"] = new Date().format("yyyy-MM-dd HH:mm:ss")
        buildData["branch"] = params.BRANCH ?: "N/A"
        buildData["gitRepository"] = params.REPOSITORY_NAME ?: "N/A"
        buildData["pcfFoundation"] = params.PCF_FOUNDATION ?: "N/A"
        buildData["pcfSpace"] = params.PCF_SPACE ?: "N/A"
        
        def buildDataJson = groovy.json.JsonOutput.toJson(buildData)
        def channelUrl = getProductWorkflowChannelUrl()
        
        if (channelUrl) {
            def success = notifyTeam(buildDataJson, channelUrl)
            
            // Mark notification as sent only if successful
            if (success) {
                markNotificationSent(counterFile, buildId)
                println "INFO: Notification sent successfully for build ${buildId} triggered by ${buildTriggeredUserName}"
            }
        } else {
            println "WARN: No channel URL found. Notification not sent."
        }
        
    } catch (Exception e) {
        println "ERROR: Failed to send Teams notification: ${e.message}"
        e.printStackTrace()
    }
}

def checkIfNotificationSent(def counterFile, def buildId) {
    if (!fileExists(counterFile)) {
        return false
    }
    
    try {
        def lines = readFile(counterFile).readLines()
        def buildEntry = lines.find { it?.trim()?.startsWith("build=${buildId}") }
        
        if (buildEntry) {
            println "INFO: Found previous notification for build: ${buildId}"
            return true
        }
    } catch (Exception e) {
        println "WARN: Error checking notification status: ${e.message}"
    }
    
    return false
}

def markNotificationSent(def counterFile, def buildId) {
    try {
        def existingContent = ""
        if (fileExists(counterFile)) {
            existingContent = readFile(counterFile)
        }
        
        // Append new build notification record
        def newContent = existingContent + "build=${buildId}\n"
        writeFile(file: counterFile, text: newContent)
        
        // Clean up old entries (keep last 50 builds)
        cleanupOldEntries(counterFile)
    } catch (Exception e) {
        println "ERROR: Failed to mark notification as sent: ${e.message}"
    }
}

def cleanupOldEntries(def counterFile) {
    try {
        if (!fileExists(counterFile)) {
            return
        }
        
        def lines = readFile(counterFile).readLines()
        def buildEntries = lines.findAll { it?.trim()?.startsWith("build=") }
        
        // Keep only last 50 entries
        if (buildEntries.size() > 50) {
            def toKeep = buildEntries.takeRight(50)
            def otherEntries = lines.findAll { !it?.trim()?.startsWith("build=") }
            def newContent = (otherEntries + toKeep).join("\n") + "\n"
            writeFile(file: counterFile, text: newContent)
            println "INFO: Cleaned up old notification entries. Kept last 50 builds."
        }
    } catch (Exception e) {
        println "WARN: Failed to cleanup old entries: ${e.message}"
    }
}

def getProductWorkflowChannelUrl() {
    try {
        def content = libraryResource('pipeline-global-config/workflow-urls.properties')
        def props = readProperties text: content
        def buildUrl = env.BUILD_URL ?: ""
        
        // Extract product name from URL: /job/API-Products/job/{PRODUCT_NAME}/
        def matcher = (buildUrl =~ /\/job\/API-Products\/job\/([^\/]+)/)
        def productName
        
        if (matcher.find()) {
            productName = matcher.group(1)
        } else {
            println "WARN: Unable to extract product name from URL: ${buildUrl}"
            return null
        }
        
        def channelUrl = props[productName]
        if (channelUrl) {
            println "INFO: Found channel URL for product: ${productName}"
            return channelUrl
        } else {
            println "WARN: No channel URL configured for product: ${productName}"
            return null
        }
    } catch (Exception e) {
        println "ERROR: Failed to get channel URL: ${e.message}"
        return null
    }
}

def notifyTeam(def buildDataJson, def channelUrl) {
    try {
        def responseCode = sh(
            script: """curl -k -w '%{http_code}' -o /dev/null -s \
                      -H 'Content-Type: application/json' \
                      -H 'Accept: application/json' \
                      -X POST '${channelUrl}' \
                      -d '${buildDataJson.replace("'", "'\\''")}'""",
            returnStdout: true
        ).trim()
        
        println "INFO: Teams notification HTTP response code: ${responseCode}"
        
        if (responseCode == "200" || responseCode == "201" || responseCode == "202") {
            println "INFO: Teams notification sent successfully."
            return true
        } else {
            println "ERROR: Failed to send Teams notification. HTTP code: ${responseCode}"
            return false
        }
    } catch (Exception e) {
        println "ERROR: Exception during Teams notification: ${e.message}"
        return false
    }
}

==================================

{
  "pipelineURL": "https://jenkins.yourcompany.com/job/API-Products/job/Ecom-API-DEV/456/",
  "triggeredBy": "John Doe",
  "triggeredByEmail": "john.doe@syf.com",
  "status": "FAILURE",
  "stage": "scan-fortify",
  "buildNumber": "456",
  "jobName": "API-Products/Ecom-API-DEV",
  "timestamp": "2025-11-19 16:45:30",
  "branch": "feature/payment-gateway",
  "gitRepository": "ecom-api-service",
  "pcfFoundation": "east-1-dev1",
  "pcfSpace": "dev"
}

--------------------------------------
    =======================================================================
    --------------------------------------------------




    from chagpt

import groovy.json.JsonOutput

def call() {
    try {

        // ---------------------------------------
        // 1. Resolve Build Status & Stage
        // ---------------------------------------
        String buildStatus = currentBuild.result ?: "SUCCESS"
        String stageName   = env.failedStage ?: "NA"
        String buildUrl    = env.BUILD_URL ?: ""

        // ---------------------------------------
        // 2. Get User Details
        // ---------------------------------------
        def userInfo = getBuildTriggeredUserDetails()
        String triggeredUserName  = userInfo?.userName ?: "Unknown"
        String triggeredUserEmail = userInfo?.userEmail ?: "Unknown"

        // Avoid sending alerts from Jenkins service account
        if (triggeredUserName.equalsIgnoreCase("SVC-APP-RLCT")) {
            println "INFO: Notification suppressed for service account user: ${triggeredUserName}"
            return
        }

        // ---------------------------------------
        // 3. Build Teams Notification Payload
        // ---------------------------------------
        Map payload = [
            title : "Pipeline Status: ${buildStatus}",
            text  : "Jenkins Pipeline Notification",
            sections: [
                [
                    facts: [
                        [ name: "Triggered By",     value: triggeredUserName ],
                        [ name: "Triggered Email",  value: triggeredUserEmail ],
                        [ name: "Pipeline URL",     value: buildUrl ],
                        [ name: "Status",           value: buildStatus ],
                        [ name: "Failed Stage",     value: stageName ]
                    ]
                ]
            ]
        ]

        String jsonPayload = JsonOutput.toJson(payload)

        // ---------------------------------------
        // 4. Get Teams Channel URL
        // ---------------------------------------
        String channelUrl = getProductWorkflowChannelUrl(buildUrl)

        if (!channelUrl) {
            println "WARN: No valid Teams channel found. Skipping notification."
            return
        }

        // ---------------------------------------
        // 5. Send the Teams Notification
        // ---------------------------------------
        notifyTeam(jsonPayload, channelUrl)

    } catch (Exception e) {
        println "ERROR: sendTeamNotification failed: ${e.message}"
    }
}





// ======================================================================
//        Extract Product/Capability Folder Name → Map to Teams Webhook
// ======================================================================
def getProductWorkflowChannelUrl(String buildUrl) {

    try {
        def content = libraryResource('pipeline-global-config/workflow-urls.properties')
        def props   = readProperties(text: content)

        /*
            Example URL:
            https://jenkins/job/API-Products/job/Payments/job/Build/

            Regex extracts → "Payments"
        */
        def matcher = buildUrl =~ /job\/([^\/]+)\/job\/([^\/]+)\//
        String folderName = null

        if (matcher.find()) {
            folderName = matcher.group(2)
        } else {
            println "WARN: Unable to extract product folder name from URL: ${buildUrl}"
            return null
        }

        String channelUrl = props[folderName]

        if (!channelUrl) {
            println "WARN: No Teams webhook mapped for folder '${folderName}'"
            return null
        }

        return channelUrl

    } catch (Exception e) {
        println "ERROR: getProductWorkflowChannelUrl failed: ${e.message}"
        return null
    }
}





// ======================================================================
//                  Send Notification to Teams via CURL
// ======================================================================
def notifyTeam(String jsonPayload, String channelUrl) {

    try {
        String safeJson = jsonPayload.replace("'", "'\\''")

        def responseCode = sh(
            script: """
                curl -k -w '%{http_code}' \
                -H 'Content-Type: application/json' \
                -H 'Accept: application/json' \
                -X POST '${channelUrl}' \
                -d '${safeJson}'
            """,
            returnStdout: true
        ).trim()

        println "INFO: Teams notification HTTP response code: ${responseCode}"

    } catch (Exception ex) {
        println "ERROR: notifyTeam failed: ${ex.message}"
    }
}

==========================================================================================================



    build triggeruserdetail

import hudson.tasks.Mailer
import hudson.model.User

def call() {

    // Get build cause (User who triggered the build)
    def buildCause = currentBuild.rawBuild.getCause(hudson.model.Cause.UserIdCause)
    def userId = buildCause?.userId ?: "UNKNOWN"

    // Get Jenkins User object
    def userData = User.get(userId, false, null)

    // Username
    def userName = userData?.getDisplayName() ?: "UNKNOWN"

    // Email
    def mailProp = userData?.getProperty(Mailer.UserProperty)
    def userEmail = mailProp?.getAddress() ?: "UNKNOWN"

    // Build URL from Jenkins env
    def buildUrl = env.BUILD_URL ?: "UNKNOWN"

    return [
        userName : userName,
        userEmail: userEmail,
        buildUrl : buildUrl
    ]
}


