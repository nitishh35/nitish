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

updated the jenkinsfile

================================
    @Library([
    'shared-libs@feature/EC-1037-notify-common-framework-teams-group-fix-fortify-team-alert-fix-test-automation'
]) _

pipeline {
    agent any

    stages {
        stage('Load Properties') {
            steps {
                script {
                    envProperty = loadEnvironmentProperties()
                    echo "Properties Loaded"
                }
            }
        }

        stage('Trigger Global Counter Script') {
            steps {
                script {
                    echo "Running updateGlobalCounterFile..."
                    updateGlobalCounterFile("resetWithNotification")
                }
            }
        }

        stage('Trigger Teams Notification Manually') {
            steps {
                script {
                    echo "Calling notifyTeamsChannel() directly"
                    notifyTeamsChannel()
                }
            }
        }
    }
}

=====================

✅ FINAL — Full Working sendTeamNotification.groovy
======================================================
#!/usr/bin/env groovy

def call() {

    try {

        def buildStatus = currentBuild.result ?: "SUCCESS"
        def stageName   = env.failedStage ?: "NA"

        // Get triggered user
        def userInfo = getBuildTriggeredUserDetails()
        def triggeredUserEmail = userInfo.userEmail
        def triggeredUserName  = userInfo.userName

        // Suppress notifications for SVC accounts
        if (triggeredUserName?.startsWith('SVC-')) {
            println "INFO: Notification suppressed for service account: ${triggeredUserName}"
            return
        }

        // Build notification payload
        def buildData = [
            pipelineURL      : env.BUILD_URL,
            triggerdBy       : triggeredUserName,
            triggerdByEmail  : triggeredUserEmail,
            status           : buildStatus,
            stage            : (buildStatus == "FAILURE" ? stageName : "NA")
        ]

        def buildDataJson = groovy.json.JsonOutput.toJson(buildData)

        // Resolve the webhook URL
        def channelUrl = getProductWorkflowChannelUrl()

        if (!channelUrl) {
            println "WARN: No Teams channel URL found for this job — skipping notification."
            return
        }

        // Send notification
        notifyTeam(buildDataJson, channelUrl)

    } catch (Exception e) {
        println "ERROR: Failed to send Teams notification: ${e.message}"
    }
}

/* ------------------------------------------------------------------
   Determine the Teams webhook URL based on job folder + job name
-------------------------------------------------------------------*/
def getProductWorkflowChannelUrl() {

    def content = libraryResource('pipeline-global-config/workflow-urls.properties')
    def props   = readProperties(text: content)

    def buildUrl = env.BUILD_URL ?: ""

    /*
        Supports both folder structures:

        /job/API-Products/job/<JobName>/
        /job/Common-Framework/job/<JobName>/
    */
    def matcher = buildUrl =~ /\/job\/(API-Products|Common-Framework)\/job\/([^\/]+)\//

    if (!matcher.find()) {
        println "WARN: Could not extract job name from: ${buildUrl}"
        return null
    }

    def parentFolder = matcher.group(1)       // API-Products or Common-Framework
    def jobName      = matcher.group(2)       // Alerts, CustID, PayMyBill, etc.

    println "INFO: Extracted Folder: ${parentFolder}"
    println "INFO: Extracted Job: ${jobName}"

    // Lookup in workflow-urls.properties
    def channelUrl = props[jobName]

    if (!channelUrl) {
        println "WARN: No entry found in workflow-urls.properties for key '${jobName}'"
        return null
    }

    return channelUrl
}

/* ------------------------------------------------------------------
   Send notification to Teams webhook using curl
-------------------------------------------------------------------*/
def notifyTeam(String payloadJson, String channelUrl) {

    println "INFO: Sending notification to Teams channel: ${channelUrl}"

    def httpCode = sh(
        script: """
            curl -k -w '%{http_code}' \
                 -H 'Content-Type: application/json' \
                 -H 'Accept: application/json' \
                 -X POST '${channelUrl}' \
                 -d '${payloadJson.replace("'", "'\\\\''")}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: Received HTTP



