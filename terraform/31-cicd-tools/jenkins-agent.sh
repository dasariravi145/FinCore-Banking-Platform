
#!/bin/bash
set -Eeuo pipefail

exec > >(tee -a /var/log/fincore-agent-bootstrap.log) 2>&1

echo "Starting FinCore Jenkins Agent bootstrap"

# Install required software
dnf install -y \
  fontconfig \
  java-21-openjdk \
  git \
  curl \
  ca-certificates \
  jq \
  unzip \
  dnf-plugins-core \
  openssh-server

# Install Docker
dnf config-manager --add-repo \
  https://download.docker.com/linux/rhel/docker-ce.repo

dnf install -y \
  docker-ce \
  docker-ce-cli \
  containerd.io \
  docker-buildx-plugin \
  docker-compose-plugin

systemctl enable --now docker
systemctl enable --now sshd

# Create dedicated Jenkins Agent user
if ! id jenkins-agent >/dev/null 2>&1; then
  useradd -m -s /bin/bash jenkins-agent
fi

# Prepare Jenkins working directory
install -d \
  -o jenkins-agent \
  -g jenkins-agent \
  -m 0750 \
  /home/jenkins-agent/agent

# Prepare SSH authorized_keys directory
install -d \
  -o jenkins-agent \
  -g jenkins-agent \
  -m 0700 \
  /home/jenkins-agent/.ssh

touch /home/jenkins-agent/.ssh/authorized_keys

chown jenkins-agent:jenkins-agent \
  /home/jenkins-agent/.ssh/authorized_keys

chmod 0600 \
  /home/jenkins-agent/.ssh/authorized_keys

# Allow trusted Jenkins build user to use Docker
usermod -aG docker jenkins-agent

# Verify installations
java -version
docker --version
git --version
systemctl is-active --quiet sshd
systemctl is-active --quiet docker

echo "Jenkins Agent base bootstrap completed"
echo "SSH public key provisioning and Jenkins registration are still required"
