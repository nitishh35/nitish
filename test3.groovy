import jenkins.*
import jenkins.model.*
import hudson.*
import hudson.model.*

// Get active foundations from shared library
def activeFoundations = getActiveFoundations()

def generateHtmlTable(versionMap, activeFoundations) {
    def html = """
    <html>
    <head>
        <style>
            table {
                font-family: arial, sans-serif;
                border-collapse: collapse;
                width: 80%;
                margin: 20px;
            }
            td, th {
                border: 1px solid #dddddd;
                text-align: center;
                padding: 12px;
            }
            th {
                background-color: #f2f2f2;
            }
            h2 {
                margin: 20px;
                font-family: arial, sans-serif;
            }
        </style>
    </head>
    <body>
        <h2>Deployment Versions - ${REPOSITORY_NAME}</h2>
        <table>
            <tr>
                <th>QA Environment</th>
                <th>QA Version</th>
                <th>UAT Environment</th>
                <th>UAT Version</th>
            </tr>
    """
    
    // Create rows for active foundations
    activeFoundations.qa.eachWithIndex { qaFound, index ->
        def uatFound = activeFoundations.uat[index]
        html += "<tr>"
        html += "<td>$qaFound</td>"
        html += "<td>${versionMap[qaFound] ?: 'NA'}</td>"
        html += "<td>$uatFound</td>"
        html += "<td>${versionMap[uatFound] ?: 'NA'}</td>"
        html += "</tr>"
    }

    html += "</table></body></html>"
    return html
}

// Main execution logic
try {
    def jenkinsCredentials = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
        com.cloudbees.plugins.credentials.Credentials.class,
        Jenkins.instance,
        null,
        null
    )

    def versionMap = [:]
    def targetCreds = null

    // Find credentials
    for(creds in jenkinsCredentials) {
        if(creds.id == "UDEPLOY_FO_CREDENTIALS") {
            targetCreds = creds
            break
        }
    }

    if (!targetCreds) {
        return "<html><body><h2>Error: UDEPLOY_FO_CREDENTIALS not found</h2></body></html>"
    }

    // Query only active QA foundations
    activeFoundations.qa.each { foundation ->
        try {
            def propName = "last-deployed-${foundation.replace('-', '')}-version"
            def command = "curl -ku $targetCreds.username:$targetCreds.password " +
                         "https://udeploy.app.syfbank.com:8443/cli/component/getProperty?" +
                         "component=Ecom-API_${REPOSITORY_NAME}&name=$propName"
            
            def process = command.execute()
            process.waitFor()
            def responseText = process.text.trim()
            
            versionMap[foundation] = responseText.contains('Property not found') ? 'NA' : responseText
        } catch (Exception e) {
            versionMap[foundation] = 'Error'
        }
    }
    
    // Query only active UAT foundations  
    activeFoundations.uat.each { foundation ->
        try {
            def propName = "last-deployed-${foundation.replace('-', '')}-version"
            def command = "curl -ku $targetCreds.username:$targetCreds.password " +
                         "https://udeploy.app.syfbank.com:8443/cli/component/getProperty?" +
                         "component=Ecom-API_${REPOSITORY_NAME}&name=$propName"
            
            def process = command.execute()
            process.waitFor()
            def responseText = process.text.trim()
            
            versionMap[foundation] = responseText.contains('Property not found') ? 'NA' : responseText
        } catch (Exception e) {
            versionMap[foundation] = 'Error'
        }
    }
    
    // Generate HTML table with only active foundations
    return generateHtmlTable(versionMap, activeFoundations)
    
} catch (Exception e) {
    return "<html><body><h2>Error: ${e.message}</h2></body></html>"
}
