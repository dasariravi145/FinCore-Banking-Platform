variable "project" {

       type = string
       default = "fincorebank"
}

variable "environment" {

      type = string
      default = "dev"
}

variable "alb_sg_tags"{
       type = map
       default = {}

}

variable "app_sg_tags" {

     type = map
     default = {}
}

variable "rds_sg_tags" {
     type = map
     default = {}
}

variable "bastion_sg_tags" {

      type = map
      default = {}
}