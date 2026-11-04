
output "alb_sg_id" {

      description = "ALB Security Group ID"
      value       = aws_security_group.alb.id

}

output "app_sg_id" {

        description = "Application Security Group ID"
        value       = aws_security_group.app.id
}

output "rds_sg_id" {

        description = "RDS Security Group ID"
        value       = aws_security_group.rds.id

}

output "bastion_sg_id" {

        description = "Bastion Security Group ID"
        value       = aws_security_group.bastion.id
        `
}
