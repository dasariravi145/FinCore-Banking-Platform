module "vpc" {
      source      = "git::https://github.com/dasariravi145/Fincore-Terraform-VPC.git"
      project = var.project
      environment = var.environment
      
}