pipeline {
    agent any

    environment {
        S3_BUCKET_NAME = 'ucs-backup'
        F5_NAME = 'WAF-10'
        F5_HOST = '192.168.1.10'
        F5_USER = 'admin'
        EMAIL_SENDER = 'sender@example.com'
        EMAIL_RECIPIENTS = 'recipient1@example.com,recipient2@example.com'
        ECS_URL = 'https://ecs-storage.example.com'
        ECS_CREDENTIAL_ID = 'ecs-id'
    }

    stages {
        stage('Set Backup Filename') {
            steps {
                script {
                    echo "F5_NAME: ${F5_NAME}"
                    
                    def date = sh(returnStdout: true, script: 'date +%H-%M-%m-%d-%y').trim()
                    echo "Current date: ${date}"
                    
                    def filename = "${F5_NAME}-${date}.ucs"
                    echo "Constructed filename: ${filename}"

                    env.BACKUP_FILENAME = filename
                    echo "Backup filename set to: ${env.BACKUP_FILENAME}"
                }
            }
        }

        stage('Backup F5 Configuration') {
            steps {
                script {
                    try {
                        sh "ssh ${F5_USER}@${F5_HOST} 'tmsh save sys ucs ${env.BACKUP_FILENAME}'"
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to create F5 backup: ${e.message}")
                    }
                }
            }
        }

        stage('Download Backup File') {
            steps {
                script {
                    try {
                        sh """
                        scp ${F5_USER}@${F5_HOST}:/var/local/ucs/${env.BACKUP_FILENAME} \${WORKSPACE}/
                        """
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to download backup file: ${e.message}")
                    }
                }
            }
        }

        stage('File Size Check') {
            steps {
                script {
                    def size = sh(returnStdout: true, script: "du -b -h ${env.BACKUP_FILENAME}").trim()
                    echo "File Size: ${size}"

                    env.FILE_SIZE = size
                    echo "Backup size: ${env.FILE_SIZE}"
                }
            }
        }

        stage('Upload to ECS Storage') {
            steps {
                script {
                    try {
                        withAWS(endpointUrl: '${ECS_URL}', credentials: '${ECS_CREDENTIAL_ID}') {
                            s3Upload(pathStyleAccessEnabled: true, payloadSigningEnabled: true, file: "${WORKSPACE}/${env.BACKUP_FILENAME}", bucket: "${S3_BUCKET_NAME}", path: "${F5_NAME}/${env.BACKUP_FILENAME}")
                        }
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to upload to ECS storage: ${e.message}")
                    }
                }
            }
        }

        stage('Remove .ucs File From F5') {
            steps {
                script {
                    try {
                        sh "ssh ${F5_USER}@${F5_HOST} 'rm /var/local/ucs/${env.BACKUP_FILENAME}'"
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to remove F5 backup: ${e.message}")
                    }
                }
            }
        }
        
        stage('Clean Workspace') {
            steps {
                cleanWs()
                echo "Workspace cleaned using Clean Workspace plugin."
            }
        }

    }

    post {
        success {
            echo "Backup completed successfully."
            script {
                emailext(
                    subject: "Backup F5 report for: ${F5_NAME} ${F5_HOST}",
                    body: """<p>Backup F5 report for: ${F5_NAME} ${F5_HOST} is complete.</p>
                             <p>The backup name is ${env.BACKUP_FILENAME} - the backup size is ${env.FILE_SIZE}</p>""",
                    to: '${EMAIL_RECIPIENTS}',
                    from: '${EMAIL_SENDER}',
                    mimeType: 'text/html'
                )
            }

        }
        failure {
            echo "Backup failed. Please check the logs for details."
            script {
                emailext(
                    subject: "Backup F5 report for: ${F5_NAME} ${F5_HOST}",
                    body: """<p>Backup F5 report for: ${F5_NAME} ${F5_HOST} is failed.</p>""",
                    to: '${EMAIL_RECIPIENTS}',
                    from: '${EMAIL_SENDER}',
                    mimeType: 'text/html'
                )
            }
        }
    }
}
