node(env.dse_worker_node) {
    
    // Git Variables
    def gitProjectKey = params.PROJECT_KEY
    def gitRepositoryName = params.REPOSITORY_NAME
    def gitBranch = params.BRANCH
    def gitPullRequestId = params.PRID ? params.PRID : 'NA'
    
    // Pipeline Build Environment
    env.pipelineEnv = 'dev'
    
    // Sonar Variables
    def sonarAppName = 'Ecom-API-DEV'
    
    // Fortify Variables
    def fortifyVersion = 'DEV'
    
    // PCF Variables
    def pcfSpace = params.PCF_SPACE
    def pcfFoundation = params.PCF_FOUNDATION
    def pcfRoute = 'ecom-api-' + gitRepositoryName
    def pcfManifestFileDev1East1 = 'manifest-dev1-east1.yml'
    def pcfManifestFileDev1East2 = 'manifest-dev1-east2.yml'
    def pcfManifestFileDevEast = 'manifest-dev-east.yml'
    def pcfManifestFileDevWest = 'manifest-dev-west.yml'
    
    ansiColor('xterm') {
        try {
            timeout(time: 40, unit: 'MINUTES') {
                
                stage('checkout') {
                    logStage('checkout') {
                        validateBuildReplayed()
                        gitSCMCodeCheckout(gitProjectKey, gitRepositoryName, gitBranch)
                    }
                }
                
                stage('validate: build.properties') {
                    logStage('validate-build.properties') {
                        validateBuildFile(gitBranch)
                    }
                }
                
                stage('validate: master-sync') {
                    logStage('validate-master-sync') {
                        validateWithMaster(gitBranch)
                    }
                }
                
                stage('validate: manifest') {
                    logStage('validate-manifest') {
                        // ... your manifest validation code ...
                    }
                }
                
                stage('validate: app onboarded in info-sec tools') {
                    logStage('validate-app-onboarded-in-info-sec-tools') {
                        infoSecScanAppName = getInfoSecScanAppName(gitProjectKey, gitRepositoryName)
                        validateAppNameInFortify(infoSecScanAppName)
                        validateAppNameInNexusIQ(infoSecScanAppName)
                    }
                }
                
                stage('build') {
                    logStage('build') {
                        buildProgress()
                        appVersion = determineAppVersion(getAppVersionMaven(), gitBranch)
                        buildMaven(appVersion)
                    }
                }
                
                stage('validate: secure dependency artifacts') {
                    logStage('validate-secure-dependency-artifacts') {
                        validateSecureArtifactVersions(gitRepositoryName)
                    }
                }
                
                stage('add task to pull request') {
                    logStage('add-task-to-pull-request') {
                        if (gitPullRequestId != 'NA') {
                            addCodingGuidelinesPullRequestTask(gitProjectKey, gitRepositoryName, gitPullRequestId)
                            addPRReviewChecklistPullRequestTask(gitProjectKey, gitRepositoryName, gitPullRequestId)
                        }
                    }
                }
                
                // The stages inside parallel stage are defined in this way to get the stage details in Jenkins classic UI
                parallel(
                    'scan: sonar': {
                        stage('scan: sonar') {
                            logStage('scan-sonar') {
                                sonarScan(sonarAppName, gitProjectKey, gitRepositoryName, gitBranch, appVersion, gitPullRequestId)
                            }
                        }
                    },
                    'scan: fortify': {
                        stage('scan: fortify') {
                            logStage('scan-fortify') {
                                try {
                                    // Reset alert state if count is back to normal (before scan starts)
                                    managefortifyalertsstate(false)
                                    
                                    // Proceed with Fortify scan
                                    fortifyScan(infoSecScanAppName, gitProjectKey, gitRepositoryName, gitPullRequestId, fortifyVersion, gitBranch)
                                    
                                } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                                    // Fortify scan timed out
                                    println "ERROR: Fortify scan timed out: ${e.message}"
                                    
                                    // Send alert ONLY if BOTH conditions met:
                                    // 1. Timeout occurred (we're in catch block)
                                    // 2. Pending count > threshold (checked inside function)
                                    managefortifyalertsstate(true)
                                    
                                    // Re-throw to fail the build
                                    throw e
                                }
                            }
                        }
                    },
                    'scan: nexus-iq': {
                        stage('scan: nexus-iq') {
                            logStage('scan-nexus-iq') {
                                nexusIqScan(infoSecScanAppName, gitProjectKey, gitRepositoryName, gitPullRequestId)
                            }
                        }
                    }
                )
                
                stage('udeploy: import-artifact') {
                    logStage('udeploy-import-artifact') {
                        if (pcfFoundation.equalsIgnoreCase('east-1-dev1')) {
                            pcfManifestFile = pcfManifestFileDev1East1
                        } else if (pcfFoundation.equalsIgnoreCase('east-2-dev1')) {
                            pcfManifestFile = pcfManifestFileDev1East2
                        } else {
                            pcfManifestFile = pcfManifestFileDevWest
                        }
                        uDeployDevVersionImport(gitRepositoryName, appVersion, pcfManifestFile, pcfRoute)
                    }
                }
                
                stage('udeploy: deploy') {
                    logStage('udeploy-deploy') {
                        uDeployDevDeployArtifact(gitRepositoryName, appVersion, pcfFoundation, pcfSpace)
                    }
                }
                
                stage('generate newman report') {
                    logStage('generate-newman-report') {
                        testNewman('dev')
                    }
                }
                
                buildSuccess()
            }
        } catch (err) {
            handleBuildFailure(err)
        } finally {
            if (currentBuild.result == 'FAILURE') {
                handleFailure()
            }
            postbuildsummary()
        }
    }
}

