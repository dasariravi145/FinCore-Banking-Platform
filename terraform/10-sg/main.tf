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