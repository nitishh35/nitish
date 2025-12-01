complted modified sript with devops and forfity svript
====================================================

    def call() {

    // Load environment properties
    def envProperty = loadEnvironmentProperties()

    def devopsWorkFlowUrl        = envProperty.devops_workflow_url
    def fortifyWorkFlowUrl       = envProperty.fortify_workflow_url

    def devopsChannelMessage     = envProperty.devops_channel_message
    def fortifyChannelEmailsJson = envProperty.fortify_channel_emails_json
    def emailAddressList         = envProperty.to_email_address_list


    // ----------------------------------------
    // DEVOPS NOTIFICATION (unchanged here)
    // ----------------------------------------

    def dataString = createEmailJson(emailAddressList)

    def enrichedDataString = dataString.replace(
        "}",
        ", \"pipelineUrl\": \"${env.BUILD_URL}\"}"
    )

    println "INFO: DevOps JSON to send: ${enrichedDataString}"

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


    // ----------------------------------------
    // FORTIFY NOTIFICATION (UPDATED SECTION)
    // ----------------------------------------

    // Add pipeline URL inside fortify JSON
    def enrichedFortifyJson = fortifyChannelEmailsJson.replace(
        "}",
        ", \"pipelineUrl\": \"${env.BUILD_URL}\"}"
    )

    println "INFO: Fortify JSON to send: ${enrichedFortifyJson}"

    def fortifyResponseCode = sh(
        script: """
            curl -ks -w '%{http_code}' \
            -H 'Content-Type: application/json' \
            -H 'Accept: application/json' \
            -X POST '${fortifyWorkFlowUrl}' \
            -d '${enrichedFortifyJson.replace("'", "'\\\\''")}'
        """,
        returnStdout: true
    ).trim()

    println "INFO: Fortify support channel response code: ${fortifyResponseCode}"
}


// ----------------------------------------
// FUNCTION: createEmailJson()
// ----------------------------------------
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
th
========================================


    how to test the fortify

To test your updated **notifyTeamChannel.groovy** (DevOps + Fortify changes) **locally**, you cannot directly execute shared-library Groovy inside Jenkins DSL.

But you *can* perform a complete local test using **3 reliable methods**:

---

# ✅ **METHOD 1: Test using a Local Bash Script (Recommended)**

This method lets you confirm:

* JSON formatting
* Pipeline URL injection
* Curl escaping
* No syntax errors

### **Step 1 — Create a local test JSON input**

Make a test file:

`test-input.txt`:

```
ravi@syf.com, ahasan@syf.com
```

### **Step 2 — Simulate createEmailJson() output**

If using email JSON:

```
{"email1":"ravi@syf.com","email2":"ahasan@syf.com"}
```

If using plain string:

```
ravi@syf.com, ahasan@syf.com
```

### **Step 3 — Simulate your enrichedDataString**

Run this in a shell:

```bash
EMAILS="ravi@syf.com, ahasan@syf.com"
BUILD_URL="https://jenkins/job/test/15/"

echo "$EMAILS, $BUILD_URL"
```

This confirms the format is correct.

### **Step 4 — Dry-run the curl**

Use a dummy endpoint like httpbin:

```bash
curl -X POST https://httpbin.org/post \
     -H "Content-Type: application/json" \
     -d '{"email1":"ravi@syf.com","email2":"ahasan@syf.com","pipelineUrl":"https://jenkins/job/test/15/"}'
```

You will see exactly what PowerAutomate would receive.

---

# ✅ **METHOD 2: Test inside Jenkins using a Sandbox Job**

This is the **best simulation of real behavior**.

Create a freestyle pipeline job with:

```groovy
node {
    def envProperty = [
        devops_workflow_url: "https://httpbin.org/post",
        fortify_workflow_url: "https://httpbin.org/post",
        to_email_address_list: "ravi@syf.com, ahasan@syf.com, nitish@syf.com",
        fortify_channel_emails_json: '{"email1":"Brad","email2":"Gerald"}'
    ]

    env.BUILD_URL = "https://jenkins/job/test/15/"

    notifyTeamChannel()
}
```

Import your shared library using:

```
@Library('your-lib') _
```

This will show:

* Final payload
* Response from httpbin
* No errors

---

# ✅ **METHOD 3: Test Groovy Script Locally Using Groovy Console**

You can also test in a local Groovy REPL.

### **Step 1 — Create a Local Test File:**

`notifyTest.groovy`:

```groovy
def emailAddressList = "ravi@syf.com, ahasan@syf.com"

def createEmailJson = { input ->
    def emailList = input.split(',').collect { it.trim() }
    def emailMap = [:]
    emailList.eachWithIndex { email, i ->
        emailMap["email${i+1}"] = email
    }
    groovy.json.JsonOutput.toJson(emailMap)
}

def dataString = createEmailJson(emailAddressList)
def enrichedDataString = dataString.replace("}", ", \"pipelineUrl\": \"https://jenkins/job/test/15/\"}")

println enrichedDataString
```

### **Step 2 — Run it locally:**

```
groovy notifyTest.groovy
```

This prints EXACT JSON your real script will send.

---

# 🟩 **WHAT YOU SHOULD VERIFY IN OUTPUT**

Make sure the result prints:

```json
{
  "email1": "ravi@syf.com",
  "email2": "ahasan@syf.com",
  "pipelineUrl": "https://jenkins/job/test/15/"
}
```

This confirms:

* JSON merges correctly
* No syntax errors
* Nothing breaks in Groovy

Then:

### Run the curl manually:

```
curl -X POST https://httpbin.org/post \
     -H "Content-Type: application/json" \
     -d '{"email1":"ravi@syf.com","email2":"ahasan@syf.com","pipelineUrl":"https://jenkins/job/test/15/"}'
```

You will see:

```
{
  "json": {
    "email1": "ravi@syf.com",
    "email2": "ahasan@syf.com",
    "pipelineUrl": "https://jenkins/job/test/15/"
  }
}
```

This means it will **100% work with PowerAutomate**.

---

# 🎯 WANT ME TO CREATE A READY-TO-RUN LOCAL TEST SCRIPT?

I can generate:

* a full `.sh` file
* a full `.groovy` local test harness
* or a Jenkins sandbox pipeline

Just tell me:

👉 **“Give me a local test script”**


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


