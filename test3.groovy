Complete Solution with All Code
📂 Step 1: Create Shared Library Repository
1.1 Create the repository structure:
bashmkdir my-jenkins-shared-library
cd my-jenkins-shared-library
mkdir vars
1.2 Create the file: vars/getUDeployVersions.groovy
Copy this EXACT code into the file:
groovy// vars/getUDeployVersions.groovy

def call(String username, String password, String repositoryName) {
    
    if (!username || !password || !repositoryName) {
        return generateErrorHtml("Missing required parameters")
    }
    
    try {
        def versions = fetchAllVersions(username, password, repositoryName)
        return generateHtmlTable(versions)
    } catch (Exception e) {
        return generateErrorHtml("Error: ${e.message}")
    }
}

def fetchAllVersions(String username, String password, String repositoryName) {
    
    def versions = [:]
    
    def environments = [
        'qa2dal', 'qa3dal', 'qa2phx', 'qa-east1', 'qa-west2', 'qa1-east1', 'qa1-east2',
        'uat2dal', 'uat3dal', 'uat2phx', 'uat-east1', 'uat-west2', 'uat1-east1', 'uat1-east2'
    ]
    
    environments.each { env ->
        versions[env] = fetchVersion(username, password, repositoryName, env)
    }
    
    return versions
}

def fetchVersion(String username, String password, String repositoryName, String environment) {
    
    try {
        def cmd = "curl -ku ${username}:${password} " +
                  "https://udeploy.app.syfbank.com:8443/cli/component/getProperty?" +
                  "component=Ecom-API_${repositoryName}&name=last-deployed-${environment}-version"
        
        def process = cmd.execute()
        process.waitForOrKill(30000)
        
        def responseText = process.text.trim()
        
        if (responseText.contains('Property not found')) {
            return 'NA'
        } else if (responseText.isEmpty()) {
            return 'EMPTY'
        } else if (responseText.contains('error') || responseText.contains('Error')) {
            return 'ERROR'
        }
        
        return responseText
        
    } catch (Exception e) {
        return "ERROR: ${e.message}"
    }
}

def generateHtmlTable(Map versions) {
    
    return """
<html>
<head>
    <style>
        table {
            font-family: arial, sans-serif;
            border-collapse: collapse;
            width: 50%;
        }
        td, th {
            border: 1px solid #dddddd;
            text-align: center;
            padding: 12px;
        }
        th {
            background-color: #4CAF50;
            color: white;
        }
        tr:nth-child(even) {
            background-color: #f2f2f2;
        }
    </style>
</head>
<body>
    <table>
        <tr>
            <th colspan='2'>QA</th>
            <th colspan='2'>UAT</th>
        </tr>
        <tr>
            <td>qa2-dal</td>
            <td>${versions['qa2dal']}</td>
            <td>uat2-dal</td>
            <td>${versions['uat2dal']}</td>
        </tr>
        <tr>
            <td>qa3-dal</td>
            <td>${versions['qa3dal']}</td>
            <td>uat3-dal</td>
            <td>${versions['uat3dal']}</td>
        </tr>
        <tr>
            <td>qa2-phx</td>
            <td>${versions['qa2phx']}</td>
            <td>uat2-phx</td>
            <td>${versions['uat2phx']}</td>
        </tr>
        <tr>
            <td>qa-east1</td>
            <td>${versions['qa-east1']}</td>
            <td>uat-east1</td>
            <td>${versions['uat-east1']}</td>
        </tr>
        <tr>
            <td>qa-west2</td>
            <td>${versions['qa-west2']}</td>
            <td>uat-west2</td>
            <td>${versions['uat-west2']}</td>
        </tr>
        <tr>
            <td>qa1-east1</td>
            <td>${versions['qa1-east1']}</td>
            <td>uat1-east1</td>
            <td>${versions['uat1-east1']}</td>
        </tr>
        <tr>
            <td>qa1-east2</td>
            <td>${versions['qa1-east2']}</td>
            <td>uat1-east2</td>
            <td>${versions['uat1-east2']}</td>
        </tr>
    </table>
</body>
</html>
"""
}

