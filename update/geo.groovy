pipeline {
    agent any

    environment {
        S3_BUCKET_NAME = 'geo-update-waf'
        F5_FOLDER = 'module'
        F5_HOST = '192.168.1.10'
        F5_USER = 'admin'
        EMAIL_SENDER = 'sender@example.com'
        EMAIL_RECIPIENTS = 'recipient1@example.com,recipient2@example.com'
        ECS_URL = 'https://ecs-storage.example.com'
        ECS_CREDENTIAL_ID = 'ecs-id'
    }

    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
                echo "Workspace cleaned using Clean Workspace plugin."
            }
        }

        stage('Download GeoIP Update Files from S3') {
            steps {
                script {
                    try {
                        withAWS(endpointUrl: '${ECS_URL}', credentials: '${ECS_CREDENTIAL_ID}') {
                            s3Download(pathStyleAccessEnabled: true, bucket: "${S3_BUCKET_NAME}", path: "${F5_FOLDER}/", file: "${WORKSPACE}/")
                        }
                        echo "Downloaded GeoIP update files from S3 bucket ${S3_BUCKET_NAME}."
                        def filesDownloaded = sh(returnStdout: true, script: "ls ${WORKSPACE}/${F5_FOLDER}").trim()
                        env.DOWNLOADED_FILES = filesDownloaded
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to download GeoIP update files: ${e.message}")
                    }
                }
            }
        }

        stage('Transfer Files to F5 WAF') {
            steps {
                script {
                    try {
                        sh "scp ${WORKSPACE}/${F5_FOLDER}/*.rpm ${F5_USER}@${F5_HOST}:/var/tmp/"
                        echo "Transferred GeoIP update files to F5 WAF at ${F5_HOST}."
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to transfer files to F5 WAF: ${e.message}")
                    }
                }
            }
        }

        stage('Update GeoIP Data on F5 WAF') {
            steps {
                script {
                    try {
                        sh "ssh ${F5_USER}@${F5_HOST} 'geoip_update_data -f /var/tmp/*.rpm'"
                        echo "GeoIP data updated on F5 WAF."
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to update GeoIP data on F5 WAF: ${e.message}")
                    }
                }
            }
        }

        stage('Delete .rpm Files') {
            steps {
                script {
                    try {
                        sh "ssh ${F5_USER}@${F5_HOST} 'rm /var/tmp/*.rpm'"
                        echo ".rpm Files Deleted"
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to Delete .rpm Files: ${e.message}")
                    }
                }
            }
        }
    }

    post {
        success {
            echo "GeoIP update completed successfully."
            script {
                emailext(
                    subject: "GeoIP Update Report for F5 WAF: ${F5_HOST}",
                    body: """<p>GeoIP update on F5 WAF (${F5_HOST}) completed successfully.</p>
                             <p>Files updated:</p>
                             <pre>${env.DOWNLOADED_FILES}</pre>""",
                    to: '${EMAIL_RECIPIENTS}',
                    from: '${EMAIL_SENDER}',
                    mimeType: 'text/html'
                )
            }
        }
        failure {
            echo "GeoIP update failed. Please check the logs for details."
            script {
                emailext(
                    subject: "GeoIP Update Failed for F5 WAF: ${F5_HOST}",
                    body: """<p>GeoIP update on F5 WAF (${F5_HOST}) failed. Please review the logs for details.</p>""",
                    to: '${EMAIL_RECIPIENTS}',
                    from: '${EMAIL_SENDER}',
                    mimeType: 'text/html'
                )
            }
        }
    }
}
