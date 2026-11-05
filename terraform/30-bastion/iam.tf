
# IAM Role for Bastion EC2
resource "aws_iam_role" "bastion" {
  name = "${var.project}-${var.environment}-bastion-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"

    Statement = [
      {
        Sid    = "AllowEC2AssumeRole"
        Effect = "Allow"

        Principal = {
          Service = "ec2.amazonaws.com"
        }

        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${var.project}-${var.environment}-bastion-role"
  })
}

# Attach AWS-managed SSM policy
resource "aws_iam_role_policy_attachment" "bastion_ssm" {
  role = aws_iam_role.bastion.name

  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# Instance Profile for EC2
resource "aws_iam_instance_profile" "bastion" {
  name = "${var.project}-${var.environment}-bastion-profile"

  role = aws_iam_role.bastion.name

  tags = merge(local.common_tags, {
    Name = "${var.project}-${var.environment}-bastion-profile"
  })
}
