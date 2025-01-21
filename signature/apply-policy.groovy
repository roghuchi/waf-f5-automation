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
    }

    stages {
        stage('Validate Input Parameters') {
            steps {
                script {
                    try {
                        if (params.POLICY_NAME == 'none') {
                            error "POLICY_NAME cannot be 'none'. Please provide a valid policy name."
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

        stage('Apply Security Policy') {
            steps {
                script {
                    try {
                        def policyId = env.POLICY_ID
                        def applyPolicyUrl = "https://${env.F5_HOST}/mgmt/tm/asm/tasks/apply-policy"
                        def jsonBody = [
                            policyReference: [
                                link: "https://${env.F5_HOST}/mgmt/tm/asm/policies/${policyId}"
                            ]
                        ]

                        // Make POST request to apply policy
                        def applyPolicyResponse = httpRequest(
                            url: applyPolicyUrl,
                            httpMode: 'POST',
                            authentication: F5_CREDENTIAL_ID,
                            requestBody: groovy.json.JsonOutput.toJson(jsonBody),
                            validResponseCodes: '200:204',
                            contentType: 'APPLICATION_JSON',
                            ignoreSslErrors: true
                        )

                        echo "Policy '${policyId}' applied successfully."
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        echo "Error applying policy: ${e.message}"
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
                        subject: "F5 Policy Apply Success: Policy '${params.POLICY_NAME}'",
                        body: """
                            <p>The Jenkins pipeline completed successfully.</p>
                            <p><b>WAF IP:</b> ${env.F5_HOST}</p>
                            <p><b>Policy Name:</b> ${params.POLICY_NAME}</p>
                            <p>The policy has been successfully applied.</p>
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
                        subject: "F5 Policy Apply Failed: Policy '${params.POLICY_NAME}'",
                        body: """
                            <p>The Jenkins pipeline failed.</p>
                            <p><b>WAF IP:</b> ${env.F5_HOST}</p>
                            <p><b>Policy Name:</b> ${params.POLICY_NAME}</p>
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
