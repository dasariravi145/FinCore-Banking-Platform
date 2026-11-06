variable "aws_region" {

        type = string
        default = "us-east-1"
}
variable "project" {

        type = string
        default = "fincorebank"
}
variable "environment" {

        type = string
        default = "dev"
}
variable "jenkins" {

        type = bool
        default = true
}
variable "sonar" {

        type = bool
        default = false
}
variable "runner" {

        type = bool
        default = false
}
variable "jenkins_instance_type" {

        type = string
        default = "t3.small"
}
variable "agent_instance_type" {

        type = string
        default = "t3.micro"
}
variable "sonar_instance_type" {

        type = string
        default = "t3.large"
}
variable "runner_instance_type" {

        type = string
        default = "t3.micro"
}
