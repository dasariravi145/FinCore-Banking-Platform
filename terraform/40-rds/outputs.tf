
# FinCore PostgreSQL RDS Outputs

output "rds_endpoint" {

        description = "PostgreSQL RDS endpoint including port"
        value       = aws_db_instance.fincore.endpoint

}

output "rds_address" {

        description = "PostgreSQL RDS hostname without port"
        value       = aws_db_instance.fincore.address

}

output "rds_port" {
    
        description = "PostgreSQL database port"
        value       = aws_db_instance.fincore.port
}

output "rds_database_name" {

        description = "Initial PostgreSQL database name"
        value       = aws_db_instance.fincore.db_name

}

output "rds_instance_identifier" {

        description = "RDS instance identifier"
        value       = aws_db_instance.fincore.identifier

}

output "rds_instance_arn" {

        description = "RDS instance ARN"
        value       = aws_db_instance.fincore.arn

}

output "rds_subnet_group_name" {

        description = "RDS database subnet group name"
        value       = aws_db_subnet_group.fincore.name

}

output "rds_master_secret_arn" {

            description = "AWS Secrets Manager ARN for RDS master credentials"

            value = aws_db_instance.fincore.master_user_secret[0].secret_arn
            
}
