data "aws_ami" "rhel" {
        most_recent = true
        owners = ["973714476881"]
        filter {
            name = "name"
            values = ["Redhat-9-DevOps-Practice"]
        }
        filter {
            name = "root-device-type"
            values = ["ebs"]
        }
        filter {
            name = "virtualization-type"
            values = ["hvm"]
        }
        filter {
            name = "architecture"
            values = ["x86_64"]
        }
}

data "aws_ami" "sonar" {
        count = var.sonar ? 1 : 0
        most_recent = true
        owners = ["679593333241"]
        filter {
            name = "name"
            values = ["SolveDevOps-SonarQube-Server-Ubuntu24.04-*"]
        }
        filter {
            name = "architecture"
            values = ["x86_64"]
        }
        filter {
            name = "virtualization-type"
            values = ["hvm"]
        }
}

data "aws_ssm_parameter" "private_subnet_ids" {

        name = "/${var.project}/${var.environment}/private_subnet_ids"
}
data "aws_ssm_parameter" "jenkins_sg_id" {
        count = var.jenkins ? 1 : 0
        name = "/${var.project}/${var.environment}/jenkins_sg_id"
}
data "aws_ssm_parameter" "jenkins_agent_sg_id" {
        count = var.jenkins ? 1 : 0
        name = "/${var.project}/${var.environment}/jenkins_agent_sg_id"
}
data "aws_ssm_parameter" "sonar_sg_id" {
        count = var.sonar ? 1 : 0
        name = "/${var.project}/${var.environment}/sonar_sg_id"
}
data "aws_ssm_parameter" "runner_sg_id" {
        count = var.runner ? 1 : 0
        name = "/${var.project}/${var.environment}/runner_sg_id"
}
