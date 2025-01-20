pipeline {
    agent any
    environment {
        F5_HOST = '192.168.1.10'
        F5_USER = 'admin'
        EMAIL_SENDER = 'sender@example.com'
        EMAIL_RECIPIENTS = 'recipient1@example.com,recipient2@example.com'
    }

    parameters {
        string(name: 'VIRTUAL_SERVER', defaultValue: 'vs-example', description: 'Enter Virtual Server Name.')
        string(name: 'POOL_NAME', defaultValue: 'pool-example', description: 'Enter Pool Name.')
        string(name: 'NODE_NAME', defaultValue: 'node-example', description: 'Enter Node Name.')
        string(name: 'CONFIRM_DELETE', defaultValue: 'no', description: 'Type "yes" to confirm deletion.')
    }

    stages {
        stage('Confirm Deletion') {
            steps {
                script {
                    if (params.CONFIRM_DELETE != 'yes') {
                        error("Deletion process aborted. CONFIRM_DELETE must be 'yes'.")
                    }
                    echo "Deletion process confirmed. Proceeding with deletion."
                }
            }
        }

        stage('Delete Virtual Server') {
            steps {
                script {
                    try {
                        def vsExists = sh(script: "ssh ${F5_USER}@${F5_HOST} 'tmsh list ltm virtual ${params.VIRTUAL_SERVER}'", returnStatus: true) == 0

                        if (vsExists) {
                            sh "ssh ${F5_USER}@${F5_HOST} 'tmsh delete ltm virtual ${params.VIRTUAL_SERVER}'"
                            echo "Virtual Server ${params.VIRTUAL_SERVER} deleted successfully."
                        } else {
                            echo "Virtual Server ${params.VIRTUAL_SERVER} does not exist. Skipping deletion."
                        }
                    } catch (Exception e) {
                        error("Failed during Virtual Server deletion: ${e.message}")
                    }
                }
            }
        }

        stage('Delete Pool') {
            steps {
                script {
                    try {
                        def poolExists = sh(script: "ssh ${F5_USER}@${F5_HOST} 'tmsh list ltm pool ${params.POOL_NAME}'", returnStatus: true) == 0

                        if (poolExists) {
                            sh "ssh ${F5_USER}@${F5_HOST} 'tmsh delete ltm pool ${params.POOL_NAME}'"
                            echo "Pool ${params.POOL_NAME} deleted successfully."
                        } else {
                            echo "Pool ${params.POOL_NAME} does not exist. Skipping deletion."
                        }
                    } catch (Exception e) {
                        error("Failed during Pool deletion: ${e.message}")
                    }
                }
            }
        }

        stage('Delete Node') {
            steps {
                script {
                    try {
                        def nodeExists = sh(script: "ssh ${F5_USER}@${F5_HOST} 'tmsh list ltm node ${params.NODE_NAME}'", returnStatus: true) == 0

                        if (nodeExists) {
                            sh "ssh ${F5_USER}@${F5_HOST} 'tmsh delete ltm node ${params.NODE_NAME}'"
                            echo "Node ${params.NODE_NAME} deleted successfully."
                        } else {
                            echo "Node ${params.NODE_NAME} does not exist. Skipping deletion."
                        }
                    } catch (Exception e) {
                        error("Failed during Node deletion: ${e.message}")
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Deletion process completed successfully."
            emailext(
                subject: "SUCCESS: Deletion of F5 Configuration",
                body: """<html>
                    <p><b>Pipeline Execution Status:</b> SUCCESS</p>
                    <p>The deletion process for the F5 configuration has been successfully completed. Below are the details:</p>
                    <ul>
                        <li><b>Virtual Server Name:</b> ${params.VIRTUAL_SERVER}</li>
                        <li><b>Pool Name:</b> ${params.POOL_NAME}</li>
                        <li><b>Node Name:</b> ${params.NODE_NAME}</li>
                    </ul>
                    <p>Please verify the deletion as needed.</p>
                </html>""",
                to: '${EMAIL_RECIPIENTS}',
                from: '${EMAIL_SENDER}',
                mimeType: 'text/html'
            )
        }

        failure {
            echo "Deletion process failed."
            emailext(
                subject: "FAILURE: Deletion of F5 Configuration",
                body: """<html>
                    <p><b>Pipeline Execution Status:</b> FAILURE</p>
                    <p>The deletion process for the F5 configuration has failed. Below are the attempted configuration details:</p>
                    <ul>
                        <li><b>Virtual Server Name:</b> ${params.VIRTUAL_SERVER}</li>
                        <li><b>Pool Name:</b> ${params.POOL_NAME}</li>
                        <li><b>Node Name:</b> ${params.NODE_NAME}</li>
                    </ul>
                    <p>Please review the pipeline logs and configuration details for troubleshooting.</p>
                </html>""",
                to: '${EMAIL_RECIPIENTS}',
                from: '${EMAIL_SENDER}',
                mimeType: 'text/html'
            )
        }
    }
}
