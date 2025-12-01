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


    def call() {
    def envProperty = loadEnvironmentProperties()

    // DevOps workflow URL from property file
    def devopsWorkFlowUrl = envProperty.devops_workflow_url

    // Emails from property file
    def emailAddressList = envProperty.to_email_address_list

    // Jenkins pipeline URL
    def pipelineUrl = env.BUILD_URL ?: ""

    // ---------------------------
    // Build DevOps JSON Payload
    // ---------------------------
    def devOpsJson = createEmailJson(emailAddressList)
    devOpsJson.put("pipelineUrl", pipelineUrl)

    def devOpsBody = groovy.json.JsonOutput.toJson(devOpsJson)
    println "INFO: DevOps JSON to send: ${devOpsBody}"

    // ---------------------------
    // Send to DevOps Teams Webhook
    // ---------------------------
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
}


// -------------------------------------------
// Helper to create JSON from CSV email list
// -------------------------------------------
def createEmailJson(def addressList) {
    def emails = addressList.split(",").collect { it.trim() }
    def map = [:]

    emails.eachWithIndex { email, index ->
        map["email${index + 1}"] = email
    }

    return map
}


