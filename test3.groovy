Yes, these findings can have an impact, but not all of them will necessarily break your AWS Provider upgrade. Let's categorize them into functional impact vs security/compliance impact.

Finding	Functional Impact	Security/Compliance Impact	Severity

OPA policy checks skipped	❌ Usually no	✅ High	High
Provider alias warning	✅ Possible	✅ Possible	High
S3 encryption ambiguity	❌ Usually no	✅ High	Medium-High
Shared Lambda execution role	❌ No	✅ Medium	Medium
Secrets Manager rotation missing	❌ No	✅ High	Medium-High
Step Functions using AWS-owned key	❌ No	✅ Compliance risk	Medium



---

1. OPA Policy Checks Were Skipped

Impact

This does not affect deployment functionality.

However, it means security guardrails were bypassed.

Example:

Public S3 bucket

Overly permissive IAM policy

Unencrypted resources


might get deployed because OPA validation never ran.

Recommendation

Before approving the provider upgrade:

opa test

or rerun the CI stage that executes policy checks.


---

2. Provider Alias Warning (aws.secondary)

Impact

This is the most concerning item from an operational perspective.

Example:

provider "aws" {
  region = "us-east-1"
}

provider "aws" {
  alias  = "secondary"
  region = "us-west-2"
}

Resource:

resource "aws_s3_bucket" "dr_bucket" {
  provider = aws.secondary
}

If the alias mapping is broken after the provider upgrade:

providers = {
  aws = aws
}

instead of

providers = {
  aws = aws
  aws.secondary = aws.secondary
}

Terraform may:

Deploy in wrong region

Use wrong account

Fail plan/apply


Recommendation

Run:

terraform validate
terraform plan

and verify all module provider mappings.


---

3. S3 Encryption Finding

Impact

Usually no runtime impact.

However compliance scanners may flag:

server_side_encryption_configuration

missing.

Verify actual state:

aws s3api get-bucket-encryption \
--bucket <bucket-name>

Ensure:

"SSEAlgorithm": "aws:kms"

or

"SSEAlgorithm": "AES256"

exists.

Recommendation

Check:

terraform state show aws_s3_bucket.<bucket>

after deployment.


---

4. Multiple Lambda Functions Sharing Same Role

Impact

Application works normally.

Security Concern

Example:

lambda-a
lambda-b
lambda-c

all using:

LambdaExecutionRole

Permissions:

s3:*
dynamodb:*
secretsmanager:*

Now every Lambda gets all permissions.

Violates:

Least Privilege Principle

Separation of Duties


Recommendation

Not a blocker for provider upgrade.

Can be tracked as technical debt.


---

5. Secrets Manager Rotation Missing

Impact

Applications continue working.

Security Impact

Secrets such as:

Database passwords

API keys

Service credentials


will never rotate automatically.

Example:

aws_secretsmanager_secret_rotation

resource missing.

Recommendation

Verify whether rotation is intentionally disabled.

Some organizations mandate:

30 days

60 days

90 days


rotation periods.


---

6. Step Functions Using AWS-Owned Key

Impact

Workflow execution continues normally.

Compliance Impact

Current:

AWS Owned Key

Preferred:

Customer Managed KMS Key (CMK)

Example:

resource "aws_sfn_state_machine" "example" {
  encryption_configuration {
    kms_key_id = aws_kms_key.sfn.arn
  }
}

Organizations requiring:

PCI-DSS

HIPAA

Banking compliance

Pharma compliance


often require CMKs.

Recommendation

Verify organization security standards before the upgrade sign-off.


---

For Your AWS Provider Upgrade

The finding that could actually cause deployment issues is:

🔴 Provider alias warning (aws.secondary)

The others are primarily:

🟠 Security/Compliance findings

and generally should not break infrastructure deployment.

Before approving the upgrade, I would verify:

1. terraform validate


2. terraform plan


3. Provider alias mappings


4. State comparison before/after upgrade


5. S3 encryption actual state


6. OPA policy execution



If you share:

Current AWS provider version

Target AWS provider version

The alias warning message

Any Terraform plan output


I can tell you which findings are genuine upgrade risks versus false positives commonly seen during AWS provider version upgrades.
