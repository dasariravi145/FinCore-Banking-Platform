

# Project Configuration

variable "project" {

        description = "Project name"
        type        = string
        default     = "fincorebank"
}

variable "environment" {

        description = "Deployment environment"
        type        = string
        default     = "dev"
}


# PostgreSQL Configuration

variable "db_engine_version" {

        description = "PostgreSQL engine version supported by AWS RDS"
        type        = string
        default     = "17"
}

variable "db_instance_class" {

        description = "RDS instance size"
        type        = string
        default     = "db.t3.micro"
}

variable "db_name" {

        description = "Initial PostgreSQL database name"
        type        = string
        default     = "fincorebank"
}

variable "db_username" {

        description = "PostgreSQL master username"
        type        = string
        default     = "fincoreadmin"
}

# Storage Configuration

variable "db_allocated_storage" {

        description = "Initial database storage in GiB"
        type        = number
        default     = 20
}

variable "db_max_allocated_storage" {

        description = "Maximum storage autoscaling limit in GiB"
        type        = number
        default     = 100
}

variable "db_storage_type" {

        description = "RDS storage type"
        type        = string
        default     = "gp3"
}


# Availability and Backup

variable "db_multi_az" {

        description = "Enable Multi-AZ deployment"
        type        = bool
        default     = false
}

variable "db_backup_retention_period" {

        description = "Automated backup retention in days"
        type        = number
        default     = 7
}

variable "db_deletion_protection" {

        description = "Protect database from accidental deletion"
        type        = bool
        default     = true
}

variable "db_skip_final_snapshot" {
    
        description = "Skip final snapshot when deleting database"
        type        = bool
        default     = false
}
