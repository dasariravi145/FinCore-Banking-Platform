

# FinCore ECR Repository URLs

output "ecr_repository_urls" {

        description = "ECR repository URLs for all FinCore applications"

        value = {
            for name, repository in aws_ecr_repository.fincore :
            name => repository.repository_url
        }
        
}


# FinCore ECR Repository ARNs

output "ecr_repository_arns" {

        description = "ECR repository ARNs"

        value = {
            for name, repository in aws_ecr_repository.fincore :
            name => repository.arn
        }

}


# FinCore ECR Repository Names

output "ecr_repository_names" {

        description = "ECR repository names"

        value = {
            for name, repository in aws_ecr_repository.fincore :
            name => repository.name
        }

}


# Individual Application Repository URLs

output "backend_ecr_url" {

        description = "Spring Boot backend ECR repository URL"

        value = aws_ecr_repository.fincore["backend"].repository_url

}

output "frontend_ecr_url" {

        description = "React frontend ECR repository URL"

        value = aws_ecr_repository.fincore["frontend"].repository_url

}
