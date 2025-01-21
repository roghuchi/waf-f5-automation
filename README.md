# waf-f5-automation

This repository contains Jenkins Groovy scripts designed to automate various operations on F5 WAF systems. These scripts streamline routine tasks, making it easier to manage F5 WAF configurations and updates efficiently.

## Features

The repository includes automation scripts for the following tasks:

1. **UCS Backup**
   - Automates the backup of UCS (User Configuration Sets) files.
   - Stores backup files in ECS.

2. **Node, Pool, and Virtual Server Management**
   - Add or delete nodes, pools, and virtual servers in the WAF system.

3. **Malicious IP Reporting**
   - Retrieve a list of malicious IPs and generate reports.

4. **Policy Control**
   - Disable policies on the WAF system when required.

5. **Signature Management**
   - Enable or disable signatures.
   - Apply updated signatures to policies.

6. **Geo Update**
   - Update geo data in the WAF system.

## Prerequisites

To use these scripts, ensure the following:

- A working Jenkins environment.
- Proper access to the F5 WAF system with necessary permissions.
- ECS configured for storing UCS backups.
- Groovy plugin installed in Jenkins.
- Necessary credentials and endpoint details for interacting with the F5 WAF.

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/waf-f5-automation.git
   ```

2. Configure Jenkins to use the scripts:
   - Add the scripts as Jenkins Pipeline scripts or as standalone Jenkins jobs.
   - Set up parameters as required for each script.

3. Update configurations:
   - Modify the scripts to include your specific environment details (e.g., API endpoints, authentications, ECS bucket names).

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository.
2. Create a new branch for your feature or bug fix.
3. Commit your changes and push the branch.
4. Submit a pull request with a detailed description of your changes.

