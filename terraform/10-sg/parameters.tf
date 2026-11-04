
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
