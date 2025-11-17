grrovy file


def call(def pcfOrg, def pcfSpace, def pcfFoundation, def manifestFile, def wiremockJar) {

    def envProperty = loadEnvironmentProperties()
    def pcfDeployUserId = envProperty.pcf_non_prod_deploy_user_id
    def pcfDeployCredentialId = envProperty.pcf_non_prod_deploy_credential_id

    def pcfFoundationUrl = "https://api.sys.${pcfFoundation}.pcf.syfbank.com"

    withCredentials([string(credentialsId: pcfDeployCredentialId, variable: 'credentials')]) {

        sh """
        cf login --skip-ssl-validation \
            -a '${pcfFoundationUrl}' \
            -u '${pcfDeployUserId}' \
            -p '${credentials}' \
            -o '${pcfOrg}' \
            -s '${pcfSpace}'
        """

        // Pass PCF_FOUNDATION variable to CF push
        sh """
        PCF_FOUNDATION=${pcfFoundation} \
        cf push -f '${manifestFile}' -p '${wiremockJar}'
        """
    }
}



jenkins file
stage('pcf: deploy') {
    logStage('pcf-deploy') {
        wiremockDeployPCF(
            pcfOrg,
            params.PCF_SPACE,
            params.PCF_FOUNDATION,   // dev1.use1 or dev1.use2
            manifestFile,
            wiremockJar
        )
    }
}




   applications:
  - name: ecom-custid-mockservices
    buildpack: java_buildpack_offline
    memory: 1G
    disk_quota: 16G
    instances: 1

    # Internal ENV variables
    env:
      JBP_CONFIG_JAVA_MAIN: '{ arguments: "--port=\\\\$PORT" }'

      # CF variable interpolation (very important)
      ROUTE_DOMAIN: app.${PCF_FOUNDATION}.pcf.syfbank.com

    routes:
      - route: ecom-custid-mockservices.${ROUTE_DOMAIN}
      - route: ecom-custid-mockservices-alt.${ROUTE_DOMAIN}





