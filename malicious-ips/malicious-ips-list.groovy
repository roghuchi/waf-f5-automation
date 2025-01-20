pipeline {
    agent any

    environment {
        F5_NAME = 'WAF-10'
        F5_HOST = '192.168.1.10'
        F5_USER = 'admin'
        OUTPUT_FILE = 'malicious_sources_output.txt'
        EMAIL_SENDER = 'sender@example.com'
        EMAIL_RECIPIENTS = 'recipient1@example.com,recipient2@example.com'
    }

    stages {
                
        stage('Clean Workspace') {
            steps {
                cleanWs()
                echo "Workspace cleaned using Clean Workspace plugin."
            }
        }

        stage('Security Malicious Sources List') {
            steps {
                script {
                    try {
                        // Create an empty file before running the SSH command
                        sh "touch ${OUTPUT_FILE}"

                        // Run the command and save output to the file
                        sh """
                            ssh ${F5_USER}@${F5_HOST} 'tmsh show security malicious-sources' > ${OUTPUT_FILE} || true
                        """
                        sh "cat ${OUTPUT_FILE}"    
                        // Check if the output file is empty
                        def fileContent = readFile("${OUTPUT_FILE}").trim()
                        if (fileContent.isEmpty() || fileContent == "There is no malicious IP") {
                            // Mark the build as NOT_BUILT if there's no malicious IP data
                            currentBuild.result = 'NOT_BUILT'
                            echo "No malicious IPs found; skipping post actions."
                            return
                        }
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Failed to create list: ${e.message}")
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Security Malicious Sources List Created."
            script {
                // Read the file content to include in the email
                def fileContent = readFile("${OUTPUT_FILE}").trim()

                // Process the file content to create a human-readable table format
                def formattedContent = """
                    <h2>Security Malicious Sources Report for: ${F5_NAME} ${F5_HOST}</h2>
                    <p>The following malicious source IPs were detected:</p>
                    <table border="1" cellpadding="5" cellspacing="0">
                        <thead>
                        </thead>
                        <tbody>
                """
                
                // Split file content by lines, assuming each line contains one IP address and its reason
                def lines = fileContent.split("\n")
                lines.each { line ->
                    // Split the line into IP and Reason part, assuming it's structured like 'IP REASON'
                    def parts = line.trim().split(/\s{2,}/) // Match on multiple spaces or tabs
                    if (parts.length > 1) {
                        def ip = parts[0].trim()
                        def reason = parts[1].trim()
                        // Add each row to the table
                        formattedContent += """
                            <tr>
                                <td>${ip}</td>
                                <td>${reason}</td>
                            </tr>
                        """
                    }
                }

                // Closing table and email body
                formattedContent += """
                        </tbody>
                    </table>
                """

                // Send the formatted report via email
                emailext(
                    subject: "Security Malicious Sources List For: ${F5_NAME} ${F5_HOST}",
                    body: formattedContent,
                    to: '${EMAIL_RECIPIENTS}',
                    from: '${EMAIL_SENDER}',
                    mimeType: 'text/html'
                )
            }
        }
        failure {
            echo "Security Malicious Sources List Failed. Please check the logs for details."
            script {
                emailext(
                    subject: "Security Malicious Sources List For: ${F5_NAME} ${F5_HOST}",
                    body: """<p>Security Malicious Sources List Failed For: ${F5_NAME} ${F5_HOST}</p>""",
                    to: '${EMAIL_RECIPIENTS}',
                    from: '${EMAIL_SENDER}',
                    mimeType: 'text/html'
                )
            }
        }
    }
}
