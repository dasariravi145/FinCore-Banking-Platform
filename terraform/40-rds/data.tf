

# Database Subnet IDs from VPC
data "aws_ssm_parameter" "database_subnet_ids" {

        name = "/${var.project}/${var.environment}/database_subnet_ids"
}


# RDS Security Group ID
data "aws_ssm_parameter" "rds_sg_id" {

        name = "/${var.project}/${var.environment}/rds_sg_id"
}
