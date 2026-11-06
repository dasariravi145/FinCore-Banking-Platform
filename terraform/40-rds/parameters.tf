

# PostgreSQL RDS Endpoint

resource "aws_ssm_parameter" "rds_endpoint" {

        name = "/${var.project}/${var.environment}/rds_endpoint"

        type  = "String"
        value = aws_db_instance.fincore.address

}


# PostgreSQL RDS Port

resource "aws_ssm_parameter" "rds_port" {

            name = "/${var.project}/${var.environment}/rds_port"

            type  = "String"
            value = tostring(aws_db_instance.fincore.port)

}

# PostgreSQL Database Name

resource "aws_ssm_parameter" "rds_database_name" {

        name = "/${var.project}/${var.environment}/rds_database_name"

        type  = "String"
        value = aws_db_instance.fincore.db_name

}


# RDS Master Secret ARN

resource "aws_ssm_parameter" "rds_master_secret_arn" {

            name = "/${var.project}/${var.environment}/rds_master_secret_arn"

            type = "String"

            value = aws_db_instance.fincore.master_user_secret[0].secret_arn
            
}
