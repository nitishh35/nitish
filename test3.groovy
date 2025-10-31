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
