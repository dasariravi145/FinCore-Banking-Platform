data "aws_iam_policy_document" "ec2_assume" {
    
        statement {
            actions = ["sts:AssumeRole"]
            principals {
            type = "Service"
            identifiers = ["ec2.amazonaws.com"]
            }
        }
}
resource "aws_iam_role" "cicd" {

        name = "${var.project}-${var.environment}-cicd-ec2-role"
        assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
        tags = local.common_tags
}
resource "aws_iam_role_policy_attachment" "ssm" {

        role = aws_iam_role.cicd.name
        policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
resource "aws_iam_instance_profile" "cicd" {

        name = "${var.project}-${var.environment}-cicd-profile"
        role = aws_iam_role.cicd.name
        tags = local.common_tags
}
