data "http" "my_public_ip_v4" {

     url = "https://ipv4.icanhazip.com"
}

data "aws_ssm_parameter" "bastion_sg_id" {

      name = "/${var.project}/${var.environment}/bastion_sg_id"
}

data "aws_ssm_parameter" "alb_sg_id" {

      name = "/${var.project}/${var.environment}/alb_sg_id"
}

data "aws_ssm_parameter" "app_sg_id" {

      name = "/${var.project}/${var.environment}/app_sg_id"
}

data "aws_ssm_parameter" "rds_sg_id" {

      name = "/${var.project}/${var.environment}/rds_sg_id"
}

