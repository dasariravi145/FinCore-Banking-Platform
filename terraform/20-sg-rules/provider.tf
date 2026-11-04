
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "6.33.0"
    }
  }

  backend "s3" {
    bucket       = "fincore-bank-state-aws-terraform"
    key          = "fincorebank-dev-sg-rules"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = "us-east-1"
}
