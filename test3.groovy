//Show the last deployed version from udeploy properties for individual foundation

//Referenced parameters: REPOSITORY_NAME

//Parameter Name: LAST_DEPLOYED_VERSION

//Job Type: UAT/sync-up job

//DSE - LAST_DEPLOYED_VERSION - promote to UAT and sync QA/UAT job

import jenkins.*

import jenkins.model.*

import hudson.*

import hudson.model.*

import groovy.json.JsonSlurper

def jenkinsCredentials = com.cloudbees.plugins.c .cloudbees.plugins.credentials. CredentialsProvider.lookupCredentia

com.cloudbees.plugins.credentials. Credentials.class,

Jenkins.instance,

null,

null
);

for(creds in jenkins Credentials){

if(creds.id == "UDEPLOY_PROD_CREDENTIALS"){

response ("curl -ku $creds.username:$creds.password https://udeploy.app.syfbank.com:8443/cli/component/getProperty? component-Ecom-API_${REPOSITORY_NAME}&name-last-deployed-qa2dal-version").execute()

responseText = response.text

qa2DalVersion responseText.contains ('Property not found') ? 'NA': responseText


response = ("curl -ku $creds.username:$creds.password https://udeploy.app.syfbank.com:8443/cli/component/getProperty?

component-Ecom-API_${REPOSITORY_NAME}&name=last-deployed-qa2phx-version").execute()

responseText = response.text

qa2PhxVersion = responseText.contains('Property not found') ? 'NA': responseText


response ("curl -ku $creds.username:$creds.password https://udeploy.app.syfbank.com:8443/cli/component/getProperty? component-Ecom-API_${REPOSITORY_NAME}&name=last-deployed-qa1-east1-version").execute()

responseText response.text

qalEast1Version responseText.contains('Property not found')? 'NA': responseText


response ("curl -ku $creds.username:$creds.password https://udeploy.app.syfbank.com:8443/cli/component/getProperty? component-Ecom-API_${REPOSITORY_NAME}&name-last-deployed-qa1-east2-version").execute()

responseText response.text

qa1East2Version responseText.contains('Property not found') ? 'NA': responseText


response ("curl -ku $creds.username: $creds.password https://udeploy.app.syfbank.com:8443/cli/component/getProperty? component-Ecom-API_${REPOSITORY_NAME}&name-last-deployed-uat2dal-version").execute()

responseText = response.text

uat2DalVersion = responseText.contains('Property not found') ? 'NA': responseText.


response = ("curl -ku $creds.username:$creds.password https://udeploy.app.syfbank.com:8443/cli/component/getProperty? component-Ecom-API_${REPOSITORY_NAME}&name=last-deployed-uat2phx-version").execute()

responseText = response.text

uat2PhxVersion = responseText.contains('Property not found') ? 'NA': responseText


response ("curl -ku $creds.username:$creds.password https://udeploy.app.syfbank.com:8443/c11/component/getProperty? component-Ecom-API_${REPOSITORY_NAME}&name-last-deployed-uat1-east1-version").execute()

responseText response.text

uat1East1Version responseText.contains('Property not found')? 'NA': responseText


response ("curl -ku $creds.username:$creds.password https://udeploy.app.syfbank.com:8443/cli/component/getProperty? component-Ecom-API_${REPOSITORY_NAME}&name-last-deployed-uat1-east2-version").execute()

responseText response.text

uat1East2Version responseText.contains('Property not found')? 'NA' responseText


return "<html><head><style>table {font-family: arial, sans-serif; border-collapse: collapse; width: 50%;}td, th {border: 1px solid #dddddd; text-align: center; padding: 12px;}</style></head><body><table><tr><th colspan='2'>

QA</th><th colspan='2'>JAT</th> </tr><tr><td>qa2-dal</td><td>$qa2DalVersion</td><td>uat2-dal</td><td> Suat2DalVersion</td></tr>

<tr><td>qa2-phx</td><td>$qa2PhxVersion</td><td>uat2-phx</td><td>$uat2PhxVersion</td></tr>

<tr><td>qal-east1</td><td>$qa1East1Version</td><td>uat1-east1</td><td>

Suat1East1Version</td></tr><tr><td>qa1-east2</td><td>$qa1East2Version</td><td>uat1-east2</td><td>$uat1East2Version</td>

</tr></table></body></html>"

}
}
