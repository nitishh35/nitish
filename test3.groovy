def call() {

    // Load environment properties
    def envProperty = loadEnvironmentProperties()

    def devopsWorkFlowUrl        = envProperty.devops_workflow_url
    def fortifyWorkFlowUrl       = envProperty.fortify_workflow_url
    def devopsChannelMessage     = envProperty.devops_channel_message
    def fortifyChannelEmailsJson = envProperty.fortify_channel_emails_json
    def emailAddressList         = envProperty.to_email_address_list


    // ----------------------------
    // DEVOPS NOTIFICATION SECTION
    // ----------------------------

    // Convert email list to JSON using your existing function
    def dataString = createEmailJson(emailAddressList)

    // Append pipeline URL into SAME JSON (flat JSON merge)
    def enrichedDataString = dataString.replace(
        "}",
        ", \"pipelineUrl\": \"${env.BUILD_URL}\"}"
    )

    println "INFO: DevOps JSON to send: ${enrichedDataString}"

    // Send to DevOps workflow
    def devopsChannelResponseCode = sh(
        script: """
            curl -ks -w '%{http_code}' \
            -H 'Content-Type: application/json' \
            -H 'Accept: application/json' \
            -X POST '${devopsWorkFlowUrl}' \
            -d '${enrichedDataString.replace("'", "'\\\\''")}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: DevOps Teams channel response code: ${devopsChannelResponseCode}"


    // ----------------------------
    // FORTIFY NOTIFICATION SECTION
    // ----------------------------

    def fortifyResponseCode = sh(
        script: """
            curl -ks -w '%{http_code}' \
            -H 'Content-Type: application/json' \
            -H 'Accept: application/json' \
            -X POST '${fortifyWorkFlowUrl}' \
            -d '${fortifyChannelEmailsJson.replace("'", "'\\\\''")}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: Fortify support channel response code: ${fortifyResponseCode}"
}


// -------------------------------------------------------
// FUNCTION: createEmailJson()
// -------------------------------------------------------
def createEmailJson(def emailAddressList) {

    def emailList = emailAddressList.split(',').collect { it.trim() }

    def emailMap = [:]
    emailList.eachWithIndex { email, index ->
        emailMap["email${index + 1}"] = email
    }

    def jsonString = groovy.json.JsonOutput.toJson(emailMap)

    println "INFO: Email JSON created: ${jsonString}"

    return jsonString
}
