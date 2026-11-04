# Bastion: SSH from current public IP
resource "aws_vpc_security_group_ingress_rule" "bastion_internet" {
  security_group_id = local.bastion_sg_id

  description = "SSH access from administrator IP"
  from_port   = 22
  to_port     = 22
  ip_protocol = "tcp"
  cidr_ipv4   = local.my_ip

  tags = merge(local.common_tags, {
    Name = "${var.project}-${var.environment}-bastion-ssh"
  })
}

# ALB: HTTPS from internet
resource "aws_vpc_security_group_ingress_rule" "alb_internet" {
  security_group_id = local.alb_sg_id

  description = "HTTPS access from internet"
  from_port   = 443
  to_port     = 443
  ip_protocol = "tcp"
  cidr_ipv4   = "0.0.0.0/0"

  tags = merge(local.common_tags, {
    Name = "${var.project}-${var.environment}-alb-https"
  })
}

# Application: Spring Boot traffic from ALB
resource "aws_vpc_security_group_ingress_rule" "app_alb" {
  security_group_id = local.app_sg_id

  description                  = "Spring Boot access from ALB"
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
  referenced_security_group_id = local.alb_sg_id

  tags = merge(local.common_tags, {
    Name = "${var.project}-${var.environment}-app-alb"
  })
}

# RDS: PostgreSQL traffic from application
resource "aws_vpc_security_group_ingress_rule" "rds_app" {
  security_group_id = local.rds_sg_id

  description                  = "PostgreSQL access from application"
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  referenced_security_group_id = local.app_sg_id

  tags = merge(local.common_tags, {
    Name = "${var.project}-${var.environment}-rds-app"
  })
}