
resource "aws_ssm_parameter" "alb_sg_id" {

        name  = "/${var.project}/${var.environment}/alb_sg_id"
        type  = "String"
        value = aws_security_group.alb.id

        tags = merge(
            local.common_tags, 
        {
            Name = "${var.project}-${var.environment}-alb-sg-id"
        }
        )
}

resource "aws_ssm_parameter" "app_sg_id" {
        name  = "/${var.project}/${var.environment}/app_sg_id"
        type  = "String"
        value = aws_security_group.app.id

        tags = merge(
        local.common_tags, 
        {
            Name = "${var.project}-${var.environment}-app-sg-id"
        }
        )
}

resource "aws_ssm_parameter" "rds_sg_id" {
        name  = "/${var.project}/${var.environment}/rds_sg_id"
        type  = "String"
        value = aws_security_group.rds.id

        tags = merge(
        local.common_tags,
        {
            Name = "${var.project}-${var.environment}-rds-sg-id"
        }
        )
}

resource "aws_ssm_parameter" "bastion_sg_id" {
        name  = "/${var.project}/${var.environment}/bastion_sg_id"
        type  = "String"
        value = aws_security_group.bastion.id

        tags = merge(
        local.common_tags,
        {
            Name = "${var.project}-${var.environment}-bastion-sg-id"
        }
        )
}


# ==========================================
# FinCore CI/CD Security Groups
# ==========================================

resource "aws_security_group" "jenkins" {
  name        = "${var.project}-${var.environment}-jenkins-sg"
  description = "Security group for Jenkins Controller"
  vpc_id      = data.aws_ssm_parameter.vpc_id.value

  tags = merge(local.common_tags, {
    Name = "${var.project}-${var.environment}-jenkins-sg"
  })
}

resource "aws_security_group" "jenkins_agent" {
  name        = "${var.project}-${var.environment}-jenkins-agent-sg"
  description = "Security group for Jenkins Agent"
  vpc_id      = data.aws_ssm_parameter.vpc_id.value

  tags = merge(local.common_tags, {
    Name = "${var.project}-${var.environment}-jenkins-agent-sg"
  })
}

resource "aws_security_group" "sonar" {
  name        = "${var.project}-${var.environment}-sonar-sg"
  description = "Security group for SonarQube"
  vpc_id      = data.aws_ssm_parameter.vpc_id.value

  tags = merge(local.common_tags, {
    Name = "${var.project}-${var.environment}-sonar-sg"
  })
}

resource "aws_security_group" "runner" {
  name        = "${var.project}-${var.environment}-runner-sg"
  description = "Security group for GitHub Actions Runner"
  vpc_id      = data.aws_ssm_parameter.vpc_id.value

  tags = merge(local.common_tags, {
    Name = "${var.project}-${var.environment}-runner-sg"
  })
}

# ==========================================
# Store Security Group IDs in AWS SSM
# ==========================================

resource "aws_ssm_parameter" "jenkins_sg_id" {

            name  = "/${var.project}/${var.environment}/jenkins_sg_id"
            type  = "String"
            value = aws_security_group.jenkins.id

            tags = merge(local.common_tags, {
                Name = "${var.project}-${var.environment}-jenkins-sg-id"
            })
}

resource "aws_ssm_parameter" "jenkins_agent_sg_id" {

            name  = "/${var.project}/${var.environment}/jenkins_agent_sg_id"
            type  = "String"
            value = aws_security_group.jenkins_agent.id

            tags = merge(local.common_tags, {
                Name = "${var.project}-${var.environment}-jenkins-agent-sg-id"
            })
}

resource "aws_ssm_parameter" "sonar_sg_id" {

            name  = "/${var.project}/${var.environment}/sonar_sg_id"
            type  = "String"
            value = aws_security_group.sonar.id

            tags = merge(local.common_tags, {
                Name = "${var.project}-${var.environment}-sonar-sg-id"
            })
}

resource "aws_ssm_parameter" "runner_sg_id" {
    
            name  = "/${var.project}/${var.environment}/runner_sg_id"
            type  = "String"
            value = aws_security_group.runner.id

            tags = merge(local.common_tags, {
                Name = "${var.project}-${var.environment}-runner-sg-id"
            })
}
