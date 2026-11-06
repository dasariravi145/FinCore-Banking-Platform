
resource "aws_ssm_document" "jenkins_agent_ssh_setup" {
        count = var.jenkins ? 1 : 0

        name = "${var.project}-${var.environment}-jenkins-agent-ssh-setup"

        document_type   = "Command"
        document_format = "YAML"

        content = yamlencode({
            schemaVersion = "2.2"

            description = "Configure Jenkins Agent SSH public key"

            parameters = {
            publicKeyBase64 = {
                type          = "String"
                description   = "Base64 encoded authorized SSH public key"
                allowedPattern = "^[A-Za-z0-9+/=]+$"
            }
            }

            mainSteps = [
            {
                action = "aws:runShellScript"
                name   = "configureAgentSSH"

                inputs = {
                runCommand = [
                    "set -eu",
                    "id jenkins-agent >/dev/null 2>&1",
                    "install -d -o jenkins-agent -g jenkins-agent -m 0700 /home/jenkins-agent/.ssh",
                    "printf '%s' '{{ publicKeyBase64 }}' | base64 -d > /home/jenkins-agent/.ssh/authorized_keys",
                    "chown jenkins-agent:jenkins-agent /home/jenkins-agent/.ssh/authorized_keys",
                    "chmod 0600 /home/jenkins-agent/.ssh/authorized_keys",
                    "systemctl is-active --quiet sshd"
                ]
                }
            }
            ]
        })

        tags = local.common_tags
}
