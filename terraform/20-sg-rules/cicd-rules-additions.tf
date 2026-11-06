

# Jenkins Controller -> Jenkins Agent

resource "aws_vpc_security_group_ingress_rule" "jenkins_agent_ssh" {

        security_group_id = local.jenkins_agent_sg_id

        referenced_security_group_id = local.jenkins_sg_id

        ip_protocol = "tcp"
        from_port   = 22
        to_port     = 22

        description = "Allow Jenkins Controller to SSH into Jenkins Agent"

        tags = merge(local.common_tags, {
            Name = "${var.project}-${var.environment}-jenkins-agent-ssh"
        })
}


# Jenkins Controller -> SonarQube

resource "aws_vpc_security_group_ingress_rule" "sonar_from_jenkins" {

        security_group_id = local.sonar_sg_id

        referenced_security_group_id = local.jenkins_sg_id

        ip_protocol = "tcp"
        from_port   = 9000
        to_port     = 9000

        description = "Allow Jenkins Controller to access SonarQube"

        tags = merge(local.common_tags, {
            Name = "${var.project}-${var.environment}-sonar-from-jenkins"
        })
}


# Jenkins Agent -> SonarQube
resource "aws_vpc_security_group_ingress_rule" "sonar_from_agent" {

        security_group_id = local.sonar_sg_id

        referenced_security_group_id = local.jenkins_agent_sg_id

        ip_protocol = "tcp"
        from_port   = 9000
        to_port     = 9000

        description = "Allow Jenkins Agent to submit SonarQube analysis"

        tags = merge(local.common_tags, {
            Name = "${var.project}-${var.environment}-sonar-from-agent"
        })
  
}




# Jenkins Controller -> Jenkins Agent
resource "aws_vpc_security_group_ingress_rule" "jenkins_agent_ssh" {
    
        security_group_id = local.jenkins_agent_sg_id

        referenced_security_group_id = local.jenkins_sg_id

        ip_protocol = "tcp"
        from_port   = 22
        to_port     = 22

        description = "Allow Jenkins Controller to SSH into Jenkins Agent"

        tags = merge(local.common_tags, {
            Name = "${var.project}-${var.environment}-jenkins-agent-ssh"
        })
}


# Jenkins Controller -> SonarQube
resource "aws_vpc_security_group_ingress_rule" "sonar_from_jenkins" {

        security_group_id = local.sonar_sg_id

        referenced_security_group_id = local.jenkins_sg_id

        ip_protocol = "tcp"
        from_port   = 9000
        to_port     = 9000

        description = "Allow Jenkins Controller to access SonarQube"

        tags = merge(local.common_tags, {
            Name = "${var.project}-${var.environment}-sonar-from-jenkins"
        })
}


# Jenkins Agent -> SonarQube
resource "aws_vpc_security_group_ingress_rule" "sonar_from_agent" {

        security_group_id = local.sonar_sg_id

        referenced_security_group_id = local.jenkins_agent_sg_id

        ip_protocol = "tcp"
        from_port   = 9000
        to_port     = 9000

        description = "Allow Jenkins Agent to submit SonarQube analysis"

        tags = merge(local.common_tags, {
            Name = "${var.project}-${var.environment}-sonar-from-agent"
        })
}

