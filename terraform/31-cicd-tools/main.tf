resource "aws_instance" "jenkins" {
            count = var.jenkins ? 1 : 0
            ami = data.aws_ami.rhel.id
            instance_type = var.jenkins_instance_type
            subnet_id = local.private_subnet_id
            associate_public_ip_address = false
            vpc_security_group_ids = [data.aws_ssm_parameter.jenkins_sg_id[0].value]
            iam_instance_profile = aws_iam_instance_profile.cicd.name
            user_data = file("${path.module}/jenkins.sh")
            root_block_device {
                volume_size = 50
                volume_type = "gp3"
                encrypted = true
            }
            metadata_options {
                http_tokens = "required"
            }
            tags = merge(local.common_tags, { Name = "${var.project}-${var.environment}-jenkins" })
}
resource "aws_instance" "jenkins_agent" {
            count = var.jenkins ? 1 : 0
            ami = data.aws_ami.rhel.id
            instance_type = var.agent_instance_type
            subnet_id = local.private_subnet_id
            associate_public_ip_address = false
            vpc_security_group_ids = [data.aws_ssm_parameter.jenkins_agent_sg_id[0].value]
            iam_instance_profile = aws_iam_instance_profile.cicd.name
            user_data = file("${path.module}/jenkins-agent.sh")
            root_block_device {
                volume_size = 50
                volume_type = "gp3"
                encrypted = true
            }
            metadata_options {
                http_tokens = "required"
            }
            tags = merge(local.common_tags, { Name = "${var.project}-${var.environment}-jenkins-agent" })
            }
            resource "aws_instance" "runner" {
            count = var.runner ? 1 : 0
            ami = data.aws_ami.rhel.id
            instance_type = var.runner_instance_type
            subnet_id = local.private_subnet_id
            associate_public_ip_address = false
            vpc_security_group_ids = [data.aws_ssm_parameter.runner_sg_id[0].value]
            iam_instance_profile = aws_iam_instance_profile.cicd.name
            user_data = file("${path.module}/runner.sh")
            root_block_device {
                volume_size = 50
                volume_type = "gp3"
                encrypted = true
            }
            metadata_options {
                http_tokens = "required"
            }
            tags = merge(local.common_tags, { Name = "${var.project}-${var.environment}-runner" })
}
resource "aws_instance" "sonarqube" {
                count = var.sonar ? 1 : 0
                ami = data.aws_ami.sonar[0].id
                instance_type = var.sonar_instance_type
                subnet_id = local.private_subnet_id
                associate_public_ip_address = false
                vpc_security_group_ids = [data.aws_ssm_parameter.sonar_sg_id[0].value]
                iam_instance_profile = aws_iam_instance_profile.cicd.name
                root_block_device {
                    volume_size = 20
                    volume_type = "gp3"
                    encrypted = true
                }
                metadata_options {
                    http_tokens = "required"
                }
                tags = merge(local.common_tags, { Name = "${var.project}-${var.environment}-sonar" })
}
