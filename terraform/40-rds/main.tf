

# FinCore PostgreSQL DB Subnet Group

resource "aws_db_subnet_group" "fincore" {

        name = "${var.project}-${var.environment}-db-subnet-group"

        subnet_ids = split(
            ",",
            data.aws_ssm_parameter.database_subnet_ids.value
        )

        description = "Private database subnets for FinCore PostgreSQL"

        tags = {
            Project     = var.project
            Environment = var.environment
            Terraform   = "true"
            Name        = "${var.project}-${var.environment}-db-subnet-group"
        }
}


# FinCore PostgreSQL RDS Instance

resource "aws_db_instance" "fincore" {
    
            identifier = "${var.project}-${var.environment}-postgresql"

            # Database engine
            engine         = "postgres"
            engine_version = var.db_engine_version
            instance_class = var.db_instance_class

            # Database configuration
            db_name  = var.db_name
            username = var.db_username
            port     = 5432

            # AWS Secrets Manager-managed master password
            manage_master_user_password = true

            # Storage
            allocated_storage     = var.db_allocated_storage
            max_allocated_storage = var.db_max_allocated_storage
            storage_type          = var.db_storage_type
            storage_encrypted     = true

            # Networking
            db_subnet_group_name = aws_db_subnet_group.fincore.name

            vpc_security_group_ids = [
                data.aws_ssm_parameter.rds_sg_id.value
            ]

            publicly_accessible = false

            # Availability
            multi_az = var.db_multi_az

            # Backups
            backup_retention_period = var.db_backup_retention_period
            copy_tags_to_snapshot   = true

            # Database protection
            deletion_protection = var.db_deletion_protection
            skip_final_snapshot = var.db_skip_final_snapshot

            final_snapshot_identifier = (
                var.db_skip_final_snapshot
                ? null
                : "${var.project}-${var.environment}-postgresql-final"
            )

            # Maintenance
            auto_minor_version_upgrade = true

            # Apply database modifications during maintenance windows
            apply_immediately = false

            tags = {
                Project     = var.project
                Environment = var.environment
                Terraform   = "true"
                Name        = "${var.project}-${var.environment}-postgresql"
            }
}