==================================================
    ============================================
    =======================================
    def call(boolean checkOnTimeout = false) {
    def envProperty = loadEnvironmentProperties()
    def fortifyApiUrl = envProperty.fortify_api_url
    def fortifyApiToken = envProperty.fortify_api_credential_id
    def alertStateFile = envProperty.fortify_alert_state_file
    def fortifyScanCountThreshold = envProperty.fortify_scan_count_threshold as Integer
    def alertEnabled = envProperty.fortify_alert_enabled as Boolean
    def devopsWorkflowUrl = envProperty.devops_workflow_url
    def fortifyWorkflowUrl = envProperty.fortify_workflow_url
    def devopsChannelEmails = envProperty.to_email_address_list
    def fortifyChannelEmails = envProperty.fortify_team_emails

    if (!alertEnabled) {
        println "INFO: Fortify alerts disabled. Skipping alert evaluation."
        return
    }

    // Get current pending count
    def fortifyScanCount = getFortifyPendingJobsCount(fortifyApiToken, fortifyApiUrl)
    
    // If called during timeout, send alert only if count > threshold
    if (checkOnTimeout) {
        if (fortifyScanCount > fortifyScanCountThreshold) {
            // Check if alert already sent
            def notified = false
            if (fileExists(alertStateFile)) {
                def content = readFile(alertStateFile).trim()
                if (content.equalsIgnoreCase("true")) {
                    notified = true
                }
            }
            
            // Send alert only if not already sent
            if (!notified) {
                println "WARN: Fortify scan timed out AND pending count (${fortifyScanCount}) exceeded threshold (${fortifyScanCountThreshold}). Sending alert."
                sendTeamsNotification(devopsWorkflowUrl, devopsChannelEmails, "DevOps")
                sendTeamsNotification(fortifyWorkflowUrl, fortifyChannelEmails, "Fortify")
                writeFile(file: alertStateFile, text: "true\n")
            } else {
                println "INFO: Alert already sent for timeout with high pending count. Skipping duplicate notification."
            }
        } else {
            println "INFO: Fortify scan timed out but pending count (${fortifyScanCount}) is within threshold (${fortifyScanCountThreshold}). No alert sent."
        }
    } else {
        // Normal check - just reset alert state if count is within threshold
        if (fortifyScanCount <= fortifyScanCountThreshold) {
            println "INFO: Pending scans count (${fortifyScanCount}) within threshold (${fortifyScanCountThreshold}), resetting alert state"
            if (fileExists(alertStateFile)) {
                writeFile(file: alertStateFile, text: "false\n")
            }
        } else {
            println "INFO: Pending scans count (${fortifyScanCount}) exceeded threshold (${fortifyScanCountThreshold}), but no timeout occurred yet."
        }
    }
}

