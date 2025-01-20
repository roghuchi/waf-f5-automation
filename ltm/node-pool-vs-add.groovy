pipeline {
    agent any

    environment {
        F5_HOST = '192.168.1.10'
        F5_USER = 'admin'
        TRAFFIC_GROUP = 'traffic-group'
        NODE_MONITOR = 'node-monitor-name'
        POOL_MONITOR = 'pool-monitor-name'
        VS_SSL = 'virtual-server-ssl'
        EMAIL_SENDER = 'sender@example.com'
        EMAIL_RECIPIENTS = 'recipient1@example.com,recipient2@example.com'

    }

    parameters {
        string(name: 'NODE_NAME', defaultValue: 'node-example', description: 'Enter Node Name.')
        string(name: 'NODE_IP', defaultValue: '10.10.10.100', description: 'Enter Node IP (Enter a valid IPv4 address (e.g., 192.168.0.1). Format: XXX.XXX.XXX.XXX, where each XXX is between 0 and 255.).')
        string(name: 'NODE_DESCRIPTION', defaultValue: 'node-description', description: 'Enter Node Description.')
        string(name: 'POOL_NAME', defaultValue: 'pool-example', description: 'Enter Pool Name.')
        string(name: 'POOL_DESCRIPTION', defaultValue: 'pool-description', description: 'Enter Pool Description.')
        string(name: 'VIRTUAL_SERVER', defaultValue: 'vs-example', description: 'Enter Virtual Server Name.')
        string(name: 'VIRTUAL_SERVER_IP', defaultValue: '10.10.10.100', description: 'Enter VS IP (Enter a valid IPv4 address (e.g., 192.168.0.1). Format: XXX.XXX.XXX.XXX, where each XXX is between 0 and 255.).')
        string(name: 'VIRTUAL_SERVER_DESCRIPTION', defaultValue: 'vs-example-description', description: 'Enter Virtual Server URL.')
    }

    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
                echo "Workspace cleaned using Clean Workspace plugin."
            }
        }

        stage('Create or Verify Node') {
            steps {
                script {
                    try {
                        def nodeExists = sh(script: "ssh ${F5_USER}@${F5_HOST} 'tmsh list ltm node ${params.NODE_NAME}'", returnStatus: true) == 0

                        if (nodeExists) {
                            def nodeIp = sh(script: "ssh ${F5_USER}@${F5_HOST} 'tmsh list ltm node ${params.NODE_NAME} | grep address'", returnStdout: true).trim()

                            if (nodeIp != "address ${params.NODE_IP}") {
                                error("Node exists but IP address is incorrect. Expected: ${params.NODE_IP}, Found: ${nodeIp}")
                            } else {
                                echo "Node exists with the correct IP: ${nodeIp}. Skipping creation."
                            }
                        } else {
                            sh "ssh ${F5_USER}@${F5_HOST} 'tmsh create ltm node ${params.NODE_NAME} address ${params.NODE_IP} description \"${params.NODE_DESCRIPTION}\" monitor ${NODE_MONITOR}'"
                            echo "Node created successfully."
                        }
                    } catch (Exception e) {
                        error("Failed during Node creation/verification: ${e.message}")
                    }
                }
            }
        }

        stage('Create or Verify Pool') {
            steps {
                script {
                    try {
                        def poolExists = sh(script: "ssh ${F5_USER}@${F5_HOST} 'tmsh list ltm pool ${params.POOL_NAME}'", returnStatus: true) == 0

                        if (poolExists) {
                            def poolMembers = sh(script: "ssh ${F5_USER}@${F5_HOST} 'tmsh list ltm pool ${params.POOL_NAME} members | grep \"${params.NODE_NAME}:http\"'", returnStatus: true) == 0

                            if (poolMembers) {
                                echo "Node is already a member of the pool. Skipping member addition."
                            } else {
                                sh "ssh ${F5_USER}@${F5_HOST} 'tmsh modify ltm pool ${params.POOL_NAME} members add { ${params.NODE_NAME}:http }'"
                                echo "Added Node to Pool successfully."
                            }
                        } else {
                            sh "ssh ${F5_USER}@${F5_HOST} 'tmsh create ltm pool ${params.POOL_NAME} members add { ${params.NODE_NAME}:http } monitor ${POOL_MONITOR} description \"${params.POOL_DESCRIPTION}\"'"
                            echo "Pool created and Node added successfully."
                        }
                    } catch (Exception e) {
                        error("Failed during Pool creation/verification: ${e.message}")
                    }
                }
            }
        }

        stage('Create or Verify Virtual Server') {
            steps {
                script {
                    try {
                        def vsExists = sh(script: "ssh ${F5_USER}@${F5_HOST} 'tmsh list ltm virtual ${params.VIRTUAL_SERVER}'", returnStatus: true) == 0

                        if (vsExists) {
                            def vsPool = sh(script: "ssh ${F5_USER}@${F5_HOST} 'tmsh list ltm virtual ${params.VIRTUAL_SERVER} pool'", returnStdout: true).trim()

                            if (vsPool != params.POOL_NAME) {
                                sh "ssh ${F5_USER}@${F5_HOST} 'tmsh modify ltm virtual ${params.VIRTUAL_SERVER} pool ${params.POOL_NAME}'"
                                echo "Updated Virtual Server to use the correct Pool."
                            } else {
                                echo "Virtual Server is already configured with the correct Pool. Skipping modification."
                            }
                        } else {
                            def createVsCommand = """
                                tmsh create ltm virtual ${params.VIRTUAL_SERVER} \
                                description '${params.VIRTUAL_SERVER_DESCRIPTION}' \
                                destination ${params.VIRTUAL_SERVER_IP}:https \
                                ip-protocol tcp \
                                mask 255.255.255.255 \
                                pool ${params.POOL_NAME} \
                                profiles add { ${VS_SSL} { context clientside } http tcp websecurity } \
                                security-log-profiles add { SIEMLogging "Log illegal requests" } \
                                source 0.0.0.0/0 \
                                source-address-translation { type automap }
                            """
                            sh "ssh ${F5_USER}@${F5_HOST} '${createVsCommand}'"
                            echo "Virtual Server created successfully."
                        }
                    } catch (Exception e) {
                        error("Failed during Virtual Server creation/verification: ${e.message}")
                    }
                }
            }
        }
        stage('Assign Traffic-Group') {
            steps {
                script {
                    try {
                        sh "ssh ${F5_USER}@${F5_HOST} 'tmsh modify ltm virtual-address ${params.VIRTUAL_SERVER_IP} traffic-group ${TRAFFIC_GROUP}'"
                        echo "Traffic Group assign successfully."
                    } catch (Exception e) {
                        error("Failed to assign Traffic Group: ${e.message}")
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline completed successfully."
            emailext(
                subject: "SUCCESS: Configuration for Virtual Server ${params.VIRTUAL_SERVER}",
                body: """<html>
                    <p><b>Pipeline Execution Status:</b> SUCCESS</p>
                    <p>The configuration process for the Virtual Server has been successfully completed. Below are the details:</p>
                    <ul>
                        <li><b>Node Name:</b> ${params.NODE_NAME}</li>
                        <li><b>Node IP:</b> ${params.NODE_IP}</li>
                        <li><b>Node Description:</b> ${params.NODE_DESCRIPTION}</li>
                        <li><b>Pool Name:</b> ${params.POOL_NAME}</li>
                        <li><b>Pool Description:</b> ${params.POOL_DESCRIPTION}</li>
                        <li><b>Virtual Server Name:</b> ${params.VIRTUAL_SERVER}</li>
                        <li><b>Virtual Server IP:</b> ${params.VIRTUAL_SERVER_IP}</li>
                        <li><b>Virtual Server Description:</b> ${params.VIRTUAL_SERVER_DESCRIPTION}</li>
                    </ul>
                    <p>Please verify the configuration as needed.</p>
                </html>""",
                to: '${EMAIL_RECIPIENTS}',
                from: '${EMAIL_SENDER}',
                mimeType: 'text/html'
            )
        }

        failure {
            echo "Pipeline failed."
            emailext(
                subject: "FAILURE: Configuration for Virtual Server ${params.VIRTUAL_SERVER}",
                body: """<html>
                    <p><b>Pipeline Execution Status:</b> FAILURE</p>
                    <p>The configuration process for the Virtual Server has failed. Below are the attempted configuration details:</p>
                    <ul>
                        <li><b>Node Name:</b> ${params.NODE_NAME}</li>
                        <li><b>Node IP:</b> ${params.NODE_IP}</li>
                        <li><b>Node Description:</b> ${params.NODE_DESCRIPTION}</li>
                        <li><b>Pool Name:</b> ${params.POOL_NAME}</li>
                        <li><b>Pool Description:</b> ${params.POOL_DESCRIPTION}</li>
                        <li><b>Virtual Server Name:</b> ${params.VIRTUAL_SERVER}</li>
                        <li><b>Virtual Server IP:</b> ${params.VIRTUAL_SERVER_IP}</li>
                        <li><b>Virtual Server Description:</b> ${params.VIRTUAL_SERVER_DESCRIPTION}</li>
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
