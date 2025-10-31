#!/usr/bin/env groovy

import jenkins.model.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*

def call(String repositoryName) {
    // Get Jenkins instance
    def jenkinsInstance = Jenkins.instance

    // Find credentials
    def creds = CredentialsProvider.lookupCredentials(
        com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials.class,
        jenkinsInstance,
        null,
        null
    ).find { it.id == "UDEPLOY_PROD_CREDENTIALS" }

    if (!creds) {
        error "UDEPLOY_PROD_CREDENTIALS not found in Jenkins credentials!"
    }

    def username = creds.username
    def password = creds.password

    // Function to fetch property value from uDeploy
    def getProperty = { component, propertyName ->
        def cmd = """curl -ksu ${username}:${password} "https://udeploy.app.syfbank.com:8443/cli/component/getProperty?component=${component}&name=${propertyName}" """
        def response = cmd.execute().text.trim()
        return response.contains('Property not found') ? 'NA' : response
    }

    // Component name
    def componentName = "component-Ecom-API_${repositoryName}"

    // Fetch versions
    def qa2DalVersion   = getProperty(componentName, 'last-deployed-qa2dal-version')
    def qa3DalVersion   = getProperty(componentName, 'last-deployed-qa3dal-version')
    def qa2PhxVersion   = getProperty(componentName, 'last-deployed-qa2phx-version')
    def qaEast1Version  = getProperty(componentName, 'last-deployed-qa-east1-version')
    def qaWest2Version  = getProperty(componentName, 'last-deployed-qa-west2-version')
    def qa1East1Version = getProperty(componentName, 'last-deployed-qa1-east1-version')
    def qa1East2Version = getProperty(componentName, 'last-deployed-qa1-east2-version')

    def uat2DalVersion  = getProperty(componentName, 'last-deployed-uat2dal-version')
    def uat3DalVersion  = getProperty(componentName, 'last-deployed-uat3dal-version')
    def uat2PhxVersion  = getProperty(componentName, 'last-deployed-uat2phx-version')
    def uatEast1Version = getProperty(componentName, 'last-deployed-uat-east1-version')
    def uatWest2Version = getProperty(componentName, 'last-deployed-uat-west2-version')
    def uat1East1Version = getProperty(componentName, 'last-deployed-uat1-east1-version')
    def uat1East2Version = getProperty(componentName, 'last-deployed-uat1-east2-version')

    // Return an HTML summary (can be echoed in Jenkins)
    return """
    <html>
    <head>
        <style>
            table {font-family: Arial, sans-serif; border-collapse: collapse; width: 70%;}
            td, th {border: 1px solid #dddddd; text-align: center; padding: 8px;}
        </style>
    </head>
    <body>
        <table>
            <tr><th colspan='2'>QA</th><th colspan='2'>UAT</th></tr>
            <tr><td>qa2-dal</td><td>${qa2DalVersion}</td><td>uat2-dal</td><td>${uat2DalVersion}</td></tr>
            <tr><td>qa3-dal</td><td>${qa3DalVersion}</td><td>uat3-dal</td><td>${uat3DalVersion}</td></tr>
            <tr><td>qa2-phx</td><td>${qa2PhxVersion}</td><td>uat2-phx</td><td>${uat2PhxVersion}</td></tr>
            <tr><td>qa-east1</td><td>${qaEast1Version}</td><td>uat-east1</td><td>${uatEast1Version}</td></tr>
            <tr><td>qa-west2</td><td>${qaWest2Version}</td><td>uat-west2</td><td>${uatWest2Version}</td></tr>
            <tr><td>qa1-east1</td><td>${qa1East1Version}</td><td>uat1-east1</td><td>${uat1East1Version}</td></tr>
            <tr>


-----------------------------------------------------


from calaude

// ===================================================================
// FILE 1: vars/getUDeployVersions.groovy (in your shared library repo)
// ===================================================================

import jenkins.*
import jenkins.model.*
import hudson.*
import hudson.model.*
import groovy.json.JsonSlurper

def call(String repositoryName) {
    def jenkinsCredentials = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
        com.cloudbees.plugins.credentials.Credentials.class,
        Jenkins.instance,
        null,
        null
    )
    
    for(creds in jenkinsCredentials) {
        if(creds.id == "UDEPLOY_PROD_CREDENTIALS") {
            def versions = fetchAllVersions(creds, repositoryName)
            return generateHtmlTable(versions)
        }
    }
    return "Credentials not found"
}

def fetchAllVersions(creds, repositoryName) {
    def versions = [:]
    def environments = [
        'qa2dal', 'qa3dal', 'qa2phx', 'qa-east1', 'qa-west2', 'qa1-east1', 'qa1-east2',
        'uat2dal', 'uat3dal', 'uat2phx', 'uat-east1', 'uat-west2', 'uat1-east1', 'uat1-east2'
    ]
    
    environments.each { env ->
        def response = ("curl -ku ${creds.username}:${creds.password} " +
            "https://udeploy.app.syfbank.com:8443/cli/component/getProperty?" +
            "component=Ecom-API_${repositoryName}&name=last-deployed-${env}-version").execute()
        
        def responseText = response.text
        versions[env] = responseText.contains('Property not found') ? 'NA' : responseText
    }
    
    return versions
}

def generateHtmlTable(versions) {
    def html = """
    <html>
    <head>
        <style>
            table {
                font-family: arial, sans-serif;
                border-collapse: collapse;
                width: 50%;
            }
            td, th {
                border: 1px solid #dddddd;
                text-align: center;
                padding: 12px;
            }
        </style>
    </head>
    <body>
        <table>
            <tr><th colspan='2'>QA</th><th colspan='2'>UAT</th></tr>
            <tr><td>qa2-dal</td><td>${versions['qa2dal']}</td><td>uat2-dal</td><td>${versions['uat2dal']}</td></tr>
            <tr><td>qa3-dal</td><td>${versions['qa3dal']}</td><td>uat3-dal</td><td>${versions['uat3dal']}</td></tr>
            <tr><td>qa2-phx</td><td>${versions['qa2phx']}</td><td>uat2-phx</td><td>${versions['uat2phx']}</td></tr>
            <tr><td>qa-east1</td><td>${versions['qa-east1']}</td><td>uat-east1</td><td>${versions['uat-east1']}</td></tr>
            <tr><td>qa-west2</td><td>${versions['qa-west2']}</td><td>uat-west2</td><td>${versions['uat-west2']}</td></tr>
            <tr><td>qa1-east1</td><td>${versions['qa1-east1']}</td><td>uat1-east1</td><td>${versions['uat1-east1']}</td></tr>
            <tr><td>qa1-east2</td><td>${versions['qa1-east2']}</td><td>uat1-east2</td><td>${versions['uat1-east2']}</td></tr>
        </table>
    </body>
    </html>
    """
    return html
}

// ===================================================================
// FILE 2: In Jenkins Job - Active Choice Parameter (Groovy Script)
// ===================================================================

@Library('your-shared-library-name') _

// Simply call the shared library function
return getUDeployVersions(REPOSITORY_NAME)
