pipeline {
    agent any

    environment {
        F5_HOST = '192.168.1.10'
        F5_CREDENTIAL_ID = 'credential-id'
        EMAIL_SENDER = 'sender@example.com'
        EMAIL_RECIPIENTS = 'recipient1@example.com,recipient2@example.com'
    }

    parameters {
        string(name: 'POLICY_NAME', description: 'Enter the name of the policy', defaultValue: 'none')
        string(name: 'SIGNATURE_ID', description: 'Enter the id of the signature', defaultValue: 'none')
    }

    stages {
        stage('Validate Input Parameters') {
            steps {
                script {
                    try {
                        if (params.POLICY_NAME == 'none' || params.SIGNATURE_ID == 'none') {
                            error "POLICY_NAME or SIGNATURE_ID cannot be 'none'. Please provide valid inputs."
                        }
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        echo "Error in parameter validation: ${e.message}"
                        env.EMAIL_ERROR_MESSAGE = e.message // Store error message to include in email
                        throw e // Re-throw the exception to fail the pipeline
                    }
                }
            }
        }

        stage('Fetch Policy ID') {
            steps {
                script {
                    try {
                        def apiUrl = "https://${env.F5_HOST}/mgmt/tm/asm/policies?\$select=name,id"

                        // Fetch policies
                        def response = httpRequest(
                            url: apiUrl,
                            httpMode: 'GET',
                            authentication: F5_CREDENTIAL_ID,
                            validResponseCodes: '200',
                            ignoreSslErrors: true
                        )

                        // Parse response
                        def jsonResponse = readJSON(text: response.content)
                        def policies = jsonResponse.items.collectEntries { item ->
                            [(item.name): item.id]
                        }

                        // Check for policy
                        def policyName = params.POLICY_NAME
                        def policyId = policies[policyName]

                        if (!policyId) {
                            error "Policy '${policyName}' not found."
                        }

                        echo "Policy ID for '${policyName}' is: ${policyId}"
                        env.POLICY_ID = policyId // Store the policy ID for the next stages
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        echo "Error fetching policy ID: ${e.message}"
                        env.EMAIL_ERROR_MESSAGE = e.message // Store error message to include in email
                        throw e // Re-throw the exception to fail the pipeline
                    }
                }
            }
        }

        stage('Enable Signature in Policy') {
            steps {
                script {
                    try {
                        def policyId = env.POLICY_ID
                        def signatureId = params.SIGNATURE_ID
                        def patchApiUrl = "https://${env.F5_HOST}/mgmt/tm/asm/policies/${policyId}/signatures/?\$filter=signature/signatureId%20eq%20${signatureId}"

                        // JSON body to enable the signature
                        def jsonBody = [enabled: true]

                        // Make PATCH request
                        def patchResponse = httpRequest(
                            url: patchApiUrl,
                            httpMode: 'PATCH',
                            authentication: F5_CREDENTIAL_ID,
                            requestBody: groovy.json.JsonOutput.toJson(jsonBody),
                            validResponseCodes: '200:204',
                            contentType: 'APPLICATION_JSON',
                            ignoreSslErrors: true
                        )

                        // Check the totalItems to verify if the signatureId was correct
                        def checkApiUrl = "https://${env.F5_HOST}/mgmt/tm/asm/policies/${policyId}/signatures/?\$filter=signature/signatureId%20eq%20${signatureId}"
                        def checkResponse = httpRequest(
                            url: checkApiUrl,
                            httpMode: 'GET',
                            authentication: F5_CREDENTIAL_ID,
                            validResponseCodes: '200',
                            ignoreSslErrors: true
                        )

                        def checkJsonResponse = readJSON(text: checkResponse.content)

                        if (checkJsonResponse.totalItems == 0) {
                            error "Signature ID '${signatureId}' not found in Policy '${policyId}'."
                        } else if (checkJsonResponse.totalItems == 1) {
                            echo "Signature '${signatureId}' has been successfully enabled for Policy ID '${policyId}'."
                        } else {
                            error "Unexpected response when verifying Signature ID '${signatureId}' in Policy '${policyId}'. Total items: ${checkJsonResponse.totalItems}"
                        }
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        echo "Error disabling signature in policy: ${e.message}"
                        env.EMAIL_ERROR_MESSAGE = e.message // Store error message to include in email
                        throw e // Re-throw the exception to fail the pipeline
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                try {
                    emailext(
                        subject: "F5 Signature enable Success: Policy '${params.POLICY_NAME}'",
                        body: """
                            <p>The Jenkins pipeline completed successfully.</p>
                            <p><b>WAF IP:</b> ${env.F5_HOST}</p>
                            <p><b>Policy Name:</b> ${params.POLICY_NAME}</p>
                            <p><b>Signature ID:</b> ${params.SIGNATURE_ID}</p>
                            <p>The signature has been enabled successfully.</p>
                        """,
                        to: '${EMAIL_RECIPIENTS}',
                        from: '${EMAIL_SENDER}',
                        mimeType: 'text/html'
                    )
                } catch (Exception e) {
                    echo "Error sending success email: ${e.message}"
                }
            }
        }
        failure {
            script {
                try {
                    emailext(
                        subject: "F5 Signature enable Failed: Policy '${params.POLICY_NAME}'",
                        body: """
                            <p>The Jenkins pipeline failed.</p>
                            <p><b>WAF IP:</b> ${env.F5_HOST}</p>
                            <p><b>Policy Name:</b> ${params.POLICY_NAME}</p>
                            <p><b>Signature ID:</b> ${params.SIGNATURE_ID}</p>
                            <p><b>Error Details:</b> ${env.EMAIL_ERROR_MESSAGE}</p>
                            <p>Please check the Jenkins logs for more details.</p>
                        """,
                        to: '${EMAIL_RECIPIENTS}',
                        from: '${EMAIL_SENDER}',
                        mimeType: 'text/html'
                    )
                } catch (Exception e) {
                    echo "Error sending failure email: ${e.message}"
                }
            }
        }
    }
}
