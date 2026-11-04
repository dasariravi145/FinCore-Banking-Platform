locals {
  common_tags = {
    Project     = var.project
    Environment = var.environment
    Terraform   = "true"
  }

  my_ip = "${chomp(data.http.my_public_ip_v4.response_body)}/32"

  bastion_sg_id = data.aws_ssm_parameter.bastion_sg_id.value
  alb_sg_id     = data.aws_ssm_parameter.alb_sg_id.value
  app_sg_id     = data.aws_ssm_parameter.app_sg_id.value
  rds_sg_id     = data.aws_ssm_parameter.rds_sg_id.value
}