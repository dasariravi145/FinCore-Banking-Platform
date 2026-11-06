#!/bin/bash
set -Eeuo pipefail
exec > >(tee -a /var/log/fincore-jenkins-bootstrap.log) 2>&1
dnf install -y fontconfig java-21-openjdk curl ca-certificates
curl -fsSL https://pkg.jenkins.io/rpm-stable/jenkins.repo -o /etc/yum.repos.d/jenkins.repo
# Verify Jenkins repository signing key against official documentation before deployment.
rpm --import https://pkg.jenkins.io/rpm-stable/jenkins.io-2026.key
dnf install -y jenkins
systemctl daemon-reload
systemctl enable --now jenkins
systemctl is-active --quiet jenkins
echo 'Jenkins installed; complete initial setup and security configuration manually.'