def getFortifyPendingJobsCount(def fortifyApiToken, def fortifyApiUrl) {
    withCredentials([string(credentialsId: fortifyApiToken, variable: 'credentials')]) {
        def scanResponse = sh(
            script: "curl -ksH 'Authorization: FortifyToken ${credentials}' '${fortifyApiUrl}/cloudjobs?fields=jobState&q=jobState:PENDING'",
            returnStdout: true
        ).trim()

        def jsonResponse = readJSON(text: scanResponse)
        def jobsCount = jsonResponse.count ?: 0
        println "INFO: Pending scans count in Fortify: ${jobsCount}"
        return jobsCount
    }
}
===============================================
    ===========================================
    =========================================
Perfect! Now I understand completely. Let me update the code to send alerts **ONLY when BOTH conditions are met**:

1. Fortify scan times out
2. AND pending count > 5## ✅ Perfect Solution! Now Both Conditions Must Be Met:

### 🎯 Alert Logic:

```
Alert sent ONLY when:
  ✓ Fortify scan times out (FlowInterruptedException)
  AND
  ✓ Pending count > 5

Alert NOT sent when:
  ✗ Scan times out BUT count ≤ 5
  ✗ Count > 5 BUT scan completes successfully
```

### 📊 Complete Flow with All Scenarios:

| Scenario | Pending Count | Scan Status | Alert Sent? | Build Status |
|----------|---------------|-------------|-------------|--------------|
| Normal scan | ≤ 5 | ✅ Completes | ❌ No | ✅ Pass |
| Normal scan | > 5 | ✅ Completes | ❌ No | ✅ Pass |
| Timeout | ≤ 5 | ⏱️ Times out | ❌ No | ❌ Fail (timeout) |
| **Timeout (1st)** | **> 5** | **⏱️ Times out** | **✅ Yes** | **❌ Fail (timeout)** |
| **Timeout (2nd)** | **> 5** | **⏱️ Times out** | **❌ No (already sent)** | **❌ Fail (timeout)** |
| Count drops | > 5 → ≤ 5 | ✅ Completes | State reset | ✅ Pass |
| Timeout after reset | > 5 | ⏱️ Times out | ✅ Yes (new alert) | ❌ Fail (timeout) |

### 🔄 State Management:

```groovy
managefortifyalertsstate(false)  // Called BEFORE scan
  └─ If count ≤ 5: Reset alert state (allow future alerts)
  └─ If count > 5: Just log, do nothing

managefortifyalertsstate(true)   // Called ON TIMEOUT
  └─ Check count:
     ├─ If count > 5 AND not notified: Send alert
     ├─ If count > 5 AND already notified: Skip alert
     └─ If count ≤ 5: No alert (timeout for other reasons)
```

### 🎉 Key Features:

✅ **No false alerts**: Alert sent only when BOTH timeout + high count  
✅ **No duplicate alerts**: One alert per high-count period  
✅ **Auto-reset**: When count drops ≤ 5, ready for next alert  
✅ **Build never fails from alert logic**: Only timeouts fail the build  
✅ **Clear logging**: Every decision is logged for debugging

This is exactly what your manager requested! 🚀
