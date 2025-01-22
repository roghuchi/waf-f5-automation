pipeline {
    agent any

    environment {
        F5_NAME = 'WAF-10'
        F5_HOST = '192.168.1.10'
        F5_USER = 'admin'
        EMAIL_SENDER = 'sender@example.com'
        EMAIL_RECIPIENTS = 'recipient1@example.com,recipient2@example.com'
    }


    parameters {
        string(name: 'VIRTUAl_SERVER', defaultValue: '', description: 'Enter Virtual Server Name.' )
    }


    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
                echo "Workspace cleaned using Clean Workspace plugin."
            }
        }

        stage('Assign Policy to Virtual Server') {
            steps {
                script {
                    try {
                        sh """
                        ssh ${F5_USER}@${F5_HOST} 'modify ltm virtual ${params.VIRTUAl_SERVER} policies add { asm_auto_l7_policy__${params.VIRTUAl_SERVER} }'
                        """
                        echo "The virtual server policy has been enable"
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to enable policy: ${e.message}")
                    }
                }
            }
        }

    }

    post {
        success {
            echo "The virtual server policy has been enable"
            emailext(
                subject: "SUCCESS: The Virtual Server policy for ${params.VIRTUAl_SERVER} has been enabled",
                body: """<p>The Virtual Server policy ${params.POLICY_NAME} has been enabled for ${params.VIRTUAl_SERVER} .</p>""",
                to: '${EMAIL_RECIPIENTS}',
                from: '${EMAIL_SENDER}',
                mimeType: 'text/html'
            )
        }
        
        failure {
            echo "Failed to enable policy for virtual server name"
            emailext(
                subject: "FAILURE: The Virtual Server policy for ${params.VIRTUAl_SERVER} failed to enable",
                body: """<p>The Virtual Server policy ${params.POLICY_NAME} has been failed to enabled for ${params.VIRTUAl_SERVER} .</p>""",
                to: '${EMAIL_RECIPIENTS}',
                from: '${EMAIL_SENDER}',
                mimeType: 'text/html'
            )
        }
    }
}
