#!/bin/bash
set -Eeuo pipefail
exec > >(tee -a /var/log/fincore-runner-bootstrap.log) 2>&1
dnf install -y git curl jq tar gzip unzip java-21-openjdk python3 dnf-plugins-core
dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo
dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
# SECURITY: Do not embed GitHub runner registration tokens in Terraform or user_data.
# Install pinned GitHub Actions runner and register via a short-lived token in a secure manual step.
echo 'Runner host prepared; runner software/registration/service not yet configured.'