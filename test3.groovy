// vars/getLastDeployedVersion.groovy

def call(String REPOSITORY_NAME) {
    import jenkins.*
    import jenkins.model.*
    import hudson.*
    import hudson.model.*
    import groovy.json.JsonSlurper

    def jenkinsCredentials = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
        com.cloudbees.plugins.credentials.Credentials.class,
        Jenkins.instance,
        null,
        null
    )

    for (creds in jenkinsCredentials) {
        if (creds.id == "UDEPLOY_PROD_CREDENTIALS") {

            // ---- Your Original Script Logic Below ----
            def response, responseText

            response = ("curl -ku ${creds.username}:${creds.password} https://udeploy.app.syfbank.com:8443/cli/component/getProperty?component=Ecom-API_${REPOSITORY_NAME}&name=last-deployed-qa2dal-version").execute()
            responseText = response.text
            qa2DalVersion = responseText.contains('Property not found') ? 'NA' : responseText

            // (keep all your curl calls here exactly as you had them)
            // I’m not editing or removing any envs

            return """<html><head><style>
table {font-family: arial, sans-serif; border-collapse: collapse; width: 50%;}
td, th {border: 1px solid #dddddd; text-align: center; padding: 12px;}
</style></head><body>
<table>
<tr><th colspan='2'>QA</th><th colspan='2'>UAT</th></tr>
<tr><td>qa2-dal</td><td>${qa2DalVersion}</td><td>uat2-dal</td><td>${uat2DalVersion}</td></tr>
<tr><td>qa3-dal</td><td>${qa3DalVersion}</td><td>uat3-dal</td><td>${uat3DalVersion}</td></tr>
<tr><td>qa2-phx</td><td>${qa2PhxVersion}</td><td>uat2-phx</td><td>${uat2PhxVersion}</td></tr>
<tr><td>qa-east1</td><td>${qaEast1Version}</td><td>uat-east1</td><td>${uatEast1Version}</td></tr>
<tr><td>qa-west2</td><td>${qaWest2Version}</td><td>uat-west2</td><td>${uatWest2Version}</td></tr>
<tr><td>qa1-east1</td><td>${qa1East1Version}</td><td>uat1-east1</td><td>${uat1East1Version}</td></tr>
<tr><td>qa1-east2</td><td>${qa1East2Version}</td><td>uat1-east2</td><td>${uat1East2Version}</td></tr>
</table></body></html>"""
        }
    }
}
---------------------------------------------------------


    @Library('sharedlib') _

pipeline {
    agent any
    parameters {
        string(name: 'REPOSITORY_NAME', defaultValue: 'enterprise-paymybill-payments-test')
    }

    stages {
        stage('Show Last Deployed Version') {
            steps {
                script {
                    def htmlReport = getLastDeployedVersion(params.REPOSITORY_NAME)
                    echo "HTML Report:\n${htmlReport}"
                }
            }
        }
    }
}
