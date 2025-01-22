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
        string(name: 'POLICY_NAME', defaultValue: 'policy-example', description: 'Enter Policy Name.')
    }


    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
                echo "Workspace cleaned using Clean Workspace plugin."
            }
        }

        stage('Enable Profile websecurity Virtual Servers') {
            steps {
                script {
                    try {
                        sh """
                        ssh ${F5_USER}@${F5_HOST} 'tmsh modify ltm virtual ${params.VIRTUAl_SERVER} Profiles add { websecurity { } } '
                        """
                        echo "The virtual server profile has been modified"
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to modify profile: ${e.message}")
                    }
                }
            }
        }

        stage('Create LTM Policy') {
            steps {
                script {
                    try {
                        sh """
                        ssh ${F5_USER}@${F5_HOST} 'tmsh create ltm policy /Common/Drafts/asm_auto_l7_policy__${params.VIRTUAl_SERVER} controls add { asm } strategy first-match  requires add { http } rules add { default { actions add { 1 { asm enable policy ${params.POLICY_NAME} } } ordinal 1 } } '
                        """
                        echo "The ltm policy has been created"
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to created ltm policy: ${e.message}")
                    }
                }
            }
        }

        stage('Publish LTM Policy') {
            steps {
                script {
                    try {
                        sh """
                        ssh ${F5_USER}@${F5_HOST} 'tmsh publish ltm policy Drafts/asm_auto_l7_policy__${params.VIRTUAl_SERVER}'
                        """
                        echo "The policy has been published"
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to publish policy: ${e.message}")
                    }
                }
            }
        }

        stage('Assign Policy to Virtual Server') {
            steps {
                script {
                    try {
                        sh """
                        ssh ${F5_USER}@${F5_HOST} 'tmsh modify ltm virtual ${params.VIRTUAl_SERVER} policies add { asm_auto_l7_policy__${params.VIRTUAl_SERVER} }'
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
