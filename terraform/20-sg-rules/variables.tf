
variable "project" {
  type    = string
  default = "fincorebank"
}

variable "environment" {
  type    = string
  default = "dev"
}

variable "app_port" {
  description = "Spring Boot application port"
  type        = number
  default     = 8080
}