def generateErrorHtml(String errorMessage) {
    return """
<html>
<head>
    <style>
        body {
            font-family: arial, sans-serif;
            padding: 20px;
        }
        .error {
            background-color: #ffebee;
            border-left: 4px solid #f44336;
            padding: 15px;
            color: #d32f2f;
        }
    </style>
</head>
<body>
    <div class="error">
        <h3>Error</h3>
        <p>${errorMessage}</p>
    </div>
</body>
</html>
"""
}
1.3 Push to GitHub/GitLab:
bashgit init
git add .
git commit -m "Add UDeploy version checker shared library"
git remote add origin https://github.com/YOUR_ORG/my-jenkins-shared-library.git
git push -u origin main

⚙️ Step 2: Configure Jenkins Shared Library

Go to: Manage Jenkins → System → Scroll to Global Pipeline Libraries
Click Add button
Fill in these details:

Name: udeploy-library ⚠️ (Remember this name!)
Default version: main
Load implicitly: ❌ Unchecked
Allow default version to be overridden: ✅ Checked
Include @Library changes in job recent changes: ✅ Checked


Under Retrieval method, select: Modern SCM
Select Git and fill:

Project Repository: https://github.com/YOUR_ORG/my-jenkins-shared-library.git
Credentials: (Select if private repo, otherwise leave as "none")


Click Save


🔧 Step 3: Update Jenkins Active Choice Parameter
Go to your Jenkins job → Configure → Active Choice Parameter
Replace your ENTIRE script with this:
groovy@Library('udeploy-library')_

import jenkins.model.Jenkins
import com.cloudbees.plugins.credentials.CredentialsProvider

def creds = CredentialsProvider.lookupCredentials(
    com.cloudbees.plugins.credentials.Credentials.class,
    Jenkins.instance,
    null,
    null
).find { it.id == "UDEPLOY_PROD_CREDENTIALS" }

if (creds) {
    return getUDeployVersions(
        creds.username.toString(), 
        creds.password.toString(), 
        REPOSITORY_NAME
    )
} else {
    return "<html><body><h3 style='color:red;'>Error: UDEPLOY_PROD_CREDENTIALS not found</h3></body></html>"
}
⚠️ IMPORTANT: Make sure 'udeploy-library' matches the name you used in Step 2!

✅ Step 4: Test and Approve

Save your Jenkins job configuration
Run the job - It will fail with "Script not approved"
Go to: Manage Jenkins → In-process Script Approval
You will see pending approvals like:

   method jenkins.model.Jenkins getInstanceOrNull
   staticMethod jenkins.model.Jenkins getInstance
   method com.cloudbees.plugins.credentials.CredentialsProvider lookupCredentials

Approve all pending scripts
Run the job again - It should work now! ✅


🎉 Done! Now Test Future Changes
Make a change WITHOUT needing approval:
bashcd my-jenkins-shared-library

# Edit vars/getUDeployVersions.groovy
# Example: Change the table header color from green to blue
# Find this line: background-color: #4CAF50;
# Change to: background-color: #2196F3;

git add vars/getUDeployVersions.groovy
git commit -m "Changed header color to blue"
git push origin main
Run your Jenkins job again - The change will be reflected! No approval needed! 🎊

📋 Your Repository Structure Should Look Like:
my-jenkins-shared-library/
├── vars/
│   └── getUDeployVersions.groovy    (The code above)
└── README.md                         (Optional)

🐛 Troubleshooting
Error: "No signature of method: getUDeployVersions()"
Fix: The library name in @Library('udeploy-library') must match what you configured in Jenkins
Error: "Credentials not found"
Fix:

Verify UDEPLOY_PROD_CREDENTIALS exists in Jenkins
Go to: Manage Jenkins → Credentials → Check it's there

Error: "Unable to resolve class"
Fix:

Make sure file is named exactly: vars/getUDeployVersions.groovy
File must be in vars/ folder, not root

Changes not reflecting
Fix:

Wait 1-2 minutes (Jenkins caches libraries)
Or force refresh: Restart Jenkins
Check you pushed to the correct branch


📊 What You Achieved
✅ All business logic is now in Git (version controlled)
✅ Only 15 lines in Jenkins (approved once)
✅ Future changes = Just git push
✅ No more waiting for admin approvals
✅ Can rollback easily with git
✅ Can test in branches before merging

🚀 Next Steps
Now you can:

Add more environments to the list
Change HTML styling
Add error notifications
Create more shared library functions
Share this library across multiple jobs

Remember: Every change in vars/getUDeployVersions.groovy = No approval needed! 🎉
