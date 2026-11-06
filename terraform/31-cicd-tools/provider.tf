terraform {

        required_version = ">= 1.10.0"
        required_providers {
            aws = { source = "hashicorp/aws", version = "6.33.0" }
        }
        backend "s3" {
            bucket = "fincore-bank-state-aws-terraform"
            key = "fincorebank-dev-cicd-tools"
            region = "us-east-1"
            encrypt = true
            use_lockfile = true
        }
}
provider "aws" {
    
     region = var.aws_region 
     
}
