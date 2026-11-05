
resource "aws_instance" "bastion" {
  ami           = local.ami_id
  instance_type = "t3.micro"

  subnet_id = local.public_subnet_id

  associate_public_ip_address = true

  vpc_security_group_ids = [
    local.bastion_sg_id
  ]

  iam_instance_profile = aws_iam_instance_profile.bastion.name

  user_data = file("${path.module}/bastion.sh")

  root_block_device {
    volume_size = 50
    volume_type = "gp3"
    encrypted   = true

    tags = merge(
      local.common_tags,
      {
        Name = "${var.project}-${var.environment}-bastion"
      }
    )
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  tags = merge(
    local.common_tags,
    {
      Name = "${var.project}-${var.environment}-bastion"
    }
  )
}
