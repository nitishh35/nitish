def call(def pcfOrg, def pcfSpace, def pcfFoundation, def manifestFile, def wiremockJar) {

    def envProperty = loadEnvironmentProperties()
    def pcfDeployUserId = envProperty.pcf_non_prod_deploy_user_id
    def pcfDeployCredentialId = envProperty.pcf_non_prod_deploy_credential_id

    def pcfFoundationUrl = "https://api.sys.${pcfFoundation}.pcf.syfbank.com"
    def routeDomain = "app.${pcfFoundation}.pcf.syfbank.com"

    withCredentials([string(credentialsId: pcfDeployCredentialId, variable: 'credentials')]) {

        echo "🔧 Updating manifest with domain: ${routeDomain}"

        // -----------------------------
        // STEP 1: Convert to UNIX format + Replace placeholder
        // -----------------------------
        sh """
            # convert CRLF -> LF (safe even if file already LF)
            dos2unix ${manifestFile} 2>/dev/null || true

            # safe placeholder replacement using | delimiter
            sed -i "s|APP_DOMAIN_PLACEHOLDER|${routeDomain}|g" ${manifestFile}
        """

        // -----------------------------
        // STEP 2: Print updated manifest (debug)
        // -----------------------------
        sh """
            echo '----- FINAL MANIFEST CONTENT -----'
            cat ${manifestFile}
            echo '----------------------------------'
        """

        // -----------------------------
        // STEP 3: Login to PCF
        // -----------------------------
        sh """
            cf login --skip-ssl-validation \
                -a '${pcfFoundationUrl}' \
                -u '${pcfDeployUserId}' \
                -p '${credentials}' \
                -o '${pcfOrg}' \
                -s '${pcfSpace}'
        """

        // -----------------------------
        // STEP 4: Deploy using manifest
        // -----------------------------
        sh """
            cf push -f '${manifestFile}' -p '${wiremockJar}'
        """
    }
}
