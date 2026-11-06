resource "aws_security_group" "alb" {
      name = "${var.project}-${var.environment}-alb-sg"
      description = "Security group for Fincore ALB"
      vpc_id = data.aws_ssm_parameter.vpc_id.value

      tags = merge(
           local.common_tags,
           {
               Name = "${var.project}-${var.environment}-alb-sg"
           },
           var.alb_sg_tags

      )
}

resource "aws_security_group" "app" {
        name        = "${var.project}-${var.environment}-app-sg"
        description = "Security group for FinCore application"
        vpc_id      = data.aws_ssm_parameter.vpc_id.value

        tags = merge(
        local.common_tags, 
        {

            Name = "${var.project}-${var.environment}-app-sg"
        },
        var.app_sg_tags
        
        )
}

resource "aws_security_group" "rds" {
        name        = "${var.project}-${var.environment}-rds-sg"

        description = "Security group for FinCore RDS PostgreSQL"
        vpc_id      = data.aws_ssm_parameter.vpc_id.value

        tags = merge(
         local.common_tags,
        {
            Name = "${var.project}-${var.environment}-rds-sg"
        },
        var.rds_sg_tags
        )
}

resource "aws_security_group" "bastion"{

        name        = "${var.project}-${var.environment}-bastion-sg"
        description = "Security group for FinCore Bastion"
        vpc_id      = data.aws_ssm_parameter.vpc_id.value

        tags = merge(
        local.common_tags, 
        {
             Name = "${var.project}-${var.environment}-bastion-sg"
        },
        var.bastion_sg_tags
        )
}


# RDS PostgreSQL Security Group
resource "aws_security_group" "rds" {
        name        = "${var.project}-${var.environment}-rds-sg"
        description = "Security group for FinCore PostgreSQL RDS"
        vpc_id      = data.aws_ssm_parameter.vpc_id.value

        tags = merge(
            local.common_tags,
            {
            Name = "${var.project}-${var.environment}-rds-sg"
            },
            var.rds_sg_tags
        )
}

# Bastion Security Group
resource "aws_security_group" "bastion" {
        name        = "${var.project}-${var.environment}-bastion-sg"
        description = "Security group for FinCore Bastion EC2"
        vpc_id      = data.aws_ssm_parameter.vpc_id.value

        tags = merge(
            local.common_tags,
            {
            Name = "${var.project}-${var.environment}-bastion-sg"
            },
            var.bastion_sg_tags
        )
}


