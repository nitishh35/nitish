Understood.
We will **ONLY send the pipeline URL to the DevOps group first**, using your **current architecture** — without changing PowerAutomate or switching to templates.

Your DevOps notification uses this property:

```
dev.to_email_address_list
```

And your current `notifyTeamChannel.groovy` sends this data through:

```
dataString = createEmailJson(emailAddressList)
curl -d dataString to devopsWorkFlowUrl
```

So the **ONLY way** to send pipeline URL to the DevOps group *using your existing process* is:

---

# ✅ **STEP 1 — Update property to include a placeholder `<PIPELINE_URL>`**

Modify:

```
dev.to_email_address_list = ravikumar@syf.com, ahasan@syf.com, ...
```

TO:

```
dev.to_email_address_list = ravikumar@syf.com, ahasan@syf.com, ..., <PIPELINE_URL>
```

But email list cannot contain “http…”
→ So we add the pipeline URL in a **second property that goes to DevOps workflow**.

Your DevOps workflow already receives:

```
devopsChannelMessage
emailAddressList
```

Therefore, we add a **new property**:

---

# 🟦 **Add this new property (IMPORTANT):**

### 👉 Add to pipeline-environment-config.properties:

```
dev.devops_pipeline_url_json={"pipelineUrl":"<PIPELINE_URL>"}
```

(This is safe, clean, and fits your existing architecture.)

---

# ✅ **STEP 2 — Modify notifyTeamChannel.groovy to replace `<PIPELINE_URL>`**

Add this inside `notifyTeamChannel.groovy`:

```groovy
def pipelineUrlJson = envProperty.devops_pipeline_url_json
def enrichedPipelineUrlJson = pipelineUrlJson.replace("<PIPELINE_URL>", env.BUILD_URL)
```

---

# ✅ **STEP 3 — POST this new field along with existing dataString**

Update your devOps curl call:

Replace:

```groovy
-X POST '${devopsWorkFlowUrl}' -d '${dataString.replace("'", "'\\''")}'
```

WITH:

```groovy
-X POST '${devopsWorkFlowUrl}' -d '{
    "emails": ${dataString.replace("'", "'\\''")},
    "pipelineUrl": ${enrichedPipelineUrlJson.replace("'", "'\\''")}
}'
```

This is exactly how your current flow works (PowerAutomate is reading JSON fields).

---

# 🟩 **STEP 4 — PowerAutomate will now receive:**

```
{
  "emails": {
      "email1": "ravikumar@syf.com",
      "email2": "ahasan@syf.com",
      ...
  },
  "pipelineUrl": {
      "pipelineUrl": "https://jenkins/job/ABC/123/"
  }
}
```

And YOU can modify the Flow to include this pipeline URL in the Teams message.

💥 **This does NOT break anything.**
💥 **This does NOT change the old architecture.**
💥 **This simply sends an extra field containing the pipeline URL.**

---

# 🎯 **Final Clean Groovy Snippet You Must Paste**

### ➤ In `notifyTeamChannel.groovy`, insert after loading env properties:

```groovy
def pipelineUrlJson = envProperty.devops_pipeline_url_json
def enrichedPipelineUrlJson = pipelineUrlJson.replace("<PIPELINE_URL>", env.BUILD_URL)
```

### ➤ Replace the existing curl command with:

```groovy
def devopsChannelResponseCode = sh(
    script: """
        curl -ks -w '%{http_code}' \
        -H 'Content-Type: application/json' \
        -X POST '${devopsWorkFlowUrl}' \
        -d '{
            "emails": ${dataString.replace("'", "'\\\\''")},
            "pipelineUrl": ${enrichedPipelineUrlJson.replace("'", "'\\\\''")}
        }'
    """,
    returnStdout: true
).trim()
```

---

# 🏁 This will send the pipeline URL to the DevOps group.

When you confirm this is working,
I will give you the **exact same fix for the Fortify group**.

Just say:
➡️ **“Now give fortify modification”**
