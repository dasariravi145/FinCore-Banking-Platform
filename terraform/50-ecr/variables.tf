

# FinCore ECR Variables

variable "project" {

        description = "Project name used for AWS resource naming"
        type        = string
        default     = "fincorebank"

}

variable "environment" {

        description = "Deployment environment"
        type        = string
        default     = "dev"

}

variable "repositories" {

        description = "ECR repositories for FinCore applications"
        type        = set(string)

        default = [
            "backend",
            "frontend"
        ]
        
}
