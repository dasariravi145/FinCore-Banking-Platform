# Amazon ECR Repositories

resource "aws_ecr_repository" "fincore" {

        for_each = var.repositories

        name = "${var.project}/${each.value}"

        image_tag_mutability = "IMMUTABLE"

        image_scanning_configuration {
            scan_on_push = true
        }

        encryption_configuration {
            encryption_type = "AES256"
        }

        tags = merge(local.common_tags, {
            Name = "${var.project}-${var.environment}-${each.value}"
        })

}


# ECR Image Lifecycle Policy

resource "aws_ecr_lifecycle_policy" "fincore" {

        for_each = aws_ecr_repository.fincore

        repository = each.value.name

        policy = jsonencode({
            rules = [
            {
                rulePriority = 1
                description  = "Keep the latest 20 images"

                selection = {
                tagStatus   = "any"
                countType   = "imageCountMoreThan"
                countNumber = 20
                }

                action = {
                type = "expire"
                }
            }
            ]
        })

}
