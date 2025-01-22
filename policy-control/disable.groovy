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

        stage('Policy Disable Virtual Servers') {
            steps {
                script {
                    try {
                        sh """
                        ssh ${F5_USER}@${F5_HOST} 'tmsh modify ltm virtual ${params.VIRTUAl_SERVER} policies none'
                        """
                        echo "The virtual server policy has been disabled"
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to disable policy: ${e.message}")
                    }
                }
            }
        }
    }

    post {
        success {
            echo "The virtual server policy has been disabled"
            emailext(
                subject: "SUCCESS: The Virtual Server policy for ${params.VIRTUAl_SERVER} has been disabled",
                body: """<p>The Virtual Server policy has been disabled for ${params.VIRTUAl_SERVER} .</p>""",
                to: '${EMAIL_RECIPIENTS}',
                from: '${EMAIL_SENDER}',
                mimeType: 'text/html'
            )
        }
        
        failure {
            echo "Failed to disable policy for virtual server name"
            emailext(
                subject: "FAILURE: The Virtual Server policy for ${params.VIRTUAl_SERVER} failed to disable",
                body: """<p>The Virtual Server policy has been failed to disabled for ${params.VIRTUAl_SERVER} .</p>""",
                to: '${EMAIL_RECIPIENTS}',
                from: '${EMAIL_SENDER}',
                mimeType: 'text/html'
            )
        }
    }
}
