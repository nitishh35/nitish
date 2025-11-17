def call(def pcfOrg, def pcfSpace, def pcfFoundation, def manifestFile, def wiremockJar) {

    def envProperty = loadEnvironmentProperties()
    def pcfDeployUserId = envProperty.pcf_non_prod_deploy_user_id
    def pcfDeployCredentialId = envProperty.pcf_non_prod_deploy_credential_id

    def pcfFoundationUrl = "https://api.sys.${pcfFoundation}.pcf.syfbank.com"
    def routeDomain = "app.${pcfFoundation}.pcf.syfbank.com"

    withCredentials([string(credentialsId: pcfDeployCredentialId, variable: 'credentials')]) {

        // 🔹 Inject route domain into manifest file
        sh """
            sed -i 's/APP_DOMAIN_PLACEHOLDER/${routeDomain}/g' ${manifestFile}
        """

        // 🔹 Login to PCF
        sh """
            cf login --skip-ssl-validation \
                -a '${pcfFoundationUrl}' \
                -u '${pcfDeployUserId}' \
                -p '${credentials}' \
                -o '${pcfOrg}' \
                -s '${pcfSpace}'
        """

        // 🔹 Push using manifest
        sh "cf push -f '${manifestFile}' -p '${wiremockJar}'"
    }
}


   jenkins
  
      node(env.dse_worker_node) {

    // Parameters
    def gitProjectKey      = params.PROJECT_KEY
    def gitRepositoryName  = params.REPOSITORY_NAME
    def gitBranch          = params.BRANCH
    def pcfSpace           = params.PCF_SPACE
    def pcfFoundation      = params.PCF_FOUNDATION       // dev1.use1 / dev1.use2
    def pcfOrg             = 'RC-Digital-Solutions'
    def manifestFile       = 'manifest.wiremock.yml'
    def wiremockJar        = 'wiremock-standalone-2.33.2.jar'

    ansiColor('xterm') {
        try {
            timeout(time: 40, unit: 'MINUTES') {

                stage('Checkout') {
                    logStage('checkout') {
                        // checkout scm
                    }
                }

                stage('PCF Deploy') {
                    logStage('pcf-deploy') {
                        wiremockDeployPCF(
                            pcfOrg,
                            pcfSpace,
                            pcfFoundation,
                            manifestFile,
                            wiremockJar
                        )
                    }
                }

            }
        } catch (err) {
            handleBuildFailure(err)
        } finally {
            if (currentBuild.result == 'FAILURE') {
                handleFailure()
            }
        }
    }
}
